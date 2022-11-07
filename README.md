HTTP Test Harness
=================

Makes it trivial to write tests of HTTP servers in Java which can be run from any testing
framework, using the JDK's own HTTP client.

This project is a replacement for the older, very similar HTTP test harness using Netty, underneath
[this project](https://github.com/timboudreau/netty-http-client) - since the JDK has a nicely done
HTTP client, using that eliminates a lot of opportunities, and presents an opportunity to improve the
testing API.

### Features / Benefits

  * Clean, intuitive, builder-based API
  * Fully asynchronous test execution - many tests can be run concurrently, or they can be
    throttled using a `Semaphore` you pass to your `TestHarnessBuilder`, and can either be waited
    for singly or as a group.
  * Simple JSON report generation
  * Configurable serialization (uses Jackson by default)
  * Not tied to any particular testing framework
  * Easy testing of headers, responses, response codes, http versions and response bodies (deserialized or raw)
  * Flexible definition of what you pass as URL-like arguments (so you can start a server on a random port and
    just path URL paths/queries to the harness, and it can fill in the rest)
  * Block requests until a locally started server indicates it is ready for requests
  * Separately test that response-start and response-body does/doesn't exceed a time limit

Requires Java 11 or greater; uses the Java 9 module system with the module name
`com.mastfrog.http.harness`.

### Example

Calls to set up an HTTP request you want to make mirror the API of the JDK's request
builder.  When you have defined the aspects of the request you want to (URL, headers, HTTP
version, timeouts), you call `test(Consumer<Asseritions>)` which will block the calling
thread until the request has completed 

```java
harness.get("metrics").test(asserts -> {
    asserts.assertOk()
            .assertHasBody()
            .assertDoesNotTimeOut()
            .assertHasHeader("content-type")
            .assertHasHeader("date")
            .assertHeaderEquals("transfer-encoding", "chunked")
            .assertHeader("content-type", "text/plain;charset=UTF-8")
            .assertBodyContains("jvm_memory_bytes_committed");
}).printResults() // prints what each assertion did
        .assertAllSucceeded(); // throws an AssertionError with details of what failed if anything did
```

You could also call `applyingAssertions()` instead of `test()` with the
same argument, which gets you a results object but does _not_ block, launch a bunch
of requests, then launch some more, and collect all the results and wait on them
in aggregate.  That approach is more useful in a standalone testing application;
the former (and the example) is more useful in a unit test run by something like
JUnit, where you want the test method not to exit until the response is complete
and all assertions have been run.

Setting Up The Harness
----------------------

You get an instance of `TestHarnessBuilder` from `HttpTestHarness.builder()` and configure
it as needed.  That lets you set up:

  * The HTTP client to be used (if unset, you get the default JDK HTTP client - or you
    can provide your own, configuring things like timeouts or how redirects are handled)
  * Provide an implementation of `Codec` (just an interface that looks just like Jackson's `ObjectMapper`)
    for serializing / deserializing data
  * Set up headers that should be included in _all_ HTTP requests made by the harness instance (e.g. auth)
  * The maximum concurrent requests to make (or you can provide your own `Semaphore` as a
    throttle)
  * A strategy for how test "method" names are looked up (for logging/reporting) - the default
    inspects the call stack
  * Provide a `TestReport` instance results should be added to, which you can write to a JSON
    file or do something else with at the end of a series of test runs
  * Provide a `CountDownLatch` that will be notified when the server you're testing is ready
    for requests (useful to avoid spurious failures if you're starting a server you're going
    to call)
  * Set a default _response timeout_ - this is the maximum time a request may take.  The JDK's
    HttpClient has a timeout for the _initial_ bytes of a response to arrive, so a request
    that connects but is greeted with silence will time out, but it has _none_ for how long
    the response body should take to arrive, if any bytes _are_ sent.  This solves that problem.
    It can be overridden on a per-request basis if needed.
  * Set a watchdog timer interval - this is how often requests-in-flight get checked to see if they
    have exceeded their _response timeout_.

#### Example

```java
throttle = new Semaphore(3); // optionally, limit to 3 requests at once
report = new TestReport(); // a thing we can save to JSON that will itemize the testing odne
// Start the server on a random available port
app = new TestApplication().start();
harness = HttpTestHarness.builder()
        .withHttpVersion(HTTP_1_1)
        .withTestReport(report)
        .awaitingReadinessOn(app.startupLatch()) // let app tell us when it is ready
        .throttlingRequestsWith(throttle)
        .withInitialResponseTimeout(Duration.ofSeconds(30)).build() // kill requests > 30 seconds
        // Let TestApplication convert string paths into URIs, since
        // it knows the port it was started on and whether it's using
        // https or not (the HTTP client would need to be set up with
        // a permissive security policy for the self-signed cert to work).
        .convertingToUrisWith(pathAndQuery -> URI.create("http://localhost:" + app.port() + "/" + pathAndQuery));
```

License
=======

Licensed under [the MIT license](https://opensource.org/licenses/MIT).
