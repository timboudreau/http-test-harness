package com.mastfrog.http.testapp;

import com.mastfrog.http.harness.FailureSeverity;
import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.http.harness.TestReport;
import com.mastfrog.http.harness.TestResults;
import static com.google.common.net.MediaType.JSON_UTF_8;
import com.mastfrog.http.harness.HarnessLogLevel;
import com.mastfrog.predicates.Predicates;
import com.mastfrog.predicates.string.StringPredicates;
import com.mastfrog.http.testapp.endpoints.LeaveChannelOpenAndNeverRespond;
import com.mastfrog.http.testapp.endpoints.SomeObject;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.http.HttpClient.Version;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 *
 * @author Tim Boudreau
 */
@Execution(CONCURRENT)
public class TestHarnessTest {

    private static TestApplication app;
    private static TestReport report;
    private static HttpTestHarness<String> harness;
    private static final Semaphore THROTTLE = new Semaphore(30);
    private static List<String> messages = new ArrayList<>();

    @BeforeAll
    public static void setUpClass() throws IOException {
        // To enable verbose server-side logging:
        // System.setProperty("acteur.debug", "true");
        report = new TestReport();
        // Start the server on a random available port
        app = new TestApplication().start();
        harness = HttpTestHarness.builder()
                .withHttpVersion(HTTP_1_1)
                .withTestReport(report)
                .awaitingReadinessOn(app.startupLatch())
                .throttlingRequestsWith(THROTTLE)
                .replaceLogger((lev, msg) -> {
                    if (lev.isGreaterThanOrEqualTo(HarnessLogLevel.IMPORTANT)) {
                        messages.add(msg.get());
                    }
                    // do nothing
//                    if (lev == HarnessLogLevel.IMPORTANT) {
//                        System.err.println(msg.get());
//                    }
                })
                //                .logToStderr()
                .withInitialResponseTimeout(Duration.ofSeconds(30)).build()
                // Let TestApplication convert string paths into URIs, since
                // it knows the port it was started on and whether it's using
                // https or not (the HTTP client would need to be set up with
                // a permissive security policy for the self-signed cert to work).
                .convertingToUrisWith(app);

    }

    @AfterAll
    public static void tearDownClass() throws InterruptedException {
        try {
            HttpTestHarness<?> harn = harness;
            if (harn != null) { // if something failed during setup
                harness = null;
                harn.await(Duration.ofSeconds(30)); // Normally will be instant
                harn.shutdown();
            }

        } finally {
            try {
                if (app != null) { // don't fail here if setUpClass failed
                    app.shutdown();
                }
            } finally {
                // Print the test report to the console to give an idea of
                // the output here:
//                System.out.println("\nTEST REPORT:");
//                System.out.println(report);
//                for (String m : messages) {
//                    System.out.println(m);
//                }
            }
        }
    }

    @BeforeEach
    public void setUp() {
        // We use one static report that gets all test runs added to it
        assertNotNull(report, "Report not set - did setUpClass() run?");
    }

    @Test
    public void testHelloWorld() {
        // Test that a simple hello-world call works.
        assertNotNull(harness, "Harness not set up correctly");
        assertNotNull(app, "App not set up");
        TestResults<HttpResponse<String>> res = harness.get("hello")
                .test(asserts -> {
                    asserts.assertHasHeader("content-type")
                            .assertHasBody()
                            .assertHasHeader("wookies")
                            .assertNoHeader("Ouvre-Tubers")
                            .assertHeaderEquals("wookies", "food")
                            .assertResponseCodeIn(200, 201)
                            .assertBody(StringPredicates.predicate("Hello world!"));
                }).printResults();
    }

    @Test
    public void testJSON() {
        // Test that json assertions work
        assertNotNull(harness, "Harness not set up correctly");
        assertNotNull(app, "App not set up");
        harness.get("json?text=skiddoo&val=23")
                .test(asserts -> {
                    asserts.assertHasHeader("content-type")
                            .assertHeader("content-type",
                                    StringPredicates.predicate(JSON_UTF_8.toString()))
                            .assertHasBody()
                            .assertResponseCode(200)
                            .assertObject("Check json value", SomeObject.class, sob -> {
                                return new SomeObject(23, "skiddoo").equals(sob);
                            });
                }).printResults();
    }

    @Test
    public void testThrowInAssertsReleasesPermit() {
        try {
            harness.get("/blah/nothing")
                    .test(asserts -> {
                        throw new Error("blah");
                    });
            fail("Should not get here");
        } catch (Error e) {
            // ok
        }
    }

    @Test
    public void testResponseThatNeverEndsTimesOut() {
        // Makes a request to a URL that will print the time every n seconds
        // for eternity, and ensure it doesn't stay open beyond what it should.
        harness.get(
                "time?delaySeconds=2"
        ).responseStartTimeout(Duration.ofSeconds(2))
                .responseFinishedTimeout(Duration.ofSeconds(4))
                .test(asserts -> {
                    asserts.assertHasHeader("content-type")
                            .assertOk()
                            .withSeverity(FailureSeverity.WARNING, asserts2 -> {
                                asserts2.assertBody(Predicates.namedPredicate("Should not be run!", str -> {
                                    throw new Error("The body is never completed, and "
                                            + "this should not be called with partial "
                                            + "output if the request times out, but got\n"
                                            + str);
                                }));
                                // This test will fail, but should not cause an AssertionError
                                // because these are warnings
                                asserts2.assertResponseCode(13);
                            })
                            .assertTimesOut();
                }).printResults();
    }

    @Test
    public void testRequestHungWithNoHeadersSentTimesOut() throws Exception {
        // This test makes an HTTP request that will simply be left open forever,
        // to verify that our timeout logic works correctly
        harness.get("hang")
                .responseStartTimeout(Duration.ofMillis(500))
                .test(asserts -> {
                    asserts.assertTimesOut();
                }).printResults();

        // Check that the connection really was closed by the timeout
        assertTrue(LeaveChannelOpenAndNeverRespond.channelWasClosed(),
                "Channel should have been closed and was not");
    }

    @Test
    public void testHttpOneDotOhRequest() {
        // This makes a call that returns a real HTTP 1.0 response, even replying
        // with HTTP/1.0 as the version, with no chunked encoding, no content-length
        // header, just closing the connection when the response is finished.
        //
        // We want to make sure the test harness works for ANYTHING it might encounter,
        // including this.
        String s = harness.get("oldschool")
                .responseFinishedTimeout(Duration.ofSeconds(60))
                .test(asserts -> {
                    asserts.assertOk()
                            .assertHasHeader("content-type")
                            // Interestingly, there is no enum constant for 1.0, which
                            // the server WILL return here.  The JDK's HTTP client lies.
                            .assertVersion(Version.HTTP_1_1)
                            .assertBodyMatchesRegex("4\\. ABCDEFGHIJKLMNOPQRSTUVWXYZ")
                            .assertBodyContains("2. ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                })
                .printResults()
                .get()
                .body();
        assertTrue(s.contains("1. ABCDEFGHIJKLMNOPQRSTUVWXYZ"), s);
        assertTrue(s.contains("5. ABCDEFGHIJKLMNOPQRSTUVWXYZ"), s);
    }

    @Test
    public void testJsonInput() {
        SomeObject obj = new SomeObject(23, "skiddoo");
        harness.postObject("jsonInput", obj)
                .test(asserts -> {
                    asserts.assertResponseCodeGreaterThan(199)
                            .assertResponseCodeLessThan(400)
                            .assertVersion(HTTP_1_1)
                            .assertDeserializedBodyEquals(new SomeObject(24, "skiddoo-xx"));
                }).printResults();
    }

    @Test
    public void testOutboundHeaders() {
        // Test that we are sending headers correctly
        harness.get("echoHeaders")
                .header("stuff", "This-is-some-stuff-here!")
                .test(asserts -> {
                    asserts.assertBody("This-is-some-stuff-here!")
                            .assertOk();
                }).printResults();
    }

    @Test
    public void testServerRespondingWithHttpLikeGarbage() {
        if (false) {
            // This test passes, but Surefire gets cranky about the fact that
            // the JDK's HTTP client tries to log to the real System.err when the
            // server intentionally sends it random bytes instead of a valid
            // response, not the System.err that Surefire replaced the 
            // original with, so we lose most of our test output.  Need to 
            // investigate how to fix this.
            //
            // Really, Surefire should be better at it 
            // (like this: https://bit.ly/3Ftdqy0 ).
            return;
        }
        // This makes a request that outputs random bytes to the response, with
        // a trailing newline and an = in the middle, so it vaguely resembles
        // headers.  Most clients interpret this as an HTTP 0.9 response and
        // reject it.
        harness.get("evil")
                .responseFinishedTimeout(Duration.ofSeconds(7))
                .test(asserts -> {
                    asserts.assertThrown(ProtocolException.class);
                })
                .printResults();
    }

    @Test
    public void testEndlessUselessHeadersAndNeverABody() {
        // Slowloris spelled backwards :-)
        //
        // This is the slowloris DOS attack in reverse - the server sends back
        // a valid initial response line, followed by a slow, never-ending
        // drizzle of valid-but-nonsense http headers like "x-mwvpvws = czyygfq".
        //
        // I wrote a another server that did this on purpose, redirecting requests
        // from Nginx to it for any inbound request with "php" in the URL (all
        // hosts on the internet are be constantly probed for PHP exploits).
        //
        // It succeeded in tying up a ton of connections for more than eight 
        // hours :-)
        //
        // Here we just want to be sure that our test harness will _not_ endlessly
        // await a response body that will never come.
        harness.get("sirolwolS")
                .responseFinishedTimeout(Duration.ofSeconds(4))
                .test(asserts -> {
                    asserts.assertTimesOut();
                }).printResults();
    }

    @Test
    public void testDelayCallWithBadParameter() {
        // Just ensure that bad requests are bad.
        harness.get("delayed?delay=wurgle")
                .test(asserts -> {
                    asserts.assertBodyContains("number")
                            .assertBadRequest();
                });
    }
}
