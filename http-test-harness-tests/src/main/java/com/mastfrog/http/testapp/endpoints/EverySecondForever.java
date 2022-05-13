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

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.util.time.TimeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path("/time")
@Methods(GET)
@ParametersMustBeNumbersIfPresent("delaySeconds")
@Description("Emits the current time every <code>delaySeconds</code> (an optional "
        + "URL parameter, default 1) forever, never closing the connection.  Like "
        + "the phone number you could once call, or shortwave station.")
public class EverySecondForever extends Acteur implements ChannelFutureListener {

    private final ZonedDateTime start;
    private final long delaySeconds;
    private final ScheduledExecutorService pool;

    @Inject
    EverySecondForever(HttpEvent evt, ScheduledExecutorService pool) {
        this.pool = pool;
        delaySeconds = evt.longUrlParameter("delaySeconds").or(1L);
        add(CONTENT_TYPE, PLAIN_TEXT_UTF_8);
        start = ZonedDateTime.now();
        setChunked(true);
        setResponseBodyWriter(this);
        ok();
    }

    @Override
    public void operationComplete(ChannelFuture f) {
        if (f.isCancelled() || f.cause() != null || pool.isShutdown() || !f.channel().isOpen()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        Duration elapsed = Duration.between(start, now);
        String message = "The time is now " + TimeUtil.toHttpHeaderFormat(now)
                + ".\n" + "This request has been open for "
                + TimeUtil.format(elapsed) + ".\n\n";
        byte[] msg = message.getBytes(UTF_8);

        ByteBuf buf = f.channel().alloc().buffer(msg.length);
        buf.writeBytes(msg);
        f.channel().writeAndFlush(new DefaultHttpContent(buf));
        f.addListener((ChannelFuture onFlush) -> {
            if (onFlush.isCancelled() || f.cause() != null || pool.isShutdown()) {
                return;
            }
            try {
                pool.schedule(() -> {
                    operationComplete(onFlush);
                }, delaySeconds, TimeUnit.SECONDS);
            } catch (RejectedExecutionException ee) {
                // okay, test is being shut down
            }
        });
    }
}
