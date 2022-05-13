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
package com.mastfrog.http.testapp.endpoints;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.util.function.EnhCompletableFuture;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path({"delayed", "/delay/*"})
@Methods(GET)
@ParametersMustBeNumbersIfPresent("delay")
@Description("Returns a response 'Hello $NAME' where $NAME is the URL parameter <code>name</code>, "
        + "or 'Newman' if none is provided.")
public class DelayedResponse extends Acteur implements Runnable {

    private final EnhCompletableFuture<String> fut;
    private String name = "Newman";

    @Inject
    DelayedResponse(HttpEvent event, ScheduledExecutorService pool) {
        if (event.path().size() > 1) {
            name = event.path().lastElement().toString();
        }
        long delay = event.longUrlParameter("delay").or(500L);
        fut = deferThenRespond(HttpResponseStatus.OK);
        pool.schedule(this, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        fut.complete("Hello, " + name + ".\n");
    }
}
