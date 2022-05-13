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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.Random;
import javax.inject.Inject;

/**
 * Emits a corrupted HTTP response on purpose.
 *
 * @author Tim Boudreau
 */
@HttpCall
@Methods(GET)
@Path("/evil")
@Description("Hijacks the connection and emits random bytes with a vague "
        + "resemblance to HTTP headers until the connection is broken.  Most "
        + "client interpret this as an HTTP 0.9 response and close the connection.")
final class Evil extends Acteur implements ChannelFutureListener {

    private final Random rnd = new Random(11243490272L);
    private final ByteBufAllocator alloc;

    @Inject
    Evil(HttpEvent evt, ByteBufAllocator alloc) {
        this.alloc = alloc;
        evt.channel().writeAndFlush(randomBytes()).addListener(this);
        defer();
    }

    private ByteBuf randomBytes() {
        byte[] result = new byte[32];
        rnd.nextBytes(result);
        result[result.length - 1] = '\n';
        result[result.length / 2] = '=';
        ByteBuf buf = alloc.ioBuffer(result.length);
        buf.writeBytes(result);
        return buf;
    }

    @Override
    public void operationComplete(ChannelFuture f) throws Exception {
        if (f.isCancelled() || f.cause() != null) {
            return;
        }
        f.channel().writeAndFlush(randomBytes()).addListener(this);
    }
}
