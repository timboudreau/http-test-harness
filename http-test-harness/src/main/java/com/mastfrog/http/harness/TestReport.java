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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A test report, which can be passed to the {@link TestHarnessBuilder} in order
 * to collect results and generate a JSON report which can be rendered somehow.
 *
 * @author Tim Boudreau
 */
public final class TestReport {

    private final List<TestResults> allResults = new CopyOnWriteArrayList<>();
    private final String name;
    private final ZonedDateTime when = ZonedDateTime.now();

    public TestReport(String name) {
        this.name = name;
    }

    public TestReport() {
        this(TestReport.class.getSimpleName());
    }

    void add(TestResults results) {
        allResults.add(results);
    }

    /**
     * Convert the contents of this object to a JSON-renderable map, computing
     * stats.
     *
     * @return A map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
        // We want these sorted for consistency
        Map<String, Object> outer = new TreeMap<>();
        outer.put("name", name);
        outer.put("when", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(when));
        outer.put("user", System.getProperty("user.name"));
        outer.put("os", System.getProperty("os.name"));
        outer.put("arch", System.getProperty("os.arch"));
        outer.put("totalMemory", Runtime.getRuntime().totalMemory());
        outer.put("freeMemory", Runtime.getRuntime().freeMemory());
        outer.put("processors", Runtime.getRuntime().availableProcessors());
        String host = System.getenv("HOST");
        if (host != null) {
            outer.put("host", host);
        }
        Map<String, Object> result = new TreeMap<>();
        outer.put("results", result);

        int testCount = 0;
        int assertionCount = 0;
        int succeedingAssertions = 0;
        int failed = 0;
        int nonSuccess = 0;
        int warned = 0;

        Map<AssertionStatus, Integer> countByType = new EnumMap<>(AssertionStatus.class);
        outer.put("resultCountByStatus", countByType);
        for (AssertionStatus status : AssertionStatus.values()) {
            countByType.put(status, 0);
        }

        Set<String> failedTests = new TreeSet<>();
        Set<String> warnings = new TreeSet<>();
        for (TestResults<?> results : allResults) {
            testCount++;

            Map<String, Object> mm = (Map<String, Object>) result.computeIfAbsent(results.testMethod(), k -> new LinkedHashMap<>());
            List<Map<String, Object>> runs = (List<Map<String, Object>>) mm.computeIfAbsent("runs", k -> new ArrayList<>());
            int runId = runs.size() + 1;
            Map<String, Object> thisRun = new TreeMap<>();
            runs.add(thisRun);
            thisRun.put("iteration", runId);
            thisRun.put("duration", results.runDuration().toString());

            List<AssertionResult> allAssertionResults = results.allResults();
            thisRun.put("assertionResults", allAssertionResults);

            assertionCount += results.allResults().size();
            thisRun.put("totalAssertions", allAssertionResults.size());
            int localSuccesses = 0;
            int localNonSuccesses = 0;
            int localWarned = 0;
            int localFailed = 0;
            for (AssertionResult a : allAssertionResults) {
                AssertionStatus stat = a.status();
                if (stat == AssertionStatus.SUCCESS) {
                    succeedingAssertions++;
                    localSuccesses++;
                } else {
                    nonSuccess++;
                    localNonSuccesses++;
                }
                countByType.compute(stat, (AssertionStatus s, Integer old) -> old + 1);
                if (stat == AssertionStatus.FAILURE && a.severity() == FailureSeverity.FATAL) {
                    failed++;
                    localFailed++;
                    failedTests.add(results.testMethod());
                } else if (stat == AssertionStatus.FAILURE) {
                    warned++;
                    localWarned++;
                    warnings.add(results.testMethod());
                }
            }
            thisRun.put("succeeded", localSuccesses);
            thisRun.put("failed", localFailed);
            thisRun.put("warned", localWarned);
            thisRun.put("nonSuccess", localNonSuccesses);
        }
        outer.put("assertions", assertionCount);
        outer.put("successes", succeedingAssertions);
        outer.put("warnings", warned);
        outer.put("failures", failed);
        outer.put("nonSuccess", nonSuccess);
        outer.put("failedTests", failedTests);
        outer.put("warnedTests", warnings);

        return outer;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(toMap());
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
            // should not happen
            return allResults.toString();
        }
    }

    /**
     * Save this report as JSON to an output stream.
     *
     * @param out An output stream
     * @throws IOException if something goes wrong
     */
    public void save(OutputStream out) throws IOException {
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(out, toMap());
    }
}
