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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.preconditions.Checks;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creates a test harness with the configured global settings used for all
 * requests it makes.
 *
 * @author Tim Boudreau
 */
public final class TestHarnessBuilder {

    private HttpClient client;
    private Codec mapper;
    private Duration defaultTimeout;
    private Map<String, String> defaultHeaders;
    private HttpClient.Version version;
    private Consumer<AssertionResult> resultsConsumer;
    private Duration timeoutCheckInterval;
    private TestReport report;
    private Duration defaultOverallTimeout;
    private Supplier<String> testMethodFindingStrategy;
    private CountDownLatch awaitReady;
    private Semaphore concurrentRequestsThrottle;
    private BiConsumer<HarnessLogLevel, Supplier<String>> logger = new StdoutHarnessLog();

    TestHarnessBuilder() {
    }

    /**
     * If you are using the built-in stdout logging, configure the level at
     * which messages should be logged.
     *
     * @param level The level
     * @return this
     * @throws IllegalStateException if the logger was already set to something
     * other than the built-in one
     */
    public TestHarnessBuilder withMinimumLogLevel(HarnessLogLevel level) {
        if (!(logger instanceof StdoutHarnessLog)) {
            throw new IllegalStateException("Logger was already configured - have " + logger);
        }
        StdoutHarnessLog old = (StdoutHarnessLog) logger;
        logger = new StdoutHarnessLog(level, old.isStderr());
        return this;
    }

    /**
     * If you are using the built-in stdout logging, configure it to write to
     * stderr instead of stdout.
     *
     * @return this
     */
    public TestHarnessBuilder logToStderr() {
        if (!(logger instanceof StdoutHarnessLog)) {
            throw new IllegalStateException("Logger was already configured - have " + logger);
        }
        StdoutHarnessLog old = (StdoutHarnessLog) logger;
        logger = new StdoutHarnessLog(old.level(), true);
        return this;
    }

    /**
     * Some test frameworks/build-runners get very unhappy about writing to
     * stdout, so we include a way to replace the default log-to-stdout behavior
     * with something else if needed. The default log level can be set using the
     * system property
     * {@link com.mastfrog.http.harness.HarnessLogLevel#SYS_PROP_DEFAULT_LEVEL}.
     *
     * @param logger A logger
     * @return this
     */
    public TestHarnessBuilder replaceLogger(BiConsumer<HarnessLogLevel, Supplier<String>> logger) {
        this.logger = notNull("logger", logger);
        return this;
    }

    /**
     * Add a logger for internal logging to the default one that is already
     * present.
     *
     * @param logger
     * @return
     */
    public TestHarnessBuilder addLogger(BiConsumer<HarnessLogLevel, Supplier<String>> logger) {
        this.logger = this.logger.andThen(notNull("logger", logger));
        return this;
    }

    /**
     * If you need to block http requests from running until a server is started
     * and has opened a port to avoid spurious failures, pass a
     * {@link java.util.concurrent.CountDownLatch} here, and count it down once
     * the server is ready to receive requests.
     *
     * @param latch A count down latch
     * @return
     */
    public TestHarnessBuilder awaitingReadinessOn(CountDownLatch latch) {
        if (this.awaitReady != null && this.awaitReady != latch) {
            throw new IllegalStateException("Await latch already set");
        }
        this.awaitReady = notNull("latch", latch);
        return this;
    }

    /**
     * Build the test harness as configured - note the returned harness takes
     * complete URIs to make requests against - if you are starting a local
     * server (perhaps on a random port), you may want to call
     * <code>convertingToUrisWith(Function&lt;T,URI&gt;)</code> and provide a
     * function that converts paths or similar to URIs, so tests focus on what
     * they want to test and not on server coordinates.
     *
     * @return A test harness, ready to use
     */
    public HttpTestHarness<URI> build() {
        return new TestHarness(client, mapper, defaultTimeout, defaultHeaders,
                version, timeoutCheckInterval, resultsConsumer, report,
                defaultOverallTimeout, testMethodFindingStrategy, awaitReady,
                concurrentRequestsThrottle, logger);
    }

    /**
     * By default, the harness allows unlimited concurrent requests, and
     * requests are run asynchronously. If you need to limit this, do so, to a
     * value greater than or equal to one, here.
     * <p>
     * Note that if you are instantiating your test harness in a JUnit <code>&#064;Before<code> or
     * JUnit 5 <code>&#064;BeforeEach</code> setup method, you need to use
     * {@link com.mastfrog.http.harness.TestHarnessBuilder#throttlingRequestsWith(Semaphore)} instead, or
     * you will get one semaphore per test method and no throttling will happen.
     * </p>
     *
     * @param maxConcurrentRequests The greatest number of concurrent requests
     * the harness should make at a time
     * @return this
     */
    public TestHarnessBuilder withMaxConcurrentRequests(int maxConcurrentRequests) {
        concurrentRequestsThrottle = new Semaphore(Checks.greaterThanZero("concurrentRequests",
                maxConcurrentRequests), false);
        return this;
    }

    /**
     * Throttle requests using a shared semaphore.
     *
     * @param semaphore A semaphore
     * @return this
     */
    public TestHarnessBuilder throttlingRequestsWith(Semaphore semaphore) {
        if (this.concurrentRequestsThrottle != null && this.concurrentRequestsThrottle != semaphore) {
            throw new IllegalStateException("Semaphore already set to " + this.concurrentRequestsThrottle);
        }
        this.concurrentRequestsThrottle = semaphore;
        return this;
    }

    /**
     * For logging and reporting purposes, provide a strategy for finding the
     * name of the test method invoking the test harness. By default, you get
     * stack inspection which will find the first method in the stack that is
     * not part of this library. If you have deeply nested calls before running
     * your tests, you may want to use a ThreadLocal or similar.
     *
     * @param supp A supplier of a name
     * @return this
     */
    public TestHarnessBuilder withTestMethodNameFindingStrategy(Supplier<String> supp) {
        this.testMethodFindingStrategy = supp;
        return this;
    }

    /**
     * Add a test report which will be notified of the results of each test run,
     * which can be saved as JSON once all tests run in this harness are
     * complete.
     *
     * @param report A test report
     * @return this
     */
    public TestHarnessBuilder withTestReport(TestReport report) {
        this.report = report;
        return this;
    }

    /**
     * Set a default timeout for the total amount of time any request can take,
     * including the response body.
     *
     * @param timeout The maximum time a request can be in progress before it
     * will be timed out and its connection closed.
     * @return this
     */
    public TestHarnessBuilder withDefaultResponseTimeout(Duration timeout) {
        defaultOverallTimeout = timeout;
        return this;
    }

    /**
     * Set the interval at which a background thread will poll requests
     * in-progress to abort them if they have reached their timeout. The
     * underlying HTTP client's request has a settable timeout (set by calling
     * withInitialResponseTimeout) which handles the failure of the server to
     * send <i>any</i> bytes within some interval; we use our own timeout to
     * determine if the request is in progress but has taken too long, with a
     * background thread periodically polling any open requests and aborting
     * them if they are past that time-out. This method sets how frequently
     * those checks occur (the default is 100ms).
     *
     * @param dur A duration greater than zero
     * @return this
     */
    public TestHarnessBuilder withWatchdogInterval(Duration dur) {
        if (dur.toMillis() <= 0) { // yes, it can be negative, whatever that means
            throw new IllegalArgumentException("Invalid check interval " + dur);
        }
        this.timeoutCheckInterval = dur;
        return this;
    }

    /**
     * Set the HTTP client that will be used for making requests.
     *
     * @param client The client
     * @return this
     */
    public TestHarnessBuilder withClient(HttpClient client) {
        this.client = client;
        return this;
    }

    /**
     * Set the ObjectMapper that will be used to serialize and deserialize JSON;
     * convenience method for <code>setCodec</code>.
     *
     * @param mapper A mapper
     * @return this
     */
    public TestHarnessBuilder withMapper(ObjectMapper mapper) {
        return withCodec(new ObjectMapperCodec(mapper));
    }

    /**
     * Set the Codec that will be used to serialize and deserialize or similar.
     *
     * @param codec A codec
     * @return this
     */
    public TestHarnessBuilder withCodec(Codec codec) {
        this.mapper = codec;
        return this;
    }

    /**
     * Set the default timeout applied to the HTTP request for how long it can
     * take for the initial response line / start-of-headers to arrive; this has
     * nothing to do with how long the entire response takes. This value can be
     * overridden on a per-request basis.
     *
     * @param defaultTimeout The default timeout
     * @return this
     */
    public TestHarnessBuilder withInitialResponseTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        return this;
    }

    /**
     * Add a header to include in all requests.
     *
     * @param name The header name
     * @param val The header value
     * @return this
     */
    public TestHarnessBuilder withHeader(String name, String val) {
        if (this.defaultHeaders == null) {
            this.defaultHeaders = new LinkedHashMap<>(16);
        }
        this.defaultHeaders.put(name, val);
        return this;
    }

    /**
     * Add headers to be included in all requests.
     *
     * @param defaultHeaders Some headers
     * @return this
     */
    public TestHarnessBuilder withHeaders(Map<String, String> defaultHeaders) {
        if (this.defaultHeaders == null) {
            this.defaultHeaders = new LinkedHashMap<>(notNull("defaultHeaders", defaultHeaders));
        } else {
            this.defaultHeaders.putAll(notNull("defaultHeaders", defaultHeaders));
        }
        return this;
    }

    /**
     * Set the HTTP version requests from this client should use.
     *
     * @param version A version
     * @return this
     */
    public TestHarnessBuilder withHttpVersion(HttpClient.Version version) {
        this.version = version;
        return this;
    }

    /**
     * Add a consumer for test results, which will be called on the response
     * processing thread(s) as the request is completed.
     *
     * @param resultsConsumer A consumer
     * @return this
     */
    public TestHarnessBuilder withResultsConsumer(Consumer<AssertionResult> resultsConsumer) {
        if (this.resultsConsumer != null) {
            this.resultsConsumer = this.resultsConsumer.andThen(resultsConsumer);
        } else {
            this.resultsConsumer = resultsConsumer;
        }
        return this;
    }
}
