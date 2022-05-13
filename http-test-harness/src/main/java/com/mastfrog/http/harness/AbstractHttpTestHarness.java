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

import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Common boilerplate for HttpTestHarness.
 *
 * @author Tim Boudreau
 */
abstract class AbstractHttpTestHarness implements HttpTestHarness<URI> {

    final Codec codec;

    public AbstractHttpTestHarness(Codec codec) {
        this.codec = codec;
    }

    @Override
    public TestRequest get(URI uri) {
        return request().GET().uri(uri);
    }

    @Override
    public TestRequest delete(URI uri) {
        return request().DELETE().uri(uri);
    }

    @Override
    public TestRequest put(URI uri, byte[] bytes) {
        return put(uri, HttpRequest.BodyPublishers.ofByteArray(bytes));
    }

    @Override
    public TestRequest put(URI uri, String string) {
        return put(uri, HttpRequest.BodyPublishers.ofString(string, StandardCharsets.UTF_8));
    }

    @Override
    public TestRequest put(URI uri, String string, Charset charset) {
        return put(uri, HttpRequest.BodyPublishers.ofString(string, charset));
    }

    @Override
    public TestRequest put(URI uri, HttpRequest.BodyPublisher pub) {
        return request().PUT(pub).uri(uri);
    }

    @Override
    public TestRequest post(URI uri, byte[] bytes) {
        return post(uri, HttpRequest.BodyPublishers.ofByteArray(bytes));
    }

    @Override
    public TestRequest post(URI uri, String string) {
        return post(uri, HttpRequest.BodyPublishers.ofString(string, StandardCharsets.UTF_8));
    }

    @Override
    public TestRequest post(URI uri, String string, Charset charset) {
        return post(uri, HttpRequest.BodyPublishers.ofString(string, charset));
    }

    @Override
    public TestRequest post(URI uri, HttpRequest.BodyPublisher pub) {
        return request().POST(pub).uri(uri);
    }

    @Override
    public <T> TestRequest putObject(URI uri, T toSerialize) {
        try {
            return put(uri, codec.writeValueAsBytes(toSerialize));
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    @Override
    public <T> TestRequest postObject(URI uri, T toSerialize) {
        try {
            return post(uri, codec.writeValueAsBytes(toSerialize));
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
