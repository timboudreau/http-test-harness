package com.mastfrog.http.harness;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.Objects;

/**
 * The result of running one assertion.
 *
 * @author Tim Boudreau
 */
public final record AssertionResult(
        AssertionStatus status,
        FailureSeverity severity,
        String message, Object actualValue) {

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
        return sb.toString();
    }
}
