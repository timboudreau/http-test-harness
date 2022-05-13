package com.mastfrog.http.testapp.endpoints;

import com.google.common.net.MediaType;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.InjectRequestBodyAs;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import io.netty.handler.codec.http.HttpResponseStatus;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path(value = "/jsonInput")
@Methods(value = Method.POST)
@InjectRequestBodyAs(value = SomeObject.class)
@Description("Expects a JSON instance of SomeObject as input, and returns "
        + "an incremented/modified version of it that is predictable from the input")
class JsonInput extends Acteur {

    @Inject
    JsonInput(SomeObject obj) {
        if (obj.value < 0) {
            badRequest("Negative numbers aren't nice.");
        } else {
            add(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8);
            reply(HttpResponseStatus.ACCEPTED, obj.incremented());
        }
    }
}
