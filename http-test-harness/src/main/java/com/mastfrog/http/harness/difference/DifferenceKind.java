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
public enum DifferenceKind {
    CHANGE, INSERTION, DELETION, NONE, UNKNOWN;

    public String format(Object old, Object nue) {
        switch (this) {
            case CHANGE:
                return "change(" + old + " -> " + nue + ")";
            case INSERTION:
                return "insert(" + nue + ")";
            case DELETION:
                return "delete(" + old + ")";
            default:
                return name().toLowerCase();
        }
    }

    public <T> Difference<T> newDifference(T single) {
        switch (this) {
            case INSERTION:
                return new SimpleDifference<>(null, single, this);
            case DELETION:
                return new SimpleDifference<>(single, null, this);
            default:
                throw new AssertionError(this + " does not support single arguments");
        }
    }

    public <T> Difference<T> newDifference(T a, T b) {
        switch (this) {
            case CHANGE:
                return new SimpleDifference<>(a, b, this);
            default:
                throw new AssertionError(this + " does not support double arguments");
        }
    }

    public <T> Difference<T> newDifference() {
        switch (this) {
            case NONE:
            case UNKNOWN:
                return new SimpleDifference<>(null, null, this);
            default:
                throw new AssertionError(this + " cannot be no-argument");
        }
    }
}
