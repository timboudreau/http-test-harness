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
 * The severity with which an assertion failure should be treated; the default
 * is FATAL. To use a different severity, use
 * <code>Assertions.withSeverity(Consumer&lt;Assertions&gt;)</code> to add
 * assertions with altered severity, for things which may fail, where that
 * should be reported but is not necessarily catastrophic (also useful to test
 * <i>that</i> things fail in an expected way).
 *
 * @author Tim Boudreau
 */
public enum FailureSeverity {
    /**
     * Warning severity - will not result in an assertion error (unless you want
     * it to).
     */
    WARNING,
    /**
     * Fatal severity - will result in an assertion error.
     */
    FATAL

}
