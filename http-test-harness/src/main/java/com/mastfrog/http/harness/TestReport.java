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
import com.mastfrog.http.harness.difference.Difference;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final List<Throwable> thrown = new ArrayList<>();

    public TestReport(String name) {
        this.name = name;
    }

    public TestReport() {
        this(TestReport.class.getSimpleName());
    }

    void add(TestResults results) {
        allResults.add(results);
    }

    public void onThrown(Throwable thrown) {
        this.thrown.add(thrown);
    }

    private Map<String, Object> toMap(Throwable thrown) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", thrown.getClass().getName());
        result.put("message", thrown.getMessage());
        List<String> stack = new ArrayList<>();
        for (StackTraceElement ste : thrown.getStackTrace()) {
            stack.add(ste.toString());
        }
        result.put("stack", stack);
        Throwable[] supp = thrown.getSuppressed();
        if (supp != null && supp.length > 0) {
            List<Map<String, Object>> suppressed = new ArrayList<>();
            result.put("suppressed", suppressed);
            for (Throwable t : supp) {
                suppressed.add(toMap(t));
            }
        }
        if (thrown.getCause() != null) {
            result.put("cause", toMap(thrown.getCause()));
        }
        return result;
    }

    static String HTML_CSS = "        <style type=\"text/css\">\n"
            + "            @import url('https://fonts.googleapis.com/css?family=Quicksand');\n"
            + "            body {\n"
            + "                font-family:\"Helvetica\", sans-serif;\n"
            + "                font-size: 1em;\n"
            + "                margin: 1em;\n"
            + "                padding: 0;\n"
            + "                height: 100%;\n"
            + "            }\n"
            + "            table{\n"
            + "                margin-left: 2em;\n"
            + "                margin-right: 2em;\n"
            + "                margin-bottom: 1em;\n"
            + "            }\n"
            + "            h1{\n"
            + "                font-size: 2em;\n"
            + "            }\n"
            + "            h2{\n"
            + "                font-size: 1.5em;\n"
            + "            }\n"
            + "            h3{\n"
            + "                font-size: 1.25em;\n"
            + "            }\n"
            + "            h4{\n"
            + "                font-size: 1.125em;\n"
            + "            }\n"
            + "            th{\n"
            + "                font-weight: bold;\n"
            + "                background: #cccccc;\n"
            + "            }\n"
            + "            td{\n"
            + "                vertical-align: top;\n"
            + "            }\n"
            + "            .lessWide {\n"
            + "                width: 75%;\n"
            + "            }\n"
            + "            .wide {\n"
            + "                width: 90%;\n"
            + "            }\n"
            + "            .fullwidth {\n"
            + "                width: 90%;\n"
            + "            }\n"
            + "            .fail {\n"
            + "                color: #CC5454;\n"
            + "            }\n"
            + "            .odd{\n"
            + "                background: #F8F8FF;\n"
            + "            }\n"
            + "            .even{\n"
            + "                background: #FFF8F8;\n"
            + "            }\n"
            + "            tr .diffs{\n"
            + "                background: #EEEEEE;\n"
            + "                text-align: left;\n"
            + "            }\n"
            + "            .diffs{\n"
            + "                margin-left: 3em;\n"
            + "                border-left-width: 1px;\n"
            + "                border-left-color: #FFFFFF;\n"
            + "                border-left-style: solid;\n"
            + "            }\n"
            + "            .testName {\n"
            + "                color: #000077;\n"
            + "                font-weight: bold;\n"
            + "                font-size: 1.125em;\n"
            + "                margin-left: 1em;\n"
            + "                border-top-width: 1px;\n"
            + "                border-top-color: #888888;\n"
            + "                border-top-style: solid;\n"
            + "                text-align: left;\n"
            + "            }\n"
            + "\n"
            + "</style>";

    private List<TestResults<?>> failed() {
        List<TestResults<?>> l = new ArrayList<>();
        for (TestResults<?> t : allResults) {
            if (t.hasFailures()) {
                l.add(t);
            }
        }
        Collections.sort(l, (a, b) -> {
            return a.testMethod().compareTo(b.testMethod());
        });
        return l;
    }

    private List<TestResults<?>> nonSuccess() {
        List<TestResults<?>> l = new ArrayList<>();
        for (TestResults<?> t : allResults) {
            for (AssertionResult a : t) {
                if (a.status().isNonSuccess()) {
                    l.add(t);
                    break;
                }
            }
        }
        Collections.sort(l, (a, b) -> {
            return a.testMethod().compareTo(b.testMethod());
        });
        return l;
    }

    public String toHtml() {
        Map<String, Object> map = toMap();
        HtmlBuilder hb = new HtmlBuilder();
        String timeString = when.format(DateTimeFormatter.RFC_1123_DATE_TIME);
        hb.headContent("<title>" + name + " " + when.toInstant() + "</title>");
        hb.headContent("<meta charset=\"UTF-8\">");
        hb.headContent(HTML_CSS);

        hb.h1(HtmlBuilder.maybeHumanize(name));
        hb.para(allResults.size() + " tests run on " + timeString + ".");
        hb.inTag("table", () -> {
            hb.tableRows(map,
                    "failedTests", "warnedTests", "succeededTests");
            hb.tableRow("unexpectedThrows", thrown.size());
        });
        hb.h2("Assertions Summary");
        hb.inTag("table", () -> {
            hb.tableRows(map, "failures", "nonSuccess", "warnings",
                    "assertions", "successes", "warnings");
        });

        List<TestResults<?>> trs = nonSuccess();
        if (!trs.isEmpty()) {
            hb.h1("Failed Tests");
            hb.inTag("table", "wide", () -> {
                hb.inTag("tr", () -> {
                    hb.th("Severity");
                    hb.th("Status");
                    hb.th("Message");
                    hb.th("Values");
                });
                int ix = 0;
                for (TestResults<?> tr : trs) {
                    String style = (ix++ % 2 == 0) ? "even" : "odd";
                    hb.inTag("tr", style, () -> {
                        hb.td(style, 4, tr.testMethod());
                    });
                    for (AssertionResult a : tr) {
                        if (a.status() == AssertionStatus.SUCCESS) {
                            continue;
                        }
                        hb.inTag("tr", style, () -> {
                            hb.td(a.severity(), style);
                            hb.td(a.status(), style);
                            hb.td(a.message(), style);
                            hb.td(a.actualValue(), style);
                        });
                        a.differences().ifPresent(diffs -> {
                            hb.td("style", 4, () -> {
                                hb.inTag("table", "lessWide diffs", () -> {
                                    String st = style + " diffs";
                                    hb.inTag("tr", st, () -> {
                                        hb.th("diffs", 4, "Differences");
                                    });
                                    hb.inTag("tr", style, () -> {
                                        hb.th("Property");
                                        hb.th("Change Kind");
                                        hb.th("Old Value");
                                        hb.th("New Value");
                                    });
                                    hb.inTag("tr", st, () -> {
                                        List<String> names = new ArrayList<>(diffs.keySet());
                                        // Sort by deepest property first, to put the
                                        // most likely culprits at the top
                                        Collections.sort(names, TestReport::compareByDots);
                                        names.forEach(name -> {
                                            Set<Difference<?>> diff = diffs.get(name);
                                            diff.forEach(dif -> {
                                                hb.inTag("tr", style, () -> {
                                                    hb.td(null, 1, () -> {
                                                        hb.inTag("b", () -> {
                                                            hb.append(name);
                                                        });
                                                    });
                                                    hb.td(dif.kind(), st);
                                                    hb.td(dif.oldValue(), st);
                                                    hb.td(dif.newValue(), st);
                                                });
                                                hb.td(style, 4, () -> {
                                                    hb.inTag("blockquote", () -> {
                                                        hb.inTag("i", () -> {
                                                            hb.append(dif);
                                                        });
                                                    });
                                                });
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    }
                }
            });
        }

        hb.h2("System");
        hb.inTag("table", () -> {
            hb.tableRows(map, "name", "when", "user", "os", "arch",
                    "totalMemory", "freeMemory", "processors", "host");
        });
        if (!thrown.isEmpty()) {
            hb.h1("Unexpected Thrown Exceptions");
            for (Throwable t : thrown) {
                hb.pre(Strings.toString(t));
            }
        }

        return hb.toString();
    }

    private static int compareByDots(String a, String b) {
        int adots = dotCount(a);
        int bdots = dotCount(b);
        int result = Integer.compare(bdots, adots);
        if (result == 0) {
            result = a.compareTo(b);
        }
        return result;
    }

    private static int dotCount(String what) {
        int ct = 0;
        for (int i = 0; i < what.length(); i++) {
            if ('.' == what.charAt(i)) {
                ct++;
            }
        }
        return ct;
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
        int succeededTests = 0;

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
            boolean anyFailures = false;
            for (AssertionResult a : allAssertionResults) {
                AssertionStatus stat = a.status();
                if (stat == AssertionStatus.SUCCESS) {
                    succeedingAssertions++;
                    localSuccesses++;
                } else {
                    nonSuccess++;
                    localNonSuccesses++;
                    anyFailures = true;
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
            if (!anyFailures) {
                succeededTests++;
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
        outer.put("succeededTests", succeededTests);
        outer.put("warnedTests", warnings);

        if (!thrown.isEmpty()) {
            List<Map<String, Object>> throwns = new ArrayList(thrown.size());
            for (Throwable t : thrown) {
                throwns.add(toMap(t));
            }
            outer.put("thrown", throwns);
        }

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

    private static class HtmlBuilder {
        // Quick and dirty html

        private final StringBuilder sb = new StringBuilder();
        private final StringBuilder head = new StringBuilder();
        private int depth;

        private static boolean isBicapitalized(String s) {
            boolean upperSeen = false;
            boolean lowerSeen = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c) || c == ' ' || c == '.') {
                    return false;
                }
                if (Character.isLowerCase(c)) {
                    lowerSeen = true;
                } else if (Character.isUpperCase(c)) {
                    upperSeen = true;
                }
                if (lowerSeen && upperSeen) {
                    return true;
                }
            }
            return false;
        }

        private static String humanize(String s) {
            if (isBicapitalized(s)) {
                return Strings.capitalize(Strings.camelCaseToDelimited(s, ' '));
            }
            return s;
        }

        static String maybeHumanize(Object o) {
            if (o instanceof String && isBicapitalized((String) o)) {
                return humanize(o.toString());
            }
            return Objects.toString(o);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("<!doctype html>\n<html>\n");
            if (!head.isEmpty()) {
                result.append("  <head>\n");
                result.append(head);
                result.append("  </head>\n");
            }
            result.append("  <body>\n");
            result.append(sb);
            result.append("\n  </body>\n");
            return result.append("\n</html>\n").toString();
        }

        private char[] indent() {
            char[] c = new char[(depth + 1) * 2];
            Arrays.fill(c, ' ');
            return c;
        }

        HtmlBuilder headContent(String text) {
            headNewLine();
            head.append(text);
            return this;
        }

        HtmlBuilder headNewLine() {
            if (head.length() == 0 || (head.length() > 0 && head.charAt(head.length() - 1) == '\n')) {
                return this;
            }
            head.append('\n').append(indent());
            return this;
        }

        HtmlBuilder onNewLine() {
            if (sb.length() == 0) {
                return this;
            }
            if (sb.charAt(sb.length() - 1) == '\n') {
                sb.append(indent());
            } else {
                sb.append('\n').append(indent());
            }
            return this;
        }

        HtmlBuilder append(Object text) {
            if (text instanceof Collection<?>) {
                Collection<?> items = (Collection<?>) text;
                for (Iterator<?> it = items.iterator(); it.hasNext();) {
                    internalAppend(it.next());
                    if (it.hasNext()) {
                        sb.append(", ");
                    }
                }
                return this;
            } else {
                return internalAppend(text);
            }
        }

        private HtmlBuilder internalAppend(Object text) {
            String s = Strings.escape(Objects.toString(maybeHumanize(text)), Escaper.BASIC_HTML);
            sb.append(s);
            return this;
        }

        HtmlBuilder tableRows(Map<String, Object> data, String... keys) {
            for (String k : keys) {
                tableRow(k, data.get(k));
            }
            return this;
        }

        HtmlBuilder doubleRow(Runnable r) {
            onNewLine();
            sb.append("<tr><td colspan='2'>\n");
            depth++;
            try {
                r.run();
            } finally {
                depth--;
                onNewLine();
                sb.append("</td></tr>\n");
            }
            return this;
        }

        HtmlBuilder tableRow(Object... parts) {
            if (parts.length > 0) {
                inTag("tr", () -> {
                    for (int i = 0; i < parts.length; i++) {
                        Object pt = parts[i];
                        String s = Strings.escape(Objects.toString(pt), Escaper.BASIC_HTML);
                        if (parts.length == 2 && i == 0) {
                            sb.append("<td><b>").append(maybeHumanize(s)).append("</b></td>");
                        } else {
                            simpleTag("td", pt);
                        }
                    }
                });
            }
            return this;
        }

        HtmlBuilder h1(String text) {
            return simpleTag("h1", text);
        }

        HtmlBuilder h2(String text) {
            return simpleTag("h2", text);
        }

        HtmlBuilder h3(String text) {
            return simpleTag("h3", text);
        }

        HtmlBuilder h4(String text) {
            return simpleTag("h4", text);
        }

        HtmlBuilder para(String text) {
            return inTag("p", () -> {
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        onNewLine();
                    }
                    append(lines[i]);
                }
            });
        }

        HtmlBuilder pre(String text) {
            onNewLine();
            sb.append("<pre>");
            sb.append(text);
            sb.append("</pre>");
            return onNewLine();
        }

        HtmlBuilder simpleTag(String tag, String cssClass, Object text) {
            return inTag(tag, cssClass, () -> {
                append(text);
            });
        }

        HtmlBuilder simpleTag(String tag, Object text) {
            return inTag(tag, () -> {
                append(text);
            });
        }

        HtmlBuilder selfClosingTag(String what) {
            sb.append('<').append(what).append("/>");
            return this;
        }

        HtmlBuilder td(Object body) {
            return td(body, 1);
        }

        HtmlBuilder td(Object body, String style) {
            return td(style, 1, body);
        }

        HtmlBuilder td(String style, int colspan, Runnable run) {
            onNewLine();
            sb.append("<td");
            if (style != null) {
                sb.append(" style='").append(style).append('\'');
            }
            if (colspan > 1) {
                sb.append(" colspan='").append(colspan).append('\'');
            }
            sb.append(">");
            depth++;
            try {
                onNewLine();
                run.run();
            } finally {
                depth--;
            }
            return this;
        }

        HtmlBuilder td(Object body, int colspan) {
            return td(null, colspan, body);
        }

        HtmlBuilder td(String style, int colspan, Object body) {
            return tdtr("td", body, style, colspan);
        }

        HtmlBuilder th(Object body) {
            return th(body, 1);
        }

        HtmlBuilder th(Object body, String style) {
            return th(style, 1, body);
        }

        HtmlBuilder th(Object body, int colspan) {
            return th(null, colspan, body);
        }

        HtmlBuilder th(String style, int colspan, Object body) {
            return tdtr("th", body, style, colspan);
        }

        private HtmlBuilder tdtr(String what, Object body, String style, int colspan) {
            onNewLine();
            sb.append("<").append(what);
            if (colspan > 1) {
                sb.append(" colspan='").append(colspan).append("'");
            }
            if (style != null) {
                sb.append(" style='").append(style).append("'");
            }
            sb.append('>');
            append(body);
            sb.append("</").append(what).append('>');
            return this;
        }

        HtmlBuilder inTag(String tag, Runnable run) {
            onNewLine();
            sb.append('<').append(tag).append('>');
            depth++;
            try {
                onNewLine();
                run.run();
                return this;
            } finally {
                depth--;
                onNewLine();
                sb.append("</").append(tag).append(">\n");
            }
        }

        HtmlBuilder tableHead(String text, int cols) {
            onNewLine();
            sb.append("<tr><th colspan=").append(cols).append(">");
            sb.append(Strings.escape(text, Escaper.BASIC_HTML));
            sb.append("</th></tr>\n");
            return this;
        }

        HtmlBuilder inTag(String tag, String cssClass, Runnable run) {
            onNewLine();
            sb.append('<').append(tag).append(" class='").append(cssClass).append("'>");
            depth++;
            try {
                onNewLine();
                run.run();
                return this;
            } finally {
                depth--;
                onNewLine();
                sb.append("</").append(tag).append(">\n");
            }
        }
    }
}
