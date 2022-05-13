/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.http.harness;

import com.mastfrog.concurrent.IncrementableLatch;
import com.mastfrog.predicates.Predicates;
import com.mastfrog.util.codec.Codec;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Implementation of Assertions, which intercepts callbacks from the HTTP client
 * and runs the assertions associated with the given callback (headers, body
 * chunks, body).
 */
final class AssertionsImpl implements Assertions, HttpResponse.BodyHandler<String>, HttpResponse.BodySubscriber<String>, Runnable {

    private static final ThreadLocal<FailureSeverity> CURR_SEVERITY = ThreadLocal.withInitial(() -> FailureSeverity.FATAL);
    private final List<Assertion<HttpResponse.ResponseInfo, ?>> headerAssertions = new ArrayList<>(8);
    private final List<Assertion<ByteArrayOutputStream, ?>> bodyAssertions = new ArrayList<>(8);
    private final List<Assertion<ByteBuffer, ?>> chunkAssertions = new ArrayList<>(8);
    private final List<Assertion<Throwable, ?>> thrownAssertions = new ArrayList<>(8);
    private final List<Assertion<Boolean, ?>> timeoutAssertions = new ArrayList<>(1);
    private final Set<Assertion<?, ?>> invokedAssertions = ConcurrentHashMap.newKeySet();
    private final String reqInfo;
    final Consumer<AssertionResult> resultConsumer;
    final AtomicBoolean aborted;
    private final AtomicBoolean done = new AtomicBoolean();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final Codec mapper;
    private final CompletableFuture<String> future = new CompletableFuture<>();
    private final IncrementableLatch latch;
    private final Optional<Duration> overallResponseTimeout;
    private final AtomicLong invokedAt = new AtomicLong();
    private volatile boolean timedOut;
    private volatile Task task;
    private volatile Flow.Subscription subscription;

    AssertionsImpl(String reqInfo, Consumer<AssertionResult> resultConsumer, AtomicBoolean aborted,
            Codec mapper, IncrementableLatch latch,
            Optional<Duration> overallResponseTimeout) {
        this.reqInfo = reqInfo;
        this.resultConsumer = resultConsumer;
        this.aborted = aborted;
        this.mapper = mapper;
        this.latch = latch;
        this.overallResponseTimeout = overallResponseTimeout;
    }

    private AssertionsImpl addHeaderAssertion(Assertion<HttpResponse.ResponseInfo, ?> a) {
        headerAssertions.add(a);
        return this;
    }

    private AssertionsImpl addBodyAssertion(Assertion<ByteArrayOutputStream, ?> a) {
        bodyAssertions.add(a);
        return this;
    }

    private AssertionsImpl addChunkAssertion(Assertion<ByteBuffer, ?> a) {
        chunkAssertions.add(a);
        return this;
    }

    private AssertionsImpl addThrownAssertion(Assertion<Throwable, ?> a) {
        thrownAssertions.add(a);
        return this;
    }

    private AssertionsImpl addTimeoutAssertion(Assertion<Boolean, ?> a) {
        timeoutAssertions.add(a);
        return this;
    }

    /**
     * Called by the watchdog thread to ensure we time out if nothing is
     * happening to trigger tests otherwise.
     */
    @Override
    public void run() {
        abortIfTimedOut();
    }

    AssertionsImpl launched(long when, Task task) {
        invokedAt.compareAndSet(0L, when);
        this.task = task;
        return this;
    }

    boolean isTimedOut() {
        long when = invokedAt.get();
        if (when != 0L && overallResponseTimeout.isPresent()) {
            Duration dur = overallResponseTimeout.get();
            long elapsed = System.currentTimeMillis() - when;
            boolean result = dur.toMillis() < elapsed;
            if (result) {
                onTimeout();
            }
            return result;
        }
        return false;
    }

    void onTimeout() {
        if (!timedOut) {
            timedOut = true;
            runAssertions(true, timeoutAssertions);
            Task t = task;
            if (t != null) {
                t.cancel();
            }
            pendingAssertions().forEach(a -> {
                resultConsumer.accept(a.didNotRunResult());
            });
        }
    }

    boolean abortIfTimedOut() {
        boolean result = !timedOut && isTimedOut();
        if (result) {
            task.cancel();
            Flow.Subscription sub = subscription;
            if (sub != null) {
                sub.cancel();
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(reqInfo);
        Consumer<Assertion<?, ?>> c = a -> {
            sb.append('\n');
            sb.append(" * ").append(a);
        };
        headerAssertions.forEach(c);
        chunkAssertions.forEach(c);
        bodyAssertions.forEach(c);
        timeoutAssertions.forEach(c);
        invokedAssertions.forEach(c);
        thrownAssertions.forEach(c);
        return sb.toString();
    }

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
        if (aborted.get() || abortIfTimedOut()) {
            return null;
        }
        runAssertions(responseInfo, headerAssertions);
        return this;
    }

    @Override
    public AssertionsImpl assertHeader(String header, Predicate<? super String> valueTest) {
        return addHeaderAssertion(new HeaderAssertion(header, severity(), valueTest));
    }

    @Override
    public AssertionsImpl assertResponseCode(IntPredicate responseCode) {
        return addHeaderAssertion(new ResponseCodeAssertion("Response code", severity(), adapt(responseCode)));
    }

    @Override
    public AssertionsImpl assertBody(Predicate<? super String> bodyTest) {
        return addBodyAssertion(new BodyAssertion<>(out -> new String(out.toByteArray(), StandardCharsets.UTF_8), "Body", severity(), bodyTest));
    }

    @Override
    public AssertionsImpl assertVersion(Predicate<? super HttpClient.Version> versionTest) {
        return addHeaderAssertion(new VersionAssertion("HTTP version", severity(),
                versionTest));
    }

    @Override
    public AssertionsImpl assertVersion(HttpClient.Version expected) {
        return addHeaderAssertion(new ExactVersionAssertion(severity(),
                expected));
    }

    @Override
    public <T> AssertionsImpl assertObject(String description, Class<T> type, Predicate<? super T> test) {
        return addBodyAssertion(new BodyAssertion<>(new JsonConverter<>(type, mapper),
                "Body as " + type.getSimpleName(), severity(), Predicates.namedPredicate(description, test)));
    }

    @Override
    public AssertionsImpl assertTimesOut() {
        return addTimeoutAssertion(new TimeoutAssertion(true, severity()));
    }

    @Override
    public Assertions assertDoesNotTimeOut() {
        return addTimeoutAssertion(new TimeoutAssertion(false, severity()));
    }

    @Override
    public AssertionsImpl assertThrown(Class<? extends Throwable> expectedFailure) {
        return addThrownAssertion(new ThrowableAssertion("Exception should be thrown",
                severity(), expectedFailure));
    }

    @Override
    public Assertions assertChunk(String desc, Predicate<? super ByteBuffer> chunkTest) {
        return addChunkAssertion(new ChunkAssertion(desc, severity(), chunkTest));
    }

    private FailureSeverity severity() {
        return CURR_SEVERITY.get();
    }

    @Override
    public Assertions withSeverity(FailureSeverity severity, Consumer<Assertions> c) {
        FailureSeverity old = CURR_SEVERITY.get();
        CURR_SEVERITY.set(severity);
        try {
            c.accept(this);
        } finally {
            CURR_SEVERITY.set(old);
        }
        return this;
    }

    private Set<Assertion<?, ?>> pendingAssertions() {
        Set<Assertion<?, ?>> result = new LinkedHashSet<>(
                headerAssertions.size()
                + bodyAssertions.size()
                + chunkAssertions.size()
                + timeoutAssertions.size()
                + invokedAssertions.size()
                + thrownAssertions.size()
        );
        result.addAll(headerAssertions);
        result.addAll(chunkAssertions);
        result.addAll(bodyAssertions);
        result.addAll(timeoutAssertions);
        result.addAll(thrownAssertions);
        result.removeAll(invokedAssertions);
        return result;
    }

    @Override
    public CompletionStage<String> getBody() {
        return future;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (aborted.get() || abortIfTimedOut()) {
            subscription.cancel();
        } else {
            subscription.request(Long.MAX_VALUE);
        }
    }

    private <T> void runAssertion(Assertion<T, ?> a, T obj) {
        AssertionResult result;
        try {
            invokedAssertions.add(a);
            result = a.test(obj);
        } catch (Exception | Error e) {
            result = a.errorResult(e);
        }
        resultConsumer.accept(result);
    }

    private <T> void runAssertions(T obj, Collection<? extends Assertion<T, ?>> all) {
        all.forEach(a -> {
            runAssertion(a, obj);
        });
    }

    private <T> void runAssertions(Collection<? extends Assertion<T, ?>> all,
            Supplier<T> supp) {
        all.forEach(a -> {
            runAssertion(a, supp.get());
        });
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> item) {
        // We really do need some lock here.
        for (ByteBuffer buf : item) {
            try {
                // DO NOT FLIP THE BUFFER HERE.  LOOKS LIKE YOU SHOULD, BUT NO.
                // The JDK's HTTP client does *not* use Buffer.slice() to give
                // you a view of just what you need - if you flip the first
                // buffer, you get the headers and not your content.
                runAssertions(chunkAssertions, buf::duplicate);
                byte[] all = new byte[buf.remaining()];
                buf.get(all);
                bytes.write(all);
            } catch (IOException ex) {
                throw new Error(ex);
            }
        }
        abortIfTimedOut();
    }

    private volatile Throwable lastThrown;

    @Override
    public void onError(Throwable throwable) {
        // The HTTP client will call this method only _after_ the flow
        // subscription is set up, which means that early timeouts and
        // other kinds of failures may never get here at all.  For that
        // reason we also call this method from the future consumer in
        // TestHarness, to make sure we notice whatever has happened
        if (lastThrown == throwable) {
            return;
        }
        lastThrown = throwable;
        if (timedOut && (throwable instanceof CancellationException || throwable instanceof IOException)) {
            // make sure
            task.cancel();
            done();
            return;
        }
        try {
            if (throwable instanceof HttpTimeoutException) {
                onTimeout();
            }
            runAssertions(throwable, thrownAssertions);
            pendingAssertions().forEach(a -> {
                resultConsumer.accept(a.errorResult(throwable));
            });
        } finally {
            done();
        }
    }

    @Override
    public synchronized void onComplete() {
        byte[] b = bytes.toByteArray();
        try {
            runAssertions(bytes, bodyAssertions);
        } finally {
            try {
                if (!timedOut) {
                    runAssertions(false, timeoutAssertions);
                }
            } finally {
                try {
                    future.complete(new String(b, StandardCharsets.UTF_8));
                } finally {
                    done();
                }
            }
        }
    }

    void done() {
        subscription = null;
        if (done.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    static class JsonConverter<T> implements Function<ByteArrayOutputStream, T> {

        private final Class<T> type;
        private final Codec mapper;

        public JsonConverter(Class<T> type, Codec mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        @Override
        public T apply(ByteArrayOutputStream t) {
            try {
                return mapper.readValue(t.toByteArray(), type);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        }
    }

    private static final class TimeoutAssertion extends Assertion<Boolean, Boolean> {

        public TimeoutAssertion(boolean expectation, FailureSeverity severity) {
            super(expectation
                    ? "The request should time out"
                    : "The test should not time out",
                    severity, new BooleanPredicate(expectation));
        }

        @Override
        Boolean convert(Boolean obj) {
            return obj;
        }

        private static class BooleanPredicate implements Predicate<Boolean> {

            private final boolean expectation;

            BooleanPredicate(boolean expectation) {
                this.expectation = expectation;
            }

            @Override
            public boolean test(Boolean t) {
                return t != null && (t.booleanValue() == expectation);
            }

            @Override
            public String toString() {
                return Boolean.toString(expectation);
            }
        }
    }

    private static final class ThrowableAssertion extends Assertion<Throwable, Throwable> {

        public <T extends Throwable> ThrowableAssertion(String messageHead, FailureSeverity severity, Class<T> type) {
            super(messageHead, severity, new IsInstancePredicate<T, Throwable>(type));
        }

        @Override
        Throwable convert(Throwable obj) {
            return obj;
        }
    }

    private static final class IsInstancePredicate<T extends R, R> implements Predicate<R> {

        private final Class<T> type;

        public IsInstancePredicate(Class<T> type) {
            this.type = notNull("type", type);
        }

        @Override
        public boolean test(R t) {
            return type.isInstance(t);
        }

        @Override
        public String toString() {
            return "Is instance of " + type.getName();
        }
    }

    private static final class ChunkAssertion extends Assertion<ByteBuffer, ByteBuffer> {

        ChunkAssertion(String messageHead, FailureSeverity severity, Predicate<? super ByteBuffer> test) {
            super(messageHead, severity, test);
        }

        @Override
        ByteBuffer convert(ByteBuffer obj) {
            return obj;
        }
    }

    private static final class BodyAssertion<T> extends Assertion<ByteArrayOutputStream, T> {

        private final Function<ByteArrayOutputStream, T> converter;

        BodyAssertion(Function<ByteArrayOutputStream, T> converter, String description, FailureSeverity severity, Predicate<? super T> test) {
            super(description, severity, test);
            this.converter = converter;
        }

        @Override
        T convert(ByteArrayOutputStream obj) {
            return converter.apply(obj);
        }
    }

    private static final class HeaderAssertion extends Assertion<HttpResponse.ResponseInfo, String> {

        HeaderAssertion(String headerName, FailureSeverity severity, Predicate<? super String> test) {
            super(headerName, severity, test);
        }

        @Override
        String convert(HttpResponse.ResponseInfo obj) {
            return obj.headers().firstValue(messageHead).orElse(null);
        }
    }

    private static final class ResponseCodeAssertion extends Assertion<HttpResponse.ResponseInfo, Integer> {

        ResponseCodeAssertion(String messageHead, FailureSeverity severity, Predicate<? super Integer> test) {
            super(messageHead, severity, test);
        }

        @Override
        Integer convert(HttpResponse.ResponseInfo obj) {
            return obj.statusCode();
        }
    }

    private static final class VersionAssertion extends Assertion<HttpResponse.ResponseInfo, HttpClient.Version> {

        VersionAssertion(String messageHead, FailureSeverity severity, Predicate<? super HttpClient.Version> test) {
            super(messageHead, severity, test);
        }

        @Override
        HttpClient.Version convert(HttpResponse.ResponseInfo obj) {
            return obj.version();
        }
    }

    private static final class ExactVersionAssertion extends Assertion<HttpResponse.ResponseInfo, HttpClient.Version> {

        ExactVersionAssertion(FailureSeverity severity, Version expected) {
            super("HTTP Version", severity, new EqualityPredicate<Version>(expected));
        }

        @Override
        HttpClient.Version convert(HttpResponse.ResponseInfo obj) {
            return obj.version();
        }
    }

    private static final class EqualityPredicate<T> implements Predicate<T> {

        private final T what;

        public EqualityPredicate(T what) {
            this.what = what;
        }

        @Override
        public boolean test(T t) {
            return Objects.equals(what, t);
        }

        @Override
        public String toString() {
            return "equals(" + what + ")";
        }

    }

    private static Predicate<Integer> adapt(IntPredicate pred) {
        return new IntPredicateAdapter(pred);
    }

    static final class IntPredicateAdapter implements Predicate<Integer> {

        // And the JDK's IntPredicate doesn't implement this why?
        private final IntPredicate delegate;

        public IntPredicateAdapter(IntPredicate delegate) {
            this.delegate = delegate;
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public boolean test(Integer t) {
            return delegate.test(t);
        }
    }
}
