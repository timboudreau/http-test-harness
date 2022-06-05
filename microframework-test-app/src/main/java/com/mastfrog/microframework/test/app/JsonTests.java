/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.microframework.test.app;

import com.mastfrog.http.harness.FailureSeverity;
import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.http.test.microframework.Invokable;
import com.mastfrog.http.testapp.endpoints.SomeObject;
import com.mastfrog.predicates.Predicates;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/**
 *
 * @author Tim Boudreau
 */
@Invokable
public class JsonTests {

    private final HttpTestHarness<String> harn;

    public JsonTests(HttpTestHarness harn) {
        this.harn = harn;
    }

    public void testJsonInput() {
        SomeObject obj = new SomeObject(23, "skiddoo");
        harn.postObject("jsonInput", obj)
                .applyingAssertions(asserts -> {
                    asserts.assertResponseCodeGreaterThan(199)
                            .assertResponseCodeLessThan(400)
                            .assertVersion(HTTP_1_1)
                            .assertDeserializedBodyEquals(
                                    new SomeObject(24, "skiddoo-xx"))
                            .assertDeserializedBodyEquals(
                                    new SomeObject(57, "woogle"))
                            .withSeverity(FailureSeverity.WARNING, as -> {
                                as.assertResponseCode(367);
                                as.assertBody(Predicates.namedPredicate("Throw Something", b -> {
                                    throw new IllegalStateException("Woops");
                                }));
                            });
                });
    }
}
