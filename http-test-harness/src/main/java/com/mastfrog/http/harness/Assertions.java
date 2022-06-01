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

import com.mastfrog.predicates.Predicates;
import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.predicates.string.StringPredicates;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Assertions that can be applied against the response to an HTTP request; built
 * in methods cover most common scenarios.
 * <p>
 * <b>Note if you pass in your own {@link java.util.function.Predicate}
 * implementations:</b> It is helpful, for report-generation purposes, if your
 * predicate has a reasonable implementation of
 * {@link java.lang.Object#toString} that describes what exactly is being tested
 * and how, as that will be used in output, and <code>FooClass$$lambda</code> is
 * not very descriptive.
 * </p>
 * <p>
 * A dependency of this library,
 * <a href="https://mvnrepository.com/artifact/com.mastfrog/predicates"><code>com.mastfrog:predicates</code></a>
 * contains wrapper predicates that take care of this, as well as useful
 * predicates for common kinds of tests. See
 * {@link com.mastfrog.predicates.Predicates}, and in particular,
 * <code>Predicates.namedPredicate(String, Predicate&lt;T&gt;)</code>.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface Assertions {

    /**
     * Assert that a header is present in the response's headers, without regard
     * for what its value is.
     *
     * @param header The header string
     * @return this
     */
    default Assertions assertHasHeader(String header) {
        return assertHeader(header, new HeaderPresence(header));
    }

    /**
     * Assert that a header is <b>not</b> present in the response's headers.
     *
     * @param header The header string
     * @return this
     */
    default Assertions assertNoHeader(String header) {
        return assertHeader(header, new HeaderPresence(header, false));
    }

    /**
     * Assert that a header with the passed header name is present in the HTTP
     * response's headers, and that its value exactly matches the passed value.
     *
     * @param headerName
     * @param exactHeaderValue
     * @return this
     */
    default Assertions assertHeaderEquals(String headerName, String exactHeaderValue) {
        return assertHeader(headerName, StringPredicates.predicate(exactHeaderValue));
    }

    /**
     * Test that a given HTTP header matches the passed predicate; note that the
     * predicate may be passed <code>null</code> if the header is absent
     * (sometimes the absence is what you are testing for).
     *
     * @param header A header
     * @param headerValueTest A predicate
     * @return this
     */
    Assertions assertHeader(String header, Predicate<? super String> headerValueTest);

    /**
     * Assert that the HTTP response code is exactly equal to the passed
     * integer.
     *
     * @param value An integer
     * @return this
     */
    default Assertions assertResponseCode(int value) {
        return assertResponseCode(IntPredicates.matching(value));
    }

    /**
     * Assert that the response code is less than some value (such as 400).
     *
     * @param value A number
     * @return this
     */
    default Assertions assertResponseCodeLessThan(int value) {
        return assertResponseCode(IntPredicates.lessThan(value));
    }

    /**
     * Assert that the response
     *
     * @param value
     * @return
     */
    default Assertions assertResponseCodeGreaterThan(int value) {
        return assertResponseCode(IntPredicates.greaterThan(value));
    }

    /**
     * Assert that the response code is one of the passed values.
     *
     * @param first The first posisble code
     * @param more More possible codes
     * @return this
     */
    default Assertions assertResponseCodeIn(int first, int... more) {
        return assertResponseCode(IntPredicates.anyOf(first, more));
    }

    /**
     * Assert a 200 response code.
     *
     * @return this
     */
    default Assertions assertOk() {
        return assertResponseCode(200);
    }

    /**
     * Assert a 404 response code.
     *
     * @return this
     */
    default Assertions assertNotFound() {
        return assertResponseCode(404);
    }

    /**
     * Assert a 204 response code.
     *
     * @return this
     */
    default Assertions assertNoContent() {
        return assertResponseCode(204);
    }

    /**
     * Assert a 401 response code.
     *
     * @return this
     */
    default Assertions assertUnauthorized() {
        return assertResponseCode(401);
    }

    /**
     * Assert a 403 response code.
     *
     * @return this
     */
    default Assertions assertForbidden() {
        return assertResponseCode(403);
    }

    /**
     * Assert a 409 response code.
     *
     * @return this
     */
    default Assertions assertConflict() {
        return assertResponseCode(409);
    }

    /**
     * Assert a 410 response code.
     *
     * @return this
     */
    default Assertions assertGone() {
        return assertResponseCode(410);
    }

    /**
     * Assert a 410 response code.
     *
     * @return this
     */
    default Assertions assertBadRequest() {
        return assertResponseCode(400);
    }

    /**
     * Assert that the response code matches the test encapsulated in the passed
     * predicate. Note that, to have meaningful test logs, it is important that
     * the predicate has a reasonably descriptive implementation of
     * <code>toString()</code> that describes what it does. The library
     * com.mastfrog:predicates provides tools for easily creating these or
     * wrapping predicates in descriptive ones.
     *
     * @param responseCodeTest A predicate that tests the response code (aka
     * status) returned by the HTTP request.
     * @return this
     */
    Assertions assertResponseCode(IntPredicate responseCodeTest);

    /**
     * Assert that there <i>is</i> a response body with a length > 0.
     *
     * @return this
     */
    default Assertions assertHasBody() {
        return assertBody(Predicates.namedPredicate("Has body bytes", body -> {
            return body != null && !body.isEmpty();
        }));
    }

    /**
     * Assert that the passed predicate returns true for the response body text.
     *
     * @param bodyTest A predicate. Note that it is helpful for meaningful test
     * logging if the predicate has a reasonable implementation of
     * <code>toString()</code> that describes what it does.
     * @return this
     */
    Assertions assertBody(Predicate<? super String> bodyTest);

    /**
     * Assert that the response body is a character for character exact match
     * for the passed string, ignoring leading or trailing whitespace.
     *
     * @param text The expected text
     * @return this
     */
    default Assertions assertBody(String text) {
        return assertBody(StringPredicates.predicate(text).trimmingInput());
    }

    /**
     * Assert that the response body is an exact match for one of the passed
     * strings.
     *
     * @param text The first string
     * @param more Additional possible matches
     * @return this
     */
    default Assertions assertBodyOneOf(String text, String... more) {
        return assertBody(StringPredicates.predicate(text, more));
    }

    /**
     * Assert that the response body matches the regular expression passed.
     *
     * @param pattern A pattern
     * @return this
     */
    default Assertions assertBodyMatches(Pattern pattern) {
        return assertBody(StringPredicates.pattern(pattern));
    }

    /**
     * Assert that the response body matches the regular expression expressed by
     * the passed string pattern, which must be able to be parsed by
     * <code>java.util.Pattern</code>.
     *
     * @param pattern A pattern
     * @return this
     */
    default Assertions assertBodyMatchesRegex(String pattern) {
        return assertBody(StringPredicates.pattern(pattern));
    }

    /**
     * Assert that the response body as a string contains with the passed
     * substring.
     *
     * @param substring A substring
     * @return this
     */
    default Assertions assertBodyContains(String substring) {
        return assertBody(StringPredicates.contains(substring));
    }

    /**
     * Assert that the response body as a string starts with the passed
     * substring.
     *
     * @param substring A substring
     * @return this
     */
    default Assertions assertBodyStartsWith(String substring) {
        return assertBody(StringPredicates.startsWith(substring));
    }

    /**
     * Assert that the response body as a string ends with the passed substring.
     *
     * @param substring A substring
     * @return this
     */
    default Assertions assertBodyEndsWith(String substring) {
        return assertBody(StringPredicates.startsWith(substring));
    }

    /**
     * Assert that the response body, parsed as JSON, has object equality with
     * the passed object; the assertion message generation code has field-level
     * differencing of objects, so this is to be preferred if you need to
     * directly compare two objects.
     *
     * @param <T> The type to use when deserializing
     * @param type The type to use when deserializing
     * @param object An object of the type
     * @return
     */
    <T> Assertions assertDeserializedBodyEquals(Class<T> type, T object);

    /**
     * Assert that the response body, parsed as JSON, has object equality with
     * the passed object.
     *
     * @param <T> The object type
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    default <T> Assertions assertDeserializedBodyEquals(T object) {
        Class<T> type = (Class<T>) object.getClass();
        return assertDeserializedBodyEquals(type, object);
    }

    /**
     * Assert that the response body, parsed as JSON, gets a result of true from
     * the passed predicate.
     *
     * @param <T> The object type
     * @param description A description of the test
     * @param type The type to use when deserializing JSON
     * @param test A predicate - note toString() will be called on it - it is
     * helpful if the predicate describes what it does there (see
     * com.mastfrog:predicates for an easy way to do this with
     * <code>Predicates.namedPredicate(String, Predicate)</code>.
     * @return this
     */
    <T> Assertions assertObject(String description, Class<T> type, Predicate<? super T> test);

    /**
     * Assert that the HTTP version is what is expected.
     *
     * @param versionTest A test of the HTTP version
     * @return this
     */
    Assertions assertVersion(Predicate<? super HttpClient.Version> versionTest);

    /**
     * Assert that the HTTP version exactly matches the passed version (note
     * that the JDK's HTTP client does not <i>have</i> enum constants for HTTP
     * 0.9 or HTTP 1.0. If the server responds with either of these, 0.9 will
     * fail, and 1.0 will be reported as 1.1.
     *
     * @param expected The expected HTTP version
     * @return this
     */
    Assertions assertVersion(HttpClient.Version expected);

    /**
     * Assert that the request will take longer than its overall timeout and
     * that the request times out.
     *
     * @return this
     */
    Assertions assertTimesOut();

    /**
     * Assert that the request does not take longer than its overall timeout and
     * that the request times out.
     *
     * @return this
     */
    Assertions assertDoesNotTimeOut();

    /**
     * Assert that the request fails in some known way (disconnect, timeout on
     * receiving response headers, etc.).
     *
     * @param expectedFailure The type of exception that should be thrown /
     * passed to the handler's <code>onError</code> method.
     */
    Assertions assertThrown(Class<? extends Throwable> expectedFailure);

    /**
     * Add an assertion which will be called for each HTTP chunked encoding
     * chunk as it arrives; the byte buffer passed to the predicate will be
     * ready to read (no rewinding required) when it is passed to the predicate.
     *
     * @param chunkTest A test of a chunk, which will be called multiple times
     * @return this
     */
    Assertions assertChunk(String description, Predicate<? super ByteBuffer> chunkTest);

    /**
     * By default, the severity of each assertion is FATAL. If you want some
     * other severity, pass that here and all assertions added within the
     * closure of the passed consumer will have that severity.
     *
     * @param severity A severity
     * @param c A consumer
     * @return this
     */
    Assertions withSeverity(FailureSeverity severity, Consumer<Assertions> c);
}
