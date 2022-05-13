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

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Object which can be waited on, which can provide the set of failed tests.
 *
 * @author Tim Boudreau
 */
public interface TestResults<T> extends Iterable<AssertionResult> {

    /**
     * Get the name of the test or invoking method that launched the HTTP
     * request associated with this TestResults. The strategy for detecting the
     * method can be set in a {@link TestHarnessBuilder}; the default simply
     * examines the stack for the first method that is not clearly part of the
     * call sequence in this library to launch a request.
     * <p>
     * Returns the string <i>-unknown-</i> if the strategy used returns null (it
     * should not).
     * </p>
     *
     * @return A test method
     */
    String testMethod();

    /**
     * Get the response object, blocking until it is available, possibly
     * rethrowing a checked exception.
     *
     * @return The object
     */
    T get();

    /**
     * Desertialize the response body as some type, using the codec the harness
     * was configured with.
     *
     * @param <R> The type
     * @param deserializeAs The type as a class object
     * @return An instance of the type, or null if the body is null
     * @throws IOException if deserialization fails somehow
     */
    <R> R get(Class<R> deserializeAs) throws IOException;

    /**
     * Get the total time from the start of the request to the response being
     * finished, or if unfinished, the elapsed time since the start of the
     * request up to now.
     *
     * @return A duration
     */
    Duration runDuration();

    /**
     * Throws an assertion error if any of the assertions applied to the request
     * have failed.
     *
     * @return this
     */
    default TestResults<T> assertNoFailures() {
        return assertNoMatches((status, severity) -> {
            return severity == FailureSeverity.FATAL && status.isFailure();
        });
    }

    /**
     * Throws an assertion error if any of the assertions applied to the request
     * do not have the status of SUCCESS.
     *
     * @return this
     */
    default TestResults<T> assertAllSucceeded() {
        return assertNoMatches((status, severity) -> {
            return severity == FailureSeverity.FATAL && status.isNonSuccess();
        });
    }

    /**
     * Throws an assertion error if the passed predicate returns true for any of
     * the assertions in this results object.
     *
     * @param bip A predicate
     * @return this
     */
    TestResults<T> assertNoMatches(BiPredicate<AssertionStatus, FailureSeverity> bip);

    /**
     * Wait for the test this results object corresponds with to complete.
     *
     * @return this
     * @throws InterruptedException if interrupted
     */
    TestResults<T> await() throws InterruptedException;

    /**
     * Wait for the test this results object corresponds with to complete or the
     * timeout to elapse
     *
     * @return false if timed out
     * @throws InterruptedException if interrupted
     */
    boolean await(Duration dur) throws InterruptedException;

    /**
     * Cancel the associated request and its assertions - they will have a
     * status of {@link AssertionStatus#DID_NOT_RUN}.
     *
     * @return true if not already cancelled
     */
    boolean cancel();

    /**
     * Returns true if there are failures.
     *
     * @return
     */
    boolean hasFailures();

    /**
     * Returns true if the test is still runnning and the assertion results
     * obtainable from this object are incomplete.
     *
     * @return true if stil lrunning
     */
    boolean isNotYetCompleted();

    /**
     * Get the granular state of the task to perform the HTTP request associated
     * with this results object.
     *
     * @return A state
     */
    TaskState state();

    BiConsumer<HarnessLogLevel, Supplier<String>> logger();

    /**
     * Convenience method to print results to stdout or similar.
     *
     * @param st A print stream
     * @return this
     */
    default TestResults<T> printResults(PrintStream st) {
        for (AssertionResult r : this) {
            st.println(" * " + r);
        }
        return this;
    }

    /**
     * Convenience method to print results to the logger configured for the test
     * harness.
     *
     * @return this
     */
    default TestResults<T> printResults() {
        for (AssertionResult r : this) {
            logger().accept(HarnessLogLevel.IMPORTANT, () -> " * " + r);
        }
        return this;
    }

    /**
     * Get all results of assertions.
     *
     * @return The complete results
     */
    default List<AssertionResult> allResults() {
        List<AssertionResult> result = new ArrayList<>();
        for (AssertionResult t : this) {
            result.add(t);
        }
        return result;
    }
}
