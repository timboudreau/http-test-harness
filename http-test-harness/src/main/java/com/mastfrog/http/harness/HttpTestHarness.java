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

import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * A test harness for HTTP web apis - the general pattern of use is, you define
 * a request to make, using one of the methods that returns a
 * <code>TestRequest</code>, configure it with any headers or other things
 * needed, and then call its <code>test(Consumer&lt;Assertions&gt;)</code>
 * method, set up your assertions within the closure of that callback, after
 * which the request runs and assertions are applied. That call gives you back a
 * <code>TestResults</code> object, which further operations can be performed
 * on. The calling thread is blocked until the response is complete; if you wish
 * not to block the calling thread, use
 * <code>TestRequest.applyingAssertions()</code> instead.
 * <p>
 * Some code being worth a thousand words:
 * </p>
 * <pre>
 *         harness.get("http://localhost:12345/hello")
 *                 .header("user-agent", "Uber-Whatzis-1.0")
 *                 .responseStartTimeout(Duration.ofSeconds(10))
 *                 .responseFinishedTimeout(Duration.ofSeconds(10))
 *                 .test((Assertions asserts) -> {
 *                     asserts.assertHasHeader("content-type")
 *                             .assertHasBody()
 *                             .assertHasHeader("wookies")
 *                             .assertHeaderEquals("wookies", "food")
 *                             .assertResponseCodeIn(200, 201)
 *                             .assertBody(StringPredicates.predicate("Hello world!"));
 *                 }).printResults();
 * </pre>
 * <p>
 * Since it is common for tests to start a server on a random port that cannot
 * be hard-coded into a test (unless you want to prevent concurrent tests), the
 * convenience method <code>convertingToUrisWith()</code> allows you to, say,
 * use just the path portion of a URL as a string in all of the methods that
 * return a <code>TestRequest</code>, supplying a function that will apply the
 * protocol and host and port and convert that into a usable URI.
 * </p>
 * <p>
 * <b>Asynchronous operation:</b> The test harness is full asynchronous, and
 * does not tie up the calling thread (or any others) unless you want it to,
 * making parallelizing tests trivial.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface HttpTestHarness<U> {

    /**
     * Initiate an HTTP GET request.
     *
     * @param uri A URI or similar object
     * @return An object for configuring and launching the request
     */
    TestRequest get(U uri);

    /**
     * Initiate an HTTP DELETE request.
     *
     * @param uri A URI or similar object
     * @return An object for configuring and launching the request
     */
    TestRequest delete(U uri);

    /**
     * Initiate an HTTP PUT request, with a byte-array body.
     *
     * @param uri A URI or similar object
     * @param bytes Some bytes
     * @return An object for configuring and launching the request
     */
    TestRequest put(U uri, byte[] bytes);

    /**
     * Initiate an HTTP PUT request, with a string body which will be encoded
     * using UTF-8 encoding.
     *
     * @param uri A URI or similar object
     * @param string some text
     * @return An object for configuring and launching the request
     */
    TestRequest put(U uri, String string);

    /**
     * Initiate an HTTP PUT request, with a string body which will be encoded
     * using the passed character set.
     *
     * @param uri A URI or similar object
     * @param string some text
     * @param charset A character set
     * @return An object for configuring and launching the request
     */
    TestRequest put(U uri, String string, Charset charset);

    /**
     * Initiate an HTTP PUT request, with a BodyPublisher that the HTTP client
     * will call back to fetch the request body.
     *
     * @param uri A URI or similar object
     * @param pub A body publisher from the JDK's HTTP client api
     * @return An object for configuring and launching the request
     */
    TestRequest put(U uri, HttpRequest.BodyPublisher pub);

    /**
     * Initiate an HTTP PUT request, with a request body serialized from the
     * passed object using the Codec this harness was configured with when it
     * was created (for example, Jackson's ObjectMapper).
     *
     * @param <T> A type
     * @param uri A URI or similar
     * @param toSerialize An object to convert using the codec into a byte
     * stream
     * @return this
     */
    <T> TestRequest putObject(U uri, T toSerialize);

    /**
     * Initiate an HTTP POST request, with a byte-array body.
     *
     * @param uri A URI or similar object
     * @param bytes Some bytes
     * @return An object for configuring and launching the request
     */
    TestRequest post(U uri, byte[] bytes);

    /**
     * Initiate an HTTP POST request, with a string body which will be encoded
     * using UTF-8 encoding.
     *
     * @param uri A URI or similar object
     * @param string some text
     * @return An object for configuring and launching the request
     */
    TestRequest post(U uri, String string);

    /**
     * Initiate an HTTP POST request, with a string body which will be encoded
     * using the passed character set.
     *
     * @param uri A URI or similar object
     * @param string some text
     * @param charset A character set
     * @return An object for configuring and launching the request
     */
    TestRequest post(U uri, String string, Charset charset);

    /**
     * Initiate an HTTP PUT request, with a BodyPublisher that the HTTP client
     * will call back to fetch the request body.
     *
     * @param uri A URI or similar object
     * @param pub A body publisher from the JDK's HTTP client api
     * @return An object for configuring and launching the request
     */
    TestRequest post(U uri, HttpRequest.BodyPublisher pub);

    /**
     * Initiate an HTTP POST request, with a request body serialized from the
     * passed object using the Codec this harness was configured with when it
     * was created (for example, Jackson's ObjectMapper).
     *
     * @param <T> A type
     * @param uri A URI or similar
     * @param toSerialize An object to convert using the codec into a byte
     * stream
     * @return this
     */
    <T> TestRequest postObject(U uri, T toSerialize);

    /**
     * Shut down this test harness, immediately aborting any requests in
     * progress, and waiting for them to exit.
     *
     * @return this
     */
    HttpTestHarness<U> shutdown();

    /**
     * Await exit of all running requests. New requests may still be submitted,
     * and await() can be used again - this simply waits until the count of
     * running requests has dropped to zero.
     *
     * @param dur A timeout
     * @return true if running request exited before the timeout
     * @throws InterruptedException
     */
    boolean await(Duration dur) throws InterruptedException;

    /**
     * Await exit of all running requests, for however long that takes. New
     * requests may still be submitted, and await() can be used again - this
     * simply waits until the count of running requests has dropped to zero.
     *
     * @return this
     * @throws InterruptedException
     */
    HttpTestHarness<U> await() throws InterruptedException;

    /**
     * Initiate a new request (you will need to set the URI, HTTP method, and so
     * forth on it to have a valid request - see the javadoc for the JDK's
     * HttpRequestBuilder).
     *
     * @return A request
     */
    TestRequest request();

    /**
     * For convenience, if the protocol or port in your test varies, use this
     * function to allow your tests to pass something like a string URL path +
     * query, and convert that to a URI in the function you pass here, so tests
     * stay focused on business logic, not configuration details.
     * <p>
     * Note that if you have many tests that are served well by this but a few
     * that really need to pass a URI directly, you can always call
     * <code>request()</code> and set the method and URI on the resulting
     * <code>TestRequest</code> which remains URI-based.
     * </p>
     *
     * @param <T> The type to convert from
     * @param converter A conversion function
     * @return A wrapper for this HTTP test harness that takes a different type
     * for its uri parameters
     */
    default <T> HttpTestHarness<T> convertingToUrisWith(Function<T, U> converter) {
        return new URIConvertingTestHarness<>(converter, this);
    }

    /**
     * Get a list of all running or historically run tasks in this test harness,
     * which can be used to cancel tasks or get details of their status.
     *
     * @return A list of tasks
     */
    List<? extends Task> tasks();

    /**
     * Get the number of tasks which are currently running and have neither
     * failed, completed, timed out nor been cancelled.
     *
     * @return A task count greater than or equal to zero
     */
    int currentlyRunningTasks();

    /**
     * Create a builder for a new HttpTestHarness.
     *
     * @return A builder
     */
    public static TestHarnessBuilder builder() {
        return new TestHarnessBuilder();
    }
    
    public void awaitQuiet(Duration dur, boolean killOnTimeout);
}
