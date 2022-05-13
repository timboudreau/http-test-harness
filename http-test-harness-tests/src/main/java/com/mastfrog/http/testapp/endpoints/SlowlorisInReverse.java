package com.mastfrog.http.testapp.endpoints;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path("/sirolwolS")
@Methods(GET)
@Description("The famous slowloris attack in reverse - on the client.  Hijacks the "
        + "connection, and emits a valid HTTP response that is never-ending, emitting "
        + "another random header name/value pair every so often for eternity.")
final class SlowlorisInReverse extends Acteur implements ChannelFutureListener {

    private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private final Random rnd = new Random(12345);
    private final Channel channel;
    private final ScheduledExecutorService svc;

    @Inject
    @SuppressWarnings("LeakingThisInConstructor")
    SlowlorisInReverse(HttpEvent evt, ScheduledExecutorService svc) {
        channel = evt.channel();
        defer();
        emit("HTTP/1.1 201 Created\r\n").addListener(this);
        this.svc = svc;
    }

    private ChannelFuture emit(String what) {
        return emit(what, channel);
    }

    private ChannelFuture emit(String what, Channel channel) {
        if (!channel.isWritable()) {
            return channel.newFailedFuture(new Exception());
        }
        byte[] stuff = what.getBytes(US_ASCII);
        ByteBuf buf = channel.alloc().ioBuffer(stuff.length);
        buf.writeBytes(stuff);
        return channel.writeAndFlush(buf);
    }

    private ChannelFuture emitRandomHeader(Channel channel) {
        StringBuilder sb = new StringBuilder("x-");
        for (int i = 0; i < 7; i++) {
            sb.append(ALPHA[rnd.nextInt(ALPHA.length)]);
        }
        sb.append(" = ");
        for (int i = 0; i < 7; i++) {
            sb.append(ALPHA[rnd.nextInt(ALPHA.length)]);
        }
        sb.append("\r\n");
        return emit(sb.toString());
    }

    @Override
    public void operationComplete(ChannelFuture f) throws Exception {
        svc.schedule(() -> {
            if (f.isCancelled() || f.cause() != null || !f.channel().isOpen()) {
                return;
            }
            emitRandomHeader(f.channel()).addListener(this);
        }, 10, TimeUnit.MILLISECONDS);

    }
}
