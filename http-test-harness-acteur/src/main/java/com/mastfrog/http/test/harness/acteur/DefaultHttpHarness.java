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

import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.http.harness.Task;
import com.mastfrog.http.harness.TestHarnessBuilder;
import com.mastfrog.http.harness.TestRequest;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultHttpHarness implements HttpHarness {

    private final HttpTestHarness<String> delegate;
    private final Provider<ServerImplTestHarness> rethrower;

    @Inject
    DefaultHttpHarness(HttpTestHarness<String> delegate, Provider<ServerImplTestHarness> rethrower) {
        this.delegate = delegate;
        this.rethrower = rethrower;
    }

    public void rethrowServerErrors() {
        rethrower.get().rethrow();
    }

    @Override
    public TestRequest get(String uri) {
        return delegate.get(uri);
    }

    @Override
    public TestRequest delete(String uri) {
        return delegate.delete(uri);
    }

    @Override
    public TestRequest put(String uri, byte[] bytes) {
        return delegate.put(uri, bytes);
    }

    @Override
    public TestRequest put(String uri, String string) {
        return delegate.put(uri, string);
    }

    @Override
    public TestRequest put(String uri, String string, Charset charset) {
        return delegate.put(uri, string, charset);
    }

    @Override
    public TestRequest put(String uri, HttpRequest.BodyPublisher pub) {
        return delegate.put(uri, pub);
    }

    @Override
    public <T> TestRequest putObject(String uri, T toSerialize) {
        return delegate.putObject(uri, toSerialize);
    }

    @Override
    public TestRequest post(String uri, byte[] bytes) {
        return delegate.post(uri, bytes);
    }

    @Override
    public TestRequest post(String uri, String string) {
        return delegate.post(uri, string);
    }

    @Override
    public TestRequest post(String uri, String string, Charset charset) {
        return delegate.post(uri, string, charset);
    }

    @Override
    public TestRequest post(String uri, HttpRequest.BodyPublisher pub) {
        return delegate.post(uri, pub);
    }

    @Override
    public <T> TestRequest postObject(String uri, T toSerialize) {
        return delegate.postObject(uri, toSerialize);
    }

    @Override
    public HttpHarness shutdown() {
        delegate.shutdown();
        return this;
    }

    @Override
    public boolean await(Duration dur) throws InterruptedException {
        return delegate.await(dur);
    }

    @Override
    public HttpTestHarness<String> await() throws InterruptedException {
        return delegate.await();
    }

    @Override
    public TestRequest request() {
        return delegate.request();
    }

    @Override
    public <T> HttpTestHarness<T> convertingToUrisWith(Function<T, String> converter) {
        return delegate.convertingToUrisWith(converter);
    }

    @Override
    public List<? extends Task> tasks() {
        return delegate.tasks();
    }

    @Override
    public int currentlyRunningTasks() {
        return delegate.currentlyRunningTasks();
    }

    @Override
    public void awaitQuiet(Duration dur, boolean killOnTimeout) {
        delegate.awaitQuiet(dur, killOnTimeout);
    }
}
