package com.mastfrog.http.harness;

import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Wraps the underlying URI-based test harness and lets tests pass some type
 * other than URI which a conversion function can apply protocol, port,
 * whatever, to to create requests.
 *
 * @author Tim Boudreau
 */
final class URIConvertingTestHarness<U, O> implements HttpTestHarness<U> {

    private final Function<U, O> converter;
    private final HttpTestHarness<O> delegate;

    URIConvertingTestHarness(Function<U, O> converter, HttpTestHarness<O> delegate) {
        this.converter = converter;
        this.delegate = delegate;
    }

    @Override
    public TestRequest get(U uri) {
        return delegate.get(converter.apply(uri));
    }

    @Override
    public TestRequest delete(U uri) {
        return delegate.delete(converter.apply(uri));
    }

    @Override
    public TestRequest put(U uri, byte[] bytes) {
        return delegate.put(converter.apply(uri), bytes);
    }

    @Override
    public TestRequest put(U uri, String string) {
        return delegate.put(converter.apply(uri), string);
    }

    @Override
    public TestRequest put(U uri, String string, Charset charset) {
        return delegate.put(converter.apply(uri), string, charset);
    }

    @Override
    public TestRequest put(U uri, HttpRequest.BodyPublisher pub) {
        return delegate.put(converter.apply(uri), pub);
    }

    @Override
    public TestRequest post(U uri, byte[] bytes) {
        return delegate.post(converter.apply(uri), bytes);
    }

    @Override
    public TestRequest post(U uri, String string) {
        return delegate.post(converter.apply(uri), string);
    }

    @Override
    public TestRequest post(U uri, String string, Charset charset) {
        return delegate.post(converter.apply(uri), string, charset);
    }

    @Override
    public TestRequest post(U uri, HttpRequest.BodyPublisher pub) {
        return delegate.post(converter.apply(uri), pub);
    }

    @Override
    public HttpTestHarness<U> shutdown() {
        delegate.shutdown();
        return this;
    }

    @Override
    public boolean await(Duration dur) throws InterruptedException {
        return delegate.await(dur);
    }

    @Override
    public HttpTestHarness<U> await() throws InterruptedException {
        delegate.await();
        return this;
    }

    @Override
    public TestRequest request() {
        return delegate.request();
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
    public <T> TestRequest putObject(U uri, T toSerialize) {
        return delegate.putObject(converter.apply(uri), toSerialize);
    }

    @Override
    public <T> TestRequest postObject(U uri, T toSerialize) {
        return delegate.postObject(converter.apply(uri), toSerialize);
    }
}
