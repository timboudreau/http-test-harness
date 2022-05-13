package com.mastfrog.http.testapp.endpoints;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static java.nio.charset.StandardCharsets.UTF_8;
import javax.inject.Inject;

/**
 * Gives a non-chunked, HTTP 1.0 style response where bytes just arrive until
 * the connection is closed.
 *
 * @author Tim Boudreau
 */
@HttpCall
@Methods(GET)
@Path("/oldschool")
@Description("Emits an HTTP 1.0 style response - no chunking, no content length, "
        + "terminate the connection to indicate end of data.  Interestingly, the JDK's "
        + "HTTP client claims the HTTP version on the response is HTTP 1.1 and handles it "
        + "correctly (it HAS no enum constant for HTTP 1.0).  It lies.")
public class OldStyleHttp extends Acteur implements ChannelFutureListener {

    private static final long MAX_INVOCATIONS = 10;
    private final int totalInvocations;
    private volatile int invocations;

    @Inject
    OldStyleHttp(HttpEvent evt) {
        totalInvocations = evt.longUrlParameter("count").or(MAX_INVOCATIONS).intValue();
        add(CONTENT_TYPE, PLAIN_TEXT_UTF_8);
        setChunked(false);
        response().unchunked();
//                .contentWriter(this);
        setResponseBodyWriter(this);
        ok();
    }

    @Override
    public void operationComplete(ChannelFuture f) throws Exception {
        if (f.isCancelled() || f.cause() != null) {
            return;
        }
        f.channel().flush();
        int invok = invocations++;
        if (invok == totalInvocations) {
            f.addListener(CLOSE);
        } else {
            byte[] msg = (invok + ". ABCDEFGHIJKLMNOPQRSTUVWXYZ\n").getBytes(UTF_8);
            ByteBuf buf = f.channel().alloc().buffer(msg.length);
            buf.writeBytes(msg);
            f.channel().writeAndFlush(buf)
                    .addListener(this);
        }
    }
}
