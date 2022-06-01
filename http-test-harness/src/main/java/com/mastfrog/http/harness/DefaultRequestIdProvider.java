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
package com.mastfrog.http.harness;

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.RandomStrings;
import com.mastfrog.util.strings.Strings;
import java.net.http.HttpRequest;
import java.nio.charset.CharsetEncoder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of RequestIdProvider, using random strings and
 * incorporating the test name.
 *
 * @author Tim Boudreau
 */
final class DefaultRequestIdProvider implements RequestIdProvider {

    static final RandomStrings rstrings = new RandomStrings(ThreadLocalRandom.current());
    static final AtomicInteger seq = new AtomicInteger();
    static final CharsetEncoder ASCII_ENCODER = US_ASCII.newEncoder();
    static final Escaper ESCAPER = new Esc();
    final String base = rstrings.randomChars(3) + "-"
            + Long.toString(System.nanoTime(), 36) + "-";

    @Override
    public String newRequestId(HttpRequest req, String testName) {
        int sq = seq.incrementAndGet();
        return base + Integer.toString(sq, 36) + "-"
                + (testName == null ? "" : Strings.escape(testName, ESCAPER));
    }

    private static final class Esc implements Escaper {

        @Override
        public CharSequence escape(char c) {
            if (Character.isWhitespace(c) || !ASCII_ENCODER.canEncode(c)) {
                return "";
            }
            return Strings.singleChar(c);
        }
    }
}
