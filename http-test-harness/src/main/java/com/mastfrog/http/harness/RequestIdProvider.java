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
 *
 * @author Tim Boudreau
 */
public interface RequestIdProvider {

    static final RequestIdProvider DEFAULT = new DefaultRequestIdProvider();
    static final String DEFAULT_HEADER_NAME = "x-harn-req-id";
    
    String newRequestId(HttpRequest req, String testName);

    default String headerName() {
        return DEFAULT_HEADER_NAME;
    }

    default RequestIdProvider withHeaderName(String headerName) {
        return new RequestIdProvider() {
            @Override
            public String newRequestId(HttpRequest req, String testName) {
                return RequestIdProvider.this.newRequestId(req, testName);
            }

            @Override
            public String headerName() {
                return headerName;
            }
        };
    }
}
