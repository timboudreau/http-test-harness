package com.mastfrog.http.testapp;

import com.google.inject.Binder;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.server.ServerLifecycleHook;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import static com.mastfrog.giulius.SettingsBindings.BOOLEAN;
import static com.mastfrog.giulius.SettingsBindings.INT;
import static com.mastfrog.giulius.SettingsBindings.LONG;
import static com.mastfrog.giulius.SettingsBindings.STRING;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.net.PortFinder;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.inject.Inject;

/**
 * Runs a small REST API that does various nice and not-so-nice things to test
 * the behavior of the test harness, including pathologies like hanging a
 * connection permanently and never-ending responses. Can optionally use HTTPS
 * with a self-signed cert.
 * <p>
 * The server uses Acteur - so each HTTP path the server responds on is an
 * instance of `Acteur` in this package annotated with
 * <code>&064;HttpCall</code>.
 * </p>
 * <p>
 * This application can also be started using the main method of this class, and
 * all of the kinds of requests it supports examined in detail by going to
 * <code><a href="http://localhost:4949/help?html=true">http://localhost:4949/help?html=true</a></code>.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class TestApplication implements Function<String, URI> {

    private int port;
    private final boolean https;
    private volatile ServerControl ctrl;
    private final AtomicBoolean started = new AtomicBoolean();
    private final CountDownLatch startLatch = new CountDownLatch(1);

    public TestApplication() {
        this(false);
    }

    public TestApplication(boolean https) {
        this.https = https;
    }

    /**
     * A CountDownLatch of one, which is counted down once our
     * ServerLifecycleHook gets a callback that the server is started and the
     * port is open.
     *
     * @return A CountDownLatch
     */
    public CountDownLatch startupLatch() {
        return startLatch;
    }

    /**
     * Run the server for manual testing
     *
     * @param ignored ignored
     * @throws IOException if something goes wrong
     * @throws InterruptedException if something goes wrong
     */
    public static void main(String... ignored) throws IOException, InterruptedException {
        System.setProperty("acteur.debug", "true");
        TestApplication app = new TestApplication(false);
        app.start(4949);
        System.out.println("Running TestApplication on port " + app.port);
        System.out.println("Example URL: " + app.uriFor("hello"));
        app.await();
    }

    /**
     * Start the server, passing the port to use. If the port number is less
     * than or equal to zero, then a random unused server port will be chosen so
     * it is possible for parallel tests to run multiple instances of this
     * application concurrently without interfering with each other.
     *
     * @param port
     * @throws IOException
     */
    public TestApplication start(int port) throws IOException {
        if (started.compareAndSet(false, true)) {
            _start(port);
        }
        return this;
    }

    private TestApplication _start(int port) throws IOException {
        PortFinder ports = new PortFinder();
        this.port = port <= 0 ? ports.findAvailableServerPort() : port;
        Server server = new ServerBuilder()
                // So you can go to /help?html=true and get a description of the
                // web api
                .enableHelp()
                // Not used, turn off cors headers
                .disableCORS()
                // speeds up startup slightly - fiewer guice bindings * system property count
                .enableOnlyBindingsFor(INT, STRING, LONG, BOOLEAN)
                .add((Binder binder) -> {
                    // Used by a few tests that write periodically to the response
                    binder.bind(ScheduledExecutorService.class)
                            .toInstance(Executors.newScheduledThreadPool(1));
                    // Block HTTP requests from the harness until the Hook gets a callback
                    // indicating the server is up and running and is listening on the port.
                    //
                    // In practice, this is rarely an issue, but theoretically we can start
                    // banging on a server before the port is open and get spurious failures.
                    //
                    // So this also tests the await logic.
                    binder.bind(CountDownLatch.class).toInstance(startLatch);
                    // The callback that will be invoked on startup
                    binder.bind(Hook.class).asEagerSingleton();
                    // If we are not in debug mode, suppress default console
                    // logging of requests
                    if (!Boolean.getBoolean("acteur.debug")) {
                        // Use a silent request logger to reduce noise in test output
                        binder.bind(RequestLogger.class).toInstance(new RL());
                    }
                })
                .add(Settings.builder()
                        // Set up the binding of "port" to our port
                        .add(ServerModule.PORT, this.port)
                        // Sets the app name in the help page and in
                        // the Server: http header
                        .add("application.name", "HarnessTestApplication")
                        .build())
                .build();
        ctrl = server.start(https);
        return this;
    }

    /**
     * Start the server on a random unused port.
     *
     * @throws IOException if something goes wrong
     */
    public TestApplication start() throws IOException {
        return start(0);
    }

    @Override
    public URI apply(String t) {
        return uriFor(t);
    }

    public boolean isHttps() {
        return https;
    }

    public int port() {
        return port;
    }

    /**
     * Create a URL based on the server's address, port and protocol.
     *
     * @param pathAndQuery The path and query portion of the desired URL
     * @return A URI
     */
    public URI uriFor(String pathAndQuery) {
        return URI.create("http" + (https ? "s" : "") + "://localhost:" + port + "/" + pathAndQuery);
    }

    /**
     * Wait for the server to fully shut down, closing any pending requests.
     *
     * @throws InterruptedException if interrupted
     */
    public void await() throws InterruptedException {
        ctrl.await();
    }

    // Use a silent request logger to reduce noise in test output
    static class RL implements RequestLogger {

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            // do nothing
        }

        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            // do nothing
        }
    }

    /**
     * Shut down the running server.
     *
     * @throws InterruptedException if interrupted
     */
    public void shutdown() throws InterruptedException {
        if (started.compareAndSet(true, false)) {
            ServerControl c = ctrl;
            if (c != null) {
                ctrl = null;
                c.shutdown(true, 10, TimeUnit.SECONDS);
            }
        }
    }

    private static class Hook extends ServerLifecycleHook {

        private final CountDownLatch latch;

        @Inject
        Hook(CountDownLatch latch, Registry reg) {
            super(reg);
            this.latch = latch;
        }

        @Override
        protected void onStartup(Application application, Channel channel) {
            latch.countDown();
        }
    }
}
