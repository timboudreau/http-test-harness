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

import com.google.inject.name.Named;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.http.harness.HarnessLogLevel;
import static com.mastfrog.http.harness.HarnessLogLevel.DEBUG;
import com.mastfrog.http.harness.HttpTestHarness;
import static com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule.GUICE_BINDING_STARTUP_LATCH;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHooks;
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.net.PortFinder;
import static com.mastfrog.util.preconditions.Exceptions.chuck;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class ServerImplTestHarness implements ErrorInterceptor, Provider<HttpTestHarness<String>>, Function<String, URI> {

    private static final PortFinder PORTS = new PortFinder();
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();
    private final Provider<Server> server;
    private int port = -1;
    private HttpTestHarness<URI> delegate;
    private final ShutdownHooks hooks;
    private final Settings settings;
    private final Version httpVersion;
    private final CountDownLatch startupLatch;
    private final Provider<Codec> codec;

    @Inject
    ServerImplTestHarness(Provider<Server> server, ShutdownHooks hooks, Settings settings, Version httpVersion,
            @Named(GUICE_BINDING_STARTUP_LATCH) CountDownLatch startupLatch, Provider<Codec> codec) {
        this.server = server;
        this.hooks = hooks;
        this.settings = settings;
        this.httpVersion = httpVersion;
        this.startupLatch = startupLatch;
        this.codec = codec;
    }

    @Override
    public void onError(Throwable err) {
//        err.printStackTrace();
        errors.add(err);
    }

    public void rethrow() {
        if (errors.isEmpty()) {
            return;
        }
        AssertionError err = new AssertionError("Server exception thrown");
        for (Throwable t : errors) {
            err.addSuppressed(t);
        }
        throw err;
    }

    private synchronized int port() {
        if (port == -1) {
            port = settings.getInt("testPort", PORTS::findAvailableServerPort);
            port = PORTS.findAvailableServerPort();
        }
        return port;
    }

    private boolean ssl() {
        return settings.getBoolean("testSsl", false);
    }

    private boolean httpClientSecure() {
        return settings.getBoolean("testSslSecure", false);
    }

    private HttpClient.Redirect followRedirects() {
        String val = settings.getString("testFollowRedirects");
        if (val == null) {
            return HttpClient.Redirect.NORMAL;
        }
        switch (val) {
            case "never":
            case "NEVER":
            case "false":
                return HttpClient.Redirect.NEVER;
            case "true":
            case "ALWAYS":
            case "always":
                return HttpClient.Redirect.ALWAYS;
            case "normal":
            case "NORMAL":
            case "default":
                return HttpClient.Redirect.NORMAL;
            default:
                throw new AssertionError("Unknown value for setting 'testFollowRedirects': " + val
                        + " - must be one of never/false/always/true/normal/default");
        }
    }

    private HttpClient.Builder setupSSL(HttpClient.Builder client) {
        boolean isSsl = ssl();
        boolean secure = httpClientSecure();
        if (isSsl && !secure) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            try {
                client = client.sslContext(insecureSslContext());
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                return chuck(ex);
            }
        } else if (isSsl && secure) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "false");
        }
        return client;
    }

    synchronized HttpTestHarness<URI> delegate() {
        if (delegate != null) {
            return delegate;
        }
        int port_local = port();
        try {
            Server serv = server.get();
            boolean ssl = ssl();
            ServerControl ctrl = serv.start(port_local, ssl);
            assert ctrl != null;

            HttpClient.Builder client = setupSSL(HttpClient.newBuilder()
                    .followRedirects(followRedirects())
                    .version(httpVersion));

            delegate = HttpTestHarness.builder()
                    .withDefaultResponseTimeout(Duration.ofMinutes(1))
                    .withHttpVersion(httpVersion)
                    .awaitingReadinessOn(startupLatch)
                    .withMinimumLogLevel(logLevel())
                    .withCodec(codec.get())
                    .withClient(client.build())
                    .build();
            hooks.add(delegate::shutdown);
            return delegate;
        } catch (IOException ex) {
            return chuck(ex);
        }
    }

    HarnessLogLevel logLevel() {
        String val = settings.getString("harnessLogLevel");
        if (val == null) {
            if (settings.getBoolean("http.harness.debug", false)) {
                return HarnessLogLevel.DEBUG;
            } else if (settings.getBoolean("http.harness.detail", false)) {
                return HarnessLogLevel.DETAIL;
            } else {
                return HarnessLogLevel.IMPORTANT;
            }
        }
        for (HarnessLogLevel h : HarnessLogLevel.values()) {
            String s = h.name();
            if (val.equalsIgnoreCase(s)) {
                return h;
            }
        }
        System.err.println("Unknown log level for harnessLogLevel: "
                + val + ". Using " + HarnessLogLevel.IMPORTANT);
        return HarnessLogLevel.IMPORTANT;
    }

    @Override
    public HttpTestHarness<String> get() {
        return delegate().convertingToUrisWith(this);
    }

    @Override
    public URI apply(String t) {
        int port_local = port();
        String proto = ssl() ? "https" : "http";
        String uriString = proto + "://localhost:" + port_local;
        if (logLevel() == DEBUG) {
            System.out.println("----- " + uriString + " -----");
        }
        if (t != null && t.length() > 0) {
            if (t.charAt(0) != '/') {
                uriString += '/';
            }
            uriString += t;
        }
        return URI.create(uriString);
    }

    static SSLContext insecureSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{NoTrustTrustManager.INSTANCE};
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc;
    }

    private static final class NoTrustTrustManager implements X509TrustManager {

        static final NoTrustTrustManager INSTANCE = new NoTrustTrustManager();

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(
                X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(
                X509Certificate[] certs, String authType) {
        }
    }
}
