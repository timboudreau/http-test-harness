package com.mastfrog.http.testapp.endpoints;

import com.google.common.net.MediaType;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Path(value = "/hello")
@Methods(value = Method.GET)
@Description("Simply emits 'Hello world!'")
class HelloWorld extends Acteur {

    HelloWorld() {
        add(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
        add(Headers.stringHeader("Wookies"), "food");
        HttpResponseStatus variableResponseCode
                = System.currentTimeMillis() % 2 == 0
                ? HttpResponseStatus.OK
                : HttpResponseStatus.CREATED;
        reply(variableResponseCode, "Hello world!");
    }
}
