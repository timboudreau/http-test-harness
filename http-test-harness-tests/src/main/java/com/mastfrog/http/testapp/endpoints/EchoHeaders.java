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
@Methods(GET)
@Path("/echoHeaders")
@Description("Takes the HTTP header 'stuff' and repeats it as the response")
public class EchoHeaders extends Acteur {

    @Inject
    public EchoHeaders(HttpEvent evt) {
        String hdr = evt.header("stuff");
        ok(hdr);
    }
}
