package com.mastfrog.http.harness;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Just a string predicate that tests for non-nullness, with a reasonable string
 * representation so an assertion says something meaningful about what went
 * wrong.
 *
 * @author Tim Boudreau
 */
final class HeaderPresence implements Predicate<String> {

    private final String header;
    private boolean expectation;

    HeaderPresence(String header) {
        this(header, true);
    }

    HeaderPresence(String header, boolean expectation) {
        this.header = header;
        this.expectation = expectation;
    }

    @Override
    public boolean test(String t) {
        return (t != null) == expectation;
    }

    @Override
    public String toString() {
        return "Header '" + header + "' is "
                + (expectation ? "present" : "absent");
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.header);
        return hash * (expectation ? 1 : -1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HeaderPresence other = (HeaderPresence) obj;
        return Objects.equals(this.header, other.header);
    }

}
