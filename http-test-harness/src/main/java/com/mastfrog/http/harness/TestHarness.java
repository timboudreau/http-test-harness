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
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A general-purpose HTTP test-harness allowing for sets of assertions applied
 * to HTTP requests.
 *
 * @author Tim Boudreau
 */
final class TestHarness extends AbstractHttpTestHarness {

    private final HttpClient client;
    private final Duration defaultResponseStartTimeout;
    private final Map<String, String> defaultHeaders;
    private final Version defaultVersion;
    private final Consumer<AssertionResult> resultsConsumer;
    private final Bookkeeping bookkeeping = new Bookkeeping();
    private final IncrementableLatch latch = IncrementableLatch.create();
    private final ScheduledExecutorService checkTimeouts
            = Executors.newScheduledThreadPool(1);
    private final Duration timeoutCheckInterval;
    private final Optional<TestReport> report;
    private final Optional<Duration> defaultOverallTimeout;
    private final Supplier<String> testMethodFindingStrategy;
    private final Optional<CountDownLatch> awaitReady;
    private final Optional<Semaphore> concurrentRequestsThrottle;
    private final Optional<RequestIdProvider> requestIdProvider;
    private final BiConsumer<HarnessLogLevel, Supplier<String>> logger;

    TestHarness(HttpClient client, Codec codec, Duration defaultTimeout,
            Map<String, String> defaultHeaders, Version defaultVersion,
            Duration timeoutCheckInterval,
            Consumer<AssertionResult> resultsConsumer,
            TestReport report, Duration defaultOverallTimeout,
            Supplier<String> testMethodFindingStrategy,
            CountDownLatch awaitReady, Semaphore concurrentRequestsThrottle,
            RequestIdProvider requestIdProvider,
            BiConsumer<HarnessLogLevel, Supplier<String>> logger) {
        super(codec == null
                ? new ObjectMapperCodec()
                : codec);
        this.client = client == null
                ? HttpClient.newHttpClient()
                : client;
        this.timeoutCheckInterval = timeoutCheckInterval == null
                ? Duration.ofMillis(120)
                : timeoutCheckInterval;
        this.defaultResponseStartTimeout = defaultTimeout == null
                ? Duration.ofMinutes(1)
                : defaultTimeout;
        this.defaultHeaders = defaultHeaders == null
                ? Collections.emptyMap()
                : defaultHeaders;
        this.defaultVersion = defaultVersion == null
                ? Version.HTTP_2
                : defaultVersion;
        this.resultsConsumer = resultsConsumer == null
                ? res -> {
                }
                : resultsConsumer;
        this.report = Optional.ofNullable(report);
        this.defaultOverallTimeout = Optional.ofNullable(defaultOverallTimeout);
        this.testMethodFindingStrategy = testMethodFindingStrategy == null
                ? TestHarness::findCallingMethod
                : testMethodFindingStrategy;
        this.awaitReady = Optional.ofNullable(awaitReady);
        this.concurrentRequestsThrottle = Optional.ofNullable(concurrentRequestsThrottle);
        this.logger = logger;
        this.requestIdProvider = Optional.ofNullable(requestIdProvider);
    }

    private boolean awaitReady() {
        awaitReady.ifPresent(awr -> {
            if (awr.getCount() > 0) {
                Duration wait = defaultOverallTimeout.orElse(Duration.ofSeconds(30));
                try {
                    awr.await(wait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    logger.accept(HarnessLogLevel.IMPORTANT, () -> {
                        return "Interrupted waiting for server to be ready:\n"
                                + Strings.toString(ex);
                    });
                }
            }
        });
        return !Thread.interrupted();
    }

    private Runnable acquirePermitIfThrottlingRequests() {
        return concurrentRequestsThrottle.<Runnable>map(sem -> {
            try {
                AtomicBoolean released = new AtomicBoolean();
                int permits = sem.availablePermits();
                logger.accept(HarnessLogLevel.DEBUG, () -> "Attempt to acquire one of " + permits + " permits"
                        + " on " + Thread.currentThread().getName());
                long then = System.currentTimeMillis();
                sem.acquire();
                long elapsed = System.currentTimeMillis() - then;
                logger.accept(HarnessLogLevel.DEBUG,
                        () -> "Acquired permit of " + sem.availablePermits() + " had " + permits
                        + " on " + Thread.currentThread().getName() + " in " + elapsed + "ms");

                return () -> {
                    // Ensures that if the release call is attached both to the completable future
                    // and also called if an exception is thrown, we can't wind up releasing an
                    // extra permit
                    if (released.compareAndSet(false, true)) {
                        logger.accept(HarnessLogLevel.DEBUG, () -> "  release permit on "
                                + Thread.currentThread() + " avail "
                                + sem.availablePermits());
                        sem.release();
                    }
                };
            } catch (InterruptedException ex) {
                return Exceptions.chuck(ex);
            }
        }).orElse(() -> {
            logger.accept(HarnessLogLevel.DEBUG, () -> "no semaphore, no permit needed");
        });
    }

    @Override
    public List<? extends Task> tasks() {
        return bookkeeping.tasks();
    }

    @Override
    public int currentlyRunningTasks() {
        return bookkeeping.running();
    }

    @Override
    public TestHarness shutdown() {
        try {
            bookkeeping.cancelAll();
            try {
                latch.await(Duration.ofSeconds(30));
            } catch (InterruptedException ex) {
                logger.accept(HarnessLogLevel.IMPORTANT, () -> {
                    return "Interrupted waiting for all requests to exit.\n"
                            + Strings.toString(ex);
                });
            }
            latch.releaseAll();
            concurrentRequestsThrottle.ifPresent(sem -> sem.drainPermits());
        } finally {
            checkTimeouts.shutdownNow();
        }
        return this;
    }
    
    public void awaitQuiet(Duration dur, boolean killOnTimeout) {
        bookkeeping.awaitQuiet(dur, killOnTimeout);
    }

    @Override
    public boolean await(Duration dur) throws InterruptedException {
        return latch.await(dur);
    }

    @Override
    public TestHarness await() throws InterruptedException {
        latch.await();
        return this;
    }

    private HttpRequest.Builder newRequestBuilder() {
        HttpRequest.Builder result = HttpRequest
                .newBuilder()
                .timeout(defaultResponseStartTimeout)
                .version(defaultVersion);
        defaultHeaders.forEach((k, v) -> {
            result.header(k, v);
        });
        return result;
    }

    @Override
    public TestRequest request() {
        return new TestRequestBuilder();
    }

    private static String findCallingMethod() {
        for (StackTraceElement stackTrace : new Exception().getStackTrace()) {
            String s = stackTrace.toString();
            if (s.contains("TestRequest.java") || s.contains("TestHarness.java")) {
                continue;
            }
            return stackTrace.getMethodName();
        }
        return "-unknown-";
    }

    final class TestRequestBuilder extends TestRequest {

        TestRequestBuilder(HttpRequest.Builder bldr) {
            super(bldr, TestHarness.this.codec, TestHarness.this.logger);
            defaultOverallTimeout.ifPresent(dur -> {
                this.responseFinishedTimeout(dur);
            });
        }

        TestRequestBuilder() {
            this(newRequestBuilder());
        }

        private Consumer<AssertionResult> resultConsumer(List<AssertionResult> also) {
            Consumer<AssertionResult> consumer = TestHarness.this.resultsConsumer;
            Consumer<AssertionResult> b = this.additionalResultConsumer;
            if (b != null) {
                consumer = consumer.andThen(b);
            }
            if (also != null) {
                consumer = consumer.andThen(also::add);
            }
            return consumer;
        }

        @Override
        public TestResults<HttpResponse<String>> asserting(Consumer<Assertions> assertionConfigurer) {
            String testMethod = testMethodFindingStrategy.get();
            Thread.currentThread().setName(testMethod + " (was: " + Thread.currentThread().getName() + ")");
            if (!awaitReady()) {
                throw new IllegalStateException("Interrupted waiting for server start or similar.");
            }
            latch.increment();
            Runnable releasePermit = acquirePermitIfThrottlingRequests();
            this.logger.accept(HarnessLogLevel.DEBUG, () -> "start " + testMethod + " on "
                    + Thread.currentThread().getName() + " fork "
                    + System.getProperty("forkNumber"));
            CompletableFuture<HttpResponse<String>> fut = null;
            try {
                List<AssertionResult> list = new CopyOnWriteArrayList<>();
                AtomicBoolean aborted = new AtomicBoolean();
                requestIdProvider.ifPresent(idProvider -> {
                    bldr.header(idProvider.headerName(), idProvider.newRequestId(bldr.build(), testMethod));
                });
                HttpRequest req = bldr.build();
                String reqInfo = req.method() + " " + req.uri();
                AssertionsImpl assertions = new AssertionsImpl(
                        reqInfo, resultConsumer(list), aborted, super.codec, latch,
                        Optional.ofNullable(super.overallResponseTimeout));
                assertionConfigurer.accept(assertions);
                long launchAt = System.currentTimeMillis();
                fut = client.sendAsync(req, assertions);
                long timeoutMillis = timeoutCheckInterval.toMillis();
                ScheduledFuture<?> timeoutChecks
                        = checkTimeouts.scheduleAtFixedRate(assertions,
                                timeoutMillis, timeoutMillis,
                                TimeUnit.MILLISECONDS);

                CountDownLatch oneRequestCountDown = new CountDownLatch(1);
                fut.whenCompleteAsync((resp, thrown) -> {
                    releasePermit.run();
                    if (thrown != null) {
                        if (thrown.getCause() != null) {
                            assertions.onError(thrown.getCause());
                        } else {
                            assertions.onError(thrown);
                        }
                    }
                    oneRequestCountDown.countDown();
                    timeoutChecks.cancel(true);
                    if (thrown instanceof HttpTimeoutException) {
                        assertions.onTimeout();
                    }
                });
                Task task = bookkeeping.register(req.toString(), aborted, fut);
                assertions.launched(launchAt, task);
                TestResults<HttpResponse<String>> results = new TestResultsImpl(
                        testMethod,
                        req.method(),
                        req.uri(),
                        task,
                        oneRequestCountDown,
                        fut,
                        list,
                        launchAt,
                        this.logger,
                        super.codec);
                report.ifPresent(rep -> rep.add(results));
                return results;
            } catch (Exception | Error e) {
                latch.countDown();
                if (fut != null) {
                    fut.completeExceptionally(e);
                }
                releasePermit.run();
                return Exceptions.chuck(e);
            }
        }
    }
}
