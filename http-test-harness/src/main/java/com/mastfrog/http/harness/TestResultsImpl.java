package com.mastfrog.http.harness;

import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class TestResultsImpl implements TestResults<HttpResponse<String>> {

    final List<AssertionResult> liveResults;
    private final URI uri;
    private final Task task;
    private final CountDownLatch awaitDone;
    private volatile boolean anyFailures;
    private final CompletableFuture<HttpResponse<String>> future;
    private final String httpMethod;
    private final String testMethod;
    private final long launchedAt;
    private volatile Duration runDuration;
    private final BiConsumer<HarnessLogLevel, Supplier<String>> logger;
    private final Codec codec;

    TestResultsImpl(String testMethod, String httpMethod, URI uri, Task task,
            CountDownLatch awaitDone, CompletableFuture<HttpResponse<String>> future,
            List<AssertionResult> liveResults, long launchedAt,
            BiConsumer<HarnessLogLevel, Supplier<String>> logger,
            Codec codec) {
        this.liveResults = liveResults;
        this.uri = uri;
        this.task = task;
        this.awaitDone = awaitDone;
        this.future = future;
        this.httpMethod = httpMethod;
        this.testMethod = testMethod;
        this.logger = logger;
        this.launchedAt = launchedAt;
        future.whenComplete((resp, thrown) -> {
            runDuration = Duration.ofMillis(System.currentTimeMillis() - this.launchedAt);
        });
        this.codec = codec;
    }

    @Override
    public BiConsumer<HarnessLogLevel, Supplier<String>> logger() {
        return logger;
    }

    public Instant launchedAt() {
        return Instant.ofEpochMilli(launchedAt);
    }

    @Override
    public Duration runDuration() {
        Duration result = runDuration;
        if (result == null) {
            result = Duration.ofMillis(System.currentTimeMillis() - this.launchedAt);
        }
        return result;
    }

    @Override
    public String testMethod() {
        return testMethod == null ? "-unknown-" : testMethod;
    }

    @Override
    public HttpResponse<String> get() {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            return Exceptions.chuck(ex);
        } catch (ExecutionException ex) {
            return Exceptions.chuck(ex.getCause());
        }
    }

    @Override
    public TestResultsImpl await() throws InterruptedException {
        awaitDone.await();
        return this;
    }

    @Override
    public boolean await(Duration dur) throws InterruptedException {
        return awaitDone.await(dur.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean cancel() {
        return task.cancel();
    }

    @Override
    public TaskState state() {
        return task.state();
    }

    @Override
    public boolean isNotYetCompleted() {
        return task.state() == TaskState.RUNNING;
    }

    @Override
    public boolean hasFailures() {
        return anyFailures;
    }

    @Override
    public <R> R get(Class<R> deserializeAs) throws IOException {
        String bodyText = get().body();
        if (bodyText == null) {
            return null;
        }
        return codec.readValue(bodyText, deserializeAs);
    }

    @Override
    public TestResultsImpl assertNoMatches(BiPredicate<AssertionStatus, FailureSeverity> bip) {
        try {
            await();
        } catch (InterruptedException ex) {
            return Exceptions.chuck(ex);
        }
        for (AssertionResult r : this) {
            if (bip.test(r.status(), r.severity())) {
                anyFailures = true;
                break;
            }
        }
        if (anyFailures) {
            List<AssertionResult> failures = new ArrayList<>();
            for (AssertionResult r : this) {
                if (!r.isOk() || bip.test(r.status(), r.severity())) {
                    failures.add(r);
                }
            }
            StringBuilder sb = assertionListToString(failures);
            throw new AssertionError(sb);
        }
        return this;
    }

    private StringBuilder assertionListToString(List<AssertionResult> failures) {
        StringBuilder sb = new StringBuilder(testMethod).append(' ')
                .append(httpMethod)
                .append(' ')
                .append(uri)
                .append(" with ")
                .append(failures.size())
                .append(" failed assertions:");
        for (AssertionResult res : failures) {
            sb.append('\n');
            sb.append(" * ").append(res);
        }
        return sb;
    }

    @Override
    public TestResultsImpl printResults(PrintStream st) {
        logger.accept(HarnessLogLevel.DETAIL, () -> testMethod + " " + runDuration());
        logger.accept(HarnessLogLevel.DETAIL, () -> " * " + httpMethod + " " + uri
                + " with " + liveResults.size() + " assertion results:");
        for (AssertionResult r : this) {
            logger.accept(HarnessLogLevel.DETAIL, () -> "   * " + r);
        }
        return this;
    }

    @Override
    public Iterator<AssertionResult> iterator() {
        return liveResults.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(httpMethod)
                .append(' ')
                .append(uri)
                .append(' ')
                .append(runDuration())
                .append(" with ")
                .append(liveResults.size())
                .append(" assertions:");
        for (AssertionResult res : liveResults) {
            sb.append('\n');
            sb.append(" * ").append(res);
        }
        return sb.toString();
    }
}
