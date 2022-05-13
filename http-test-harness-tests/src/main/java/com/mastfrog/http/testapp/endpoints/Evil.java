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
