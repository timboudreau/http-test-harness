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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.http.harness.difference.Difference;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The result of running one assertion.
 *
 * @author Tim Boudreau
 */
public final class AssertionResult {

    @JsonProperty("status")
    private final AssertionStatus status;
    @JsonProperty("severity")
    private final FailureSeverity severity;
    @JsonProperty("actualValue")
    private final Object actualValue;
    @JsonProperty("message")
    private final String message;
    @JsonProperty(value = "differences", required = false)
    private final Map<String, Set<Difference<?>>> differences;

    @JsonCreator
    public AssertionResult(
            @JsonProperty("status") AssertionStatus status,
            @JsonProperty("severity") FailureSeverity severity,
            @JsonProperty("message") String message,
            @JsonProperty("actualValue") Object actualValue,
            @JsonProperty(value = "differences", required = false) Map<String, Set<Difference<?>>> differences) {
        this.status = notNull("status", status);
        this.severity = notNull("severity", severity);
        this.message = notNull("message", message);
        this.actualValue = actualValue;
        this.differences = differences;
    }

    public Optional<Map<String, Set<Difference<?>>>> differences() {
        return Optional.ofNullable(differences);
    }

    public String message() {
        return message;
    }

    public AssertionStatus status() {
        return status;
    }

    public FailureSeverity severity() {
        return severity;
    }

    public Object actualValue() {
        return actualValue;
    }

    @JsonIgnore
    public boolean isOk() {
        return !status.isFailure();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        status.ifFailure(() -> sb.append(severity).append(": "));
        sb.append(status).append(' ').append(message);
        sb.append(" (value: '").append(
                Strings.escape(Objects.toString(actualValue),
                        Escaper.escapeUnencodableAndControlCharacters(US_ASCII)));
        String type = actualValue == null
                ? "null"
                : actualValue instanceof Throwable
                        ? Strings.toString((Throwable) actualValue)
                        : actualValue.getClass().getName();
        sb.append(actualValue instanceof Throwable
                ? "' with stack\n"
                : "' of type ")
                .append(type)
                .append(')');
        differences().ifPresent(diff -> {
            diff.forEach((item, diffs) -> {
                if (diffs.size() == 1) {
                    sb.append("\n   * ").append(item).append(' ').append(diffs.iterator().next());
                } else {
                    sb.append("\n   * ").append(item).append(" differences:");
                    for (Difference<?> d : diffs) {
                        sb.append("\n     * ").append(d);
                    }
                }
            });
        });
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.status);
        hash = 97 * hash + Objects.hashCode(this.severity);
        hash = 97 * hash + Objects.hashCode(this.actualValue);
        hash = 97 * hash + Objects.hashCode(this.message);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || obj.getClass() != AssertionResult.class) {
            return false;
        }
        final AssertionResult other = (AssertionResult) obj;

        return status == other.status && severity == other.severity
                && message.equals(other.message)
                && Objects.equals(actualValue, other.actualValue);
    }
}
