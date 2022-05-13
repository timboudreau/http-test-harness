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
package com.mastfrog.http.harness;

import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Default harness logger implementation which writes to stdout or stderr, with
 * settable levels.
 *
 * @author Tim Boudreau
 */
final class StdoutHarnessLog implements BiConsumer<HarnessLogLevel, Supplier<String>> {

    private final HarnessLogLevel level;
    private final boolean stderr;
    private static final Escaper ESCAPE_OUTPUT
            = Escaper.escapeUnencodableAndControlCharacters(UTF_8);

    StdoutHarnessLog(HarnessLogLevel level, boolean stderr) {
        this.level = level;
        this.stderr = stderr;
    }

    StdoutHarnessLog() {
        this(HarnessLogLevel.getDefault(), false);
    }
    
    boolean isStderr() {
        return stderr;
    }
    
    HarnessLogLevel level() {
        return level;
    }

    private PrintStream printStream() {
        return stderr ? System.err : System.out;
    }

    @Override
    public void accept(HarnessLogLevel t, Supplier<String> u) {
        if (t == null) {
            t = HarnessLogLevel.DEBUG;
        }
        if (t.isGreaterThanOrEqualTo(level)) {
            String message = u.get();
            if (message == null) {
                message = "null";
            } else {
                message = Strings.escape(message, ESCAPE_OUTPUT);
            }
            printStream().println(message);
        }
    }
}
