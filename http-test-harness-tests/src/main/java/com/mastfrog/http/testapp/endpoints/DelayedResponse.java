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
