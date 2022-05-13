package com.mastfrog.http.harness;

/**
 * The severity with which an assertion failure should be treated; the default
 * is FATAL. To use a different severity, use
 * <code>Assertions.withSeverity(Consumer&lt;Assertions&gt;)</code> to add
 * assertions with altered severity, for things which may fail, where that
 * should be reported but is not necessarily catastrophic (also useful to test
 * <i>that</i> things fail in an expected way).
 *
 * @author Tim Boudreau
 */
public enum FailureSeverity {
    /**
     * Warning severity - will not result in an assertion error (unless you want
     * it to).
     */
    WARNING,
    /**
     * Fatal severity - will result in an assertion error.
     */
    FATAL

}
