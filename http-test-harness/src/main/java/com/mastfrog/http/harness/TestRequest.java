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

import com.mastfrog.util.codec.Codec;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A request which can be invoked against an HTTP server, performing a set of
 * assertions on the response headers and body. Configure the request, then call
 * <code>testedBy()</code> which will be passed an <code>Assertions</code> that
 * you can add tests of the headers and response body to.
 * <p>
 * In general, the API of this class mirrors that of the JDK's HTTP client's,
 * request type, which it uses under-the-hood.
 *
 * @author Tim Boudreau
 */
public abstract class TestRequest {

    HttpRequest.Builder bldr;
    Consumer<AssertionResult> additionalResultConsumer;
    Duration overallResponseTimeout;
    BiConsumer<HarnessLogLevel, Supplier<String>> logger;
    Codec codec;

    TestRequest(HttpRequest.Builder bldr, Codec codec, BiConsumer<HarnessLogLevel, Supplier<String>> logger) {
        this.bldr = bldr;
        this.logger = logger;
        this.codec = codec;
    }

    /**
     * Provide a codec other than the one provisioned when constructing the
     * harness, for serializing and deserializing objects in this particular
     * request.
     *
     * @param codec A codec, not null
     * @return this
     */
    public final TestRequest withCodec(Codec codec) {
        this.codec = notNull("codec", codec);
        return this;
    }

    /**
     * Add an additional logger that captures internal output for this
     * particular request.
     *
     * @param logger A logger
     * @return this
     */
    public final TestRequest addLogger(BiConsumer<HarnessLogLevel, Supplier<String>> logger) {
        this.logger = this.logger.andThen(notNull("logger", logger));
        return this;
    }

    /**
     * Add a consumer which should be called with each assertion result as it is
     * computed.
     *
     * @param resultConsumer A consumer
     * @return this
     */
    public TestRequest withResultConsumer(Consumer<AssertionResult> resultConsumer) {
        notNull("resultConsumer", resultConsumer);
        if (additionalResultConsumer != null) {
            Consumer<AssertionResult> prev = additionalResultConsumer;
            additionalResultConsumer = res -> {
                prev.accept(res);
                resultConsumer.accept(res);
            };
        } else {
            this.additionalResultConsumer = resultConsumer;
        }
        return this;
    }

    public final TestRequest uri(URI uri) {
        bldr = bldr.uri(uri);
        return this;
    }

    public final TestRequest expectContinue(boolean enable) {
        bldr = bldr.expectContinue(enable);
        return this;
    }

    public final TestRequest version(HttpClient.Version version) {
        bldr = bldr.version(version);
        return this;
    }

    public final TestRequest header(String name, String value) {
        bldr = bldr.header(name, value);
        return this;
    }

    public final TestRequest headers(String... headers) {
        bldr = bldr.headers(headers);
        return this;
    }

    /**
     * Set the HTTP client request's built in time-out - this timeout only
     * applies to how long between the request being flushed to the network
     * socket and the first bytes arriving in response.  <i>It has nothing to do
     * with how long a response takes once bytes are in-flight.</i>
     * To limit the total duration of an http request, use
     * <code>responseFinishedTimeout(Duration)</code>.
     *
     * @param duration A duration
     * @return this
     */
    public final TestRequest responseStartTimeout(Duration duration) {
        bldr = bldr.timeout(duration);
        return this;
    }

    /**
     * Set a maximum duration for the amount of time after initiating the
     * request that the entire response must be finished and closed in.
     *
     * @param duration A duration
     * @return this
     */
    public final TestRequest responseFinishedTimeout(Duration duration) {
        this.overallResponseTimeout = duration;
        return this;
    }

    /**
     * Set an HTTP header on the request.
     *
     * @param name The name
     * @param value The value
     * @return this
     */
    public final TestRequest setHeader(String name, String value) {
        bldr = bldr.setHeader(name, value);
        return this;
    }

    /**
     * Set a header, using a framework's header converter. This method assumes
     * the passed function's toString() will return the header name - so it can
     * be used with, for example, acteur-header's Headers objects - e.g.
     * <code>setHeader(Headers.IF_MODIFIED_SINCE, ZonedDateTime.now())</code>,
     * which handles correctly converting a date into an http header.
     *
     * @param <T> The value type
     * @param f A function
     * @param value A value
     * @return this
     */
    public final <T> TestRequest setHeader(Function<? super T, ? extends CharSequence> f, T value) {
        CharSequence val = f.apply(value);
        return setHeader(f.toString(), val.toString());
    }

    public final TestRequest GET() {
        bldr = bldr.GET();
        return this;
    }

    public final TestRequest POST(HttpRequest.BodyPublisher bodyPublisher) {
        bldr = bldr.POST(bodyPublisher);
        return this;
    }

    public final TestRequest PUT(HttpRequest.BodyPublisher bodyPublisher) {
        bldr = bldr.PUT(bodyPublisher);
        return this;
    }

    public final TestRequest DELETE() {
        bldr = bldr.DELETE();
        return this;
    }

    public final TestRequest method(String method, BodyPublisher bodyPublisher) {
        bldr = bldr.method(method, bodyPublisher);
        return this;
    }

    public final TestRequest method(String method) {
        return method(method, noBody());
    }

    /**
     * Perform the request, applying any assertions added by the consumer passed
     * here, blocking until the response is complete or the test has failed, and
     * throwing an assertion error if any tests fail.
     *
     * @param c A consumer which sets up the assertions about this request which
     * must pass
     * @return A test results object
     */
    public final TestResults<HttpResponse<String>> test(Consumer<Assertions> c) {
        return applyingAssertions(c).assertNoFailures();
    }

    /**
     * Initiate the request, <b>not blocking the calling thread or waiting for
     * results</b> - the returned <code>TestResults</code> can be waited on when
     * the caller wishes.
     *
     * @param c A consumer which applies assertions that should be run against
     * the response
     * @return a test results
     */
    public abstract TestResults<HttpResponse<String>> applyingAssertions(
            Consumer<Assertions> c);
}
