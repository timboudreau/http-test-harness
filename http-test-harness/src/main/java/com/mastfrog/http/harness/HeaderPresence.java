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

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Just a string predicate that tests for non-nullness, with a reasonable string
 * representation so an assertion says something meaningful about what went
 * wrong.
 *
 * @author Tim Boudreau
 */
final class HeaderPresence implements Predicate<String> {

    private final String header;
    private boolean expectation;

    HeaderPresence(String header) {
        this(header, true);
    }

    HeaderPresence(String header, boolean expectation) {
        this.header = header;
        this.expectation = expectation;
    }

    @Override
    public boolean test(String t) {
        return (t != null) == expectation;
    }

    @Override
    public String toString() {
        return "Header '" + header + "' is "
                + (expectation ? "present" : "absent");
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.header);
        return hash * (expectation ? 1 : -1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HeaderPresence other = (HeaderPresence) obj;
        return Objects.equals(this.header, other.header);
    }

}
