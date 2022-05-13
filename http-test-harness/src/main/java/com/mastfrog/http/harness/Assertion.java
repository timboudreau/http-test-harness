package com.mastfrog.http.harness;

import static com.mastfrog.http.harness.AssertionStatus.DID_NOT_RUN;
import java.util.function.Predicate;

/**
 * Internal assertion implementation - really a severity, a message and a test
 * of some kind, which may involve converting the input into another type.
 *
 * @author Tim Boudreau
 */
abstract class Assertion<T, R> {

    protected final String messageHead;
    private final FailureSeverity severity;
    private final Predicate<? super R> test;

    Assertion(String messageHead, FailureSeverity severity, Predicate<? super R> test) {
        this.messageHead = messageHead;
        this.severity = severity;
        this.test = test;
    }

    AssertionResult errorResult(Throwable thrown) {
        return new AssertionResult(AssertionStatus.INTERNAL_ERROR, severity, toString(), thrown);
    }

    AssertionResult didNotRunResult() {
        return new AssertionResult(DID_NOT_RUN, severity, toString(), null);
    }

    abstract R convert(T obj);

    AssertionResult test(T obj) {
        R value = convert(obj);
        boolean success = test.test(value);
        return new AssertionResult(AssertionStatus.of(success), severity, toString(), value);
    }

    @Override
    public String toString() {
        return (messageHead != null ? (messageHead + "") : "") + ' '
                + test.toString();
    }
}
