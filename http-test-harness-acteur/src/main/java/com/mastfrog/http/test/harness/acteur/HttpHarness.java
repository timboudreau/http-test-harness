/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.http.test.harness.acteur;

import com.google.inject.ImplementedBy;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.http.harness.HttpTestHarness;
import static com.mastfrog.util.preconditions.Exceptions.chuck;

/**
 * Reifies HttpTestHarness on String - the test harness sets up the server, so
 * only the path and query portion of the signature should be provided by a test
 * - this just eliminates the generic type on the harness argument, for tests
 * that will have their arguments injected.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultHttpHarness.class)
public interface HttpHarness extends HttpTestHarness<String> {

    /**
     * If any errors were encountered on the server side, rethrow them to fail
     * the test even if all requests were answered successfully.
     */
    default void rethrowServerErrors() {
        // do nothing
    }

    /**
     * Runs the passed test code, and will rethrow server side errors if no
     * client side errors were encountered.
     *
     * @param run A runnable
     */
    default void rethrowing(ThrowingRunnable run) {
        assert run != null;
        Throwable thrown = null;
        try {
            run.run();
        } catch (Exception | Error err) {
            thrown = err;
        } finally {
            if (thrown != null) {
                chuck(thrown);
            }
            rethrowServerErrors();
        }
    }
}
