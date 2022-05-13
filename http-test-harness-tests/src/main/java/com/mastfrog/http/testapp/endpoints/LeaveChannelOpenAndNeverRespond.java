package com.mastfrog.http.testapp.endpoints;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
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

    public static volatile boolean CHANNEL_WAS_CLOSED;

    @Inject
    LeaveChannelOpenAndNeverRespond(HttpEvent evt) {
        evt.channel().closeFuture().addListener(f -> {
            CHANNEL_WAS_CLOSED = true;
        });
        // This gives us a CompletableFuture that would continue the
        // response chain, which we intentionally never complete, in order
        // to test the logic that ensures timeouts work in the test harness
        defer();
    }
}
