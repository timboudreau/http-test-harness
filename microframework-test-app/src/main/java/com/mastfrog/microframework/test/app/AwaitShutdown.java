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
import com.mastfrog.http.test.microframework.Fixture;
import java.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
@Fixture
public class AwaitShutdown {
    
    public void shutdown(HttpTestHarness<String> harn) throws InterruptedException {
        Thread.sleep(2000);
        System.out.println("awaitShutdown awaitQuiet");
        harn.awaitQuiet(Duration.ofMinutes(5), false);
        System.out.println("awaitShutdown awaitQuiet exit");
        System.out.println("awaitShutdown await");
        harn.await();
        System.out.println("awaitShutdown await exit");
    }
}
