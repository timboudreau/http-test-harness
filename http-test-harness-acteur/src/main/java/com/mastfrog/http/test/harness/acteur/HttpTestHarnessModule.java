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

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.settings.Settings;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
public class HttpTestHarnessModule extends AbstractModule {

    static final String GUICE_BINDING_STARTUP_LATCH = "_sl_";

    @Override
    protected void configure() {
        CountDownLatch startupLatch = new CountDownLatch(1);
        bind(CountDownLatch.class).annotatedWith(Names.named(GUICE_BINDING_STARTUP_LATCH)).toInstance(startupLatch);
        bind(ErrorInterceptor.class).toProvider(ErrorInterceptorProvider.class);
        bind(Version.class).toProvider(HttpVersionProvider.class);
        bind(new HttpTestHarnessURILiteral()).toProvider(URIHarnessProvider.class).in(Scopes.SINGLETON);
        bind(new HttpTestHarnessStringLiteral()).toProvider(ServerImplTestHarness.class).in(Scopes.SINGLETON);
    }

    private static final class HttpTestHarnessURILiteral extends TypeLiteral<HttpTestHarness<URI>> {
    }

    private static final class HttpTestHarnessStringLiteral extends TypeLiteral<HttpTestHarness<String>> {
    }

    @Singleton
    private static class URIHarnessProvider implements Provider<HttpTestHarness<URI>> {

        private final Provider<ServerImplTestHarness> harness;

        @Inject
        URIHarnessProvider(Provider<ServerImplTestHarness> harness) {
            this.harness = harness;
        }

        @Override
        public HttpTestHarness<URI> get() {
            return harness.get().delegate();
        }
    }

    private static final class ErrorInterceptorProvider implements Provider<ErrorInterceptor> {

        private final ServerImplTestHarness harn;
        private final CountDownLatch latch;
        private final ExecutorService exe;
        private final AtomicBoolean notified = new AtomicBoolean();

        @Inject
        ErrorInterceptorProvider(ServerImplTestHarness harn, @Named(GUICE_BINDING_STARTUP_LATCH) CountDownLatch latch,
                @Named("eventThreads") ExecutorService exe) {
            this.exe = exe;
            this.harn = harn;
            this.latch = latch;
        }

        @Override
        public ErrorInterceptor get() {
            // We can't depend directly on acteur and use ServerLifecycleHook, or
            // acteur wouldn't be able to depend on this library.  So, triggering
            // off of the first time an error interceptor is requested and then
            // suffling it off to the event thread pool so it runs after startup
            // is completed is a substitute.  ErrorInterceptor will be requested in
            // when Application is injected, and Application is guaranteed to exist
            // by the time the socket is opened.
            if (notified.compareAndSet(false, true)) {
                exe.submit(latch::countDown);
            }
            return harn;
        }

    }

    private static final class HttpVersionProvider implements Provider<Version> {

        private final Settings settings;

        @Inject
        HttpVersionProvider(Settings settings) {
            this.settings = settings;
        }

        @Override
        public Version get() {
            String sv = settings.getString("httpVersion");
            if (sv == null) {
                return Version.HTTP_1_1;
            }
            switch (sv) {
                case "1":
                case "1_1":
                case "1.1":
                case "HTTP_1_1":
                case "http_1_1":
                case "HTTP/1.1":
                case "http/1.1":
                case "HTTP-1.1":
                case "http-1.1":
                    return Version.HTTP_1_1;
                case "2":
                case "http-2":
                case "HTTP-2":
                case "http_2":
                case "HTTP_2":
                    return Version.HTTP_2;
                default:
                    throw new AssertionError("Unknown httpVersion string '" + sv);
            }
        }

    }

}
