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
import com.mastfrog.acteur.preconditions.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path("/hang")
@Methods(GET)
@Description("Intentionally defers the responder chain and never responds.")
public class LeaveChannelOpenAndNeverRespond extends Acteur {

    private static volatile boolean CHANNEL_WAS_CLOSED;
    private static final CountDownLatch latch = new CountDownLatch(1);

    @Inject
    LeaveChannelOpenAndNeverRespond(HttpEvent evt) {
        evt.channel().closeFuture().addListener(f -> {
            CHANNEL_WAS_CLOSED = true;
            latch.countDown();
        });
        // This gives us a CompletableFuture that would continue the
        // response chain, which we intentionally never complete, in order
        // to test the logic that ensures timeouts work in the test harness
        defer();
    }

    public static synchronized boolean channelWasClosed() {
        if (!CHANNEL_WAS_CLOSED) {
            // On JDK 11, the HTTP client may not have closed the connection at
            // the time the test gets the response, so ensure that the test
            // that indeed it was closed is blocked until it has had a chance
            // to in the background
            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                // don't care
            }
        }
        for (int i = 0; i < 10 && !CHANNEL_WAS_CLOSED; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {

            }
        }
        return CHANNEL_WAS_CLOSED;
    }
}
