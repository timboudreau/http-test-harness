/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.http.test.harness.acteur.tests;

import com.mastfrog.acteur.annotations.GenericApplicationModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mastfrog.http.test.harness.acteur.HttpHarness;
import com.mastfrog.http.test.harness.acteur.HttpTestHarnessModule;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({GenericApplicationModule.class, HttpTestHarnessModule.class})
public class HelloActeurTest {

    @Test
    public void testHello(HttpHarness harn) {
        harn.get("hello").responseFinishedTimeout(Duration.ofMinutes(1))
                .applyingAssertions(asserts -> {
                    asserts.assertBody("Hello world!")
                            .assertHeaderEquals("x-woog", "blah")
                            .assertHasHeader("date")
                            .assertOk();
                }).assertNoFailures();
    }

    @Test
    public void testNotFound(HttpHarness harn) {
        harn.get("nothing").responseFinishedTimeout(Duration.ofMinutes(1))
                .applyingAssertions(asserts -> {
                    asserts
                            .assertHasHeader("date")
                            .assertNotFound();
                }).assertNoFailures();
    }
}
