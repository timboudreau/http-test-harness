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

/**
 * The status of an assertion; determining whether an assertion failing should
 * be considered an error also involves looking at the FailureSeverity of it -
 * not all failures are equal.
 *
 * @author Tim Boudreau
 */
public enum AssertionStatus {
    /**
     * The assertion succeded.
     */
    SUCCESS,
    /**
     * The assertion failed.
     */
    FAILURE,
    /**
     * An unexpected exception was thrown when attempting to run the assertion.
     */
    INTERNAL_ERROR,
    /**
     * The assertion was never run (typically due to timeout).
     */
    DID_NOT_RUN;

    static AssertionStatus of(boolean successOrFailure) {
        return successOrFailure ? SUCCESS : FAILURE;
    }

    public boolean isNonSuccess() {
        return this != SUCCESS;
    }

    public void ifFailure(Runnable run) {
        if (isFailure()) {
            run.run();
        }
    }

    public boolean isFailure() {
        switch (this) {
            case FAILURE:
            case INTERNAL_ERROR:
                return true;
            default:
                return false;
        }
    }
}
