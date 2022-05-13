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
