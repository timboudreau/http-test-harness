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

import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.http.test.microframework.Invokable;

/**
 *
 * @author Tim Boudreau
 */
@Invokable
public class TestManyMethods {

    private final HttpTestHarness<String> harness;

    public TestManyMethods(HttpTestHarness harness) {
        this.harness = harness;
    }

    public void testOne(int port) {
        System.out.println("testOne");
        harness.get("hello").applyingAssertions(asserts -> {
            asserts.assertOk();
        });
    }

    public void testTwo() {
        System.out.println("testTwo");
        harness.get("hello").applyingAssertions(asserts -> {
            asserts.assertOk();
        });
    }

    public void testThree(HttpTestHarness<String> harness) {
        System.out.println("testThree");
        harness.get("hello").applyingAssertions(asserts -> {
            asserts.assertOk();
        });
    }
    
    public void throwSomething() throws Exception {
        throw new Exception("Hey there");
    }
}
