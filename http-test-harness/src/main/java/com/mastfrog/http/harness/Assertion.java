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

import static com.mastfrog.http.harness.AssertionStatus.DID_NOT_RUN;
import com.mastfrog.http.harness.difference.Difference;
import com.mastfrog.http.harness.difference.Differencing;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Internal assertion implementation - really a severity, a message and a test
 * of some kind, which may involve converting the input into another type.
 *
 * @author Tim Boudreau
 */
abstract class Assertion<T, R> {

    protected final String messageHead;
    private final FailureSeverity severity;
    protected final Predicate<? super R> test;

    Assertion(String messageHead, FailureSeverity severity, Predicate<? super R> test) {
        this.messageHead = messageHead;
        this.severity = severity;
        this.test = test;
    }

    AssertionResult errorResult(Throwable thrown) {
        return new AssertionResult(AssertionStatus.INTERNAL_ERROR,
                severity, toString(), thrown, null);
    }

    AssertionResult didNotRunResult() {
        return new AssertionResult(DID_NOT_RUN, severity, toString(),
                null, null);
    }

    abstract R convert(T obj);

    AssertionResult test(T obj) {
        R value = convert(obj);
        boolean success = test.test(value);
        Map<String, Set<Difference<?>>> diffs = null;
        if (test instanceof Differencing) {
            diffs = ((Differencing) test).differences();
        }
        return new AssertionResult(AssertionStatus.of(success),
                severity, toString(), value, diffs);
    }

    @Override
    public String toString() {
        return (messageHead != null ? (messageHead + "") : "") + ' '
                + test.toString();
    }
}
