package com.mastfrog.http.testapp.endpoints;

import com.google.common.net.MediaType;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path(value = "/json")
@Methods(value = Method.GET)
@RequiredUrlParameters(value = {"text", "val"})
@ParametersMustBeNumbersIfPresent("val")
@Description("Emits the URL parameters <code>text</code> and <code>val</code> "
        + "as a JSON instance of SomeObject.")
class HelloJson extends Acteur {

    @Inject
    HelloJson(HttpEvent request) {
        add(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8);
        String txt = request.urlParameter("text");
        long val = request.longUrlParameter("val").get();
        ok(new SomeObject((int) val, txt));
    }

}
