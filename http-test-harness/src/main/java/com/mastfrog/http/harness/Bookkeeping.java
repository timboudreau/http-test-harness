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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Keeps track of open requests and manages cancellation.
 *
 * @author Tim Boudreau
 */
final class Bookkeeping {

    private final AtomicInteger running = new AtomicInteger();
    private final List<TaskImpl> entries = new CopyOnWriteArrayList<>();

    TaskImpl register(String what, AtomicBoolean canceller, CompletableFuture<?> fut) {
        TaskImpl result = new TaskImpl(what, canceller, fut);
        running.incrementAndGet();
        entries.add(new TaskImpl(what, canceller, fut));
        fut.whenCompleteAsync((obj, thrown) -> {
            running.decrementAndGet();
            synchronized (this) {
                notifyAll();
            }
        });
        return result;
    }

    List<Task> tasks() {
        return new ArrayList<>(entries);
    }

    int running() {
        return running.get();
    }

    void eachTask(Consumer<Task> c) {
        entries.forEach(c);
    }

    public void awaitQuiet(Duration timeout, boolean killOnTimeout) {
        long expiresAt = System.currentTimeMillis() + timeout.toMillis();
        boolean unexpired;
        while ((unexpired = System.currentTimeMillis() < expiresAt) && running.get() > 0) {
            try {
                synchronized (this) {
                    wait(100);
                }
            } catch (InterruptedException ex) {
                unexpired = false;
                break;
            }
        }
        if (!unexpired && killOnTimeout) {
            cancelAll();
        }
    }

    public Collection<? extends Task> cancelAll() {
        List<TaskImpl> toRemove = new LinkedList<>();
        List<Task> cancelled = new ArrayList<>();
        while (!entries.isEmpty()) {
            toRemove.addAll(entries);
            entries.clear();
            for (TaskImpl e : toRemove) {
                if (e.cancel()) {
                    cancelled.add(e);
                }
            }
            toRemove.clear();
        }
        return cancelled;
    }

    static class TaskImpl implements Task {

        final String what;
        final AtomicBoolean canceller;
        final CompletableFuture<?> fut;
        private final long started = System.currentTimeMillis();
        private volatile long finishedAt;

        TaskImpl(String what, AtomicBoolean canceller, CompletableFuture<?> fut) {
            this.what = what;
            this.canceller = canceller;
            this.fut = fut;
        }

        public Void get() throws InterruptedException, ExecutionException {
            fut.get();
            return null;
        }

        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            fut.get(timeout, unit);
            return null;
        }

        public Void join() {
            fut.join();
            return null;
        }

        @Override
        public Duration duration() {
            long end = finishedAt;
            if (end == 0L) {
                end = System.currentTimeMillis();
            }
            return Duration.ofMillis(end - started);
        }

        @Override
        public boolean cancel() {
            boolean result = canceller.compareAndSet(false, true);
            if (result) {
                fut.cancel(true);
            }
            return result;
        }

        @Override
        public TaskState state() {
            boolean done = fut.isDone();
            boolean cancelled = fut.isCancelled();
            boolean exceptional = fut.isCompletedExceptionally();
            boolean cancelPending = canceller.get();
            if (cancelled) {
                return TaskState.CANCELLED;
            } else if (cancelPending) {
                return TaskState.CANCEL_PENDING;
            } else if (exceptional) {
                return TaskState.ERRORED;
            } else if (done) {
                return TaskState.DONE;
            }
            return TaskState.RUNNING;
        }

        @Override
        public String description() {
            return what;
        }
    }
}
