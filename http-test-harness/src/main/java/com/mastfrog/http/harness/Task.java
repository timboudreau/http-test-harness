package com.mastfrog.http.harness;

import java.time.Duration;

/**
 * A task which is running or was run - typically one HTTP request. Tasks can be
 * obtained from the harness instance - mainly useful for logging runtime state.
 *
 * @author Tim Boudreau
 */
public interface Task {

    /**
     * A human readable description of the task.
     *
     * @return A string
     */
    String description();

    /**
     * Cancel the task.
     *
     * @return true if the task was not already cancelled or completed somehwo
     */
    boolean cancel();

    /**
     * The duration of time the task has been running or did run.
     *
     * @return A duration
     */
    Duration duration();

    /**
     * The state of the task at the point this method was called.
     *
     * @return THe task's state
     */
    TaskState state();

    /**
     * Determine if the task is still running.
     *
     * @return true if it is running
     */
    default boolean isRunning() {
        return state().isRunning();
    }
}
