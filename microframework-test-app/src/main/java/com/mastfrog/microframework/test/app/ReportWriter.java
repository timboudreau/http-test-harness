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
import com.mastfrog.http.harness.TestReport;
import com.mastfrog.http.test.microframework.OnError;
import com.mastfrog.http.test.microframework.PostRun;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 *
 * @author Tim Boudreau
 */
public class ReportWriter implements OnError {

    @PostRun
    public static void save(TestReport report, HttpTestHarness<String> harn) throws IOException, InterruptedException {
        Thread.sleep(5000);
        System.out.println("reportWriter await");
        harn.await();
        System.out.println("write report\n" + report);
        Path file = Paths.get("test-report.json");
        try ( OutputStream out = Files.newOutputStream(file)) {
            report.save(out);
        }
        file = Paths.get("test-report.html");
        Files.writeString(file, report.toHtml(), WRITE, TRUNCATE_EXISTING, CREATE);
    }

    @Override
    public void onError(String task, String executing, Throwable thrown) {
        System.out.println("onError " + task + " " + executing);
        thrown.printStackTrace();
        
    }
}
