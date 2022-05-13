package com.mastfrog.http.harness;

/**
 * The state of a task that was created by submitting an http request to run.
 *
 * @author Tim Boudreau
 */
public enum TaskState {
    /**
     * The task is running - this is the initial state.
     */
    RUNNING,
    /**
     * The task was cancelled.
     */
    CANCELLED,
    /**
     * The task was cancelled but has not noticed yet.
     */
    CANCEL_PENDING,
    /**
     * The task was completed.
     */
    DONE,
    /**
     * The task completed exceptionally.
     */
    ERRORED;

    public boolean isRunning() {
        return this == RUNNING || this == CANCEL_PENDING;
    }

}
