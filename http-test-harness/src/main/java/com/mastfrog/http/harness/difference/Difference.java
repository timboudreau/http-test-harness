/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.http.harness.difference;

/**
 *
 * @author Tim Boudreau
 */
public interface Difference<T> {

    T oldValue();

    T newValue();

    DifferenceKind kind();

    @SuppressWarnings("unchecked")
    public static <T> Difference<T> create(T a, T b) {
        if (a instanceof CharSequence && b instanceof CharSequence) {
            return (Difference<T>) new StringDifference((CharSequence) a, (CharSequence) b);
        }
        if (a instanceof Number && b instanceof Number) {
            return (Difference<T>) new NumericDifference((Number) a, (Number) b);
        }
        return new SimpleDifference<>(a, b, DifferenceKind.CHANGE);
    }
}
