package com.mastfrog.http.harness;

/**
 * The status of an assertion; determining whether an assertion failing should
 * be considered an error also involves looking at the FailureSeverity of it -
 * not all failures are equal.
 *
 * @author Tim Boudreau
 */
public enum AssertionStatus {
    /**
     * The assertion succeded.
     */
    SUCCESS,
    /**
     * The assertion failed.
     */
    FAILURE,
    /**
     * An unexpected exception was thrown when attempting to run the assertion.
     */
    INTERNAL_ERROR,
    /**
     * The assertion was never run (typically due to timeout).
     */
    DID_NOT_RUN;

    static AssertionStatus of(boolean successOrFailure) {
        return successOrFailure ? SUCCESS : FAILURE;
    }

    public boolean isNonSuccess() {
        return this != SUCCESS;
    }

    public void ifFailure(Runnable run) {
        if (isFailure()) {
            run.run();
        }
    }

    public boolean isFailure() {
        switch (this) {
            case FAILURE:
            case INTERNAL_ERROR:
                return true;
            default:
                return false;
        }
    }
}
