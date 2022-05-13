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
