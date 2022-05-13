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

import java.util.regex.Pattern;

/**
 * We do some internal logging of the activities of the test harness, which
 * should not be tied to a particular logging framework or method of logging.
 *
 * @author Tim Boudreau
 */
public enum HarnessLogLevel {
    DEBUG,
    DETAIL,
    IMPORTANT;
    public static final String SYS_PROP_DEFAULT_LEVEL = "com.mastfrog.http.harness.level";

    public boolean isGreaterThanOrEqualTo(HarnessLogLevel other) {
        return other == this ? true : ordinal() >= other.ordinal();
    }


    @Override
    public String toString() {
        return name().toLowerCase();
    }

    static HarnessLogLevel getDefault() {
        String prop = System.getProperty(SYS_PROP_DEFAULT_LEVEL);
        if (prop == null) {
            return DETAIL;
        }
        return find(prop);
    }

    private static HarnessLogLevel find(String what) {
        for (HarnessLogLevel level : values()) {
            if (level.name().equalsIgnoreCase(what)) {
                return level;
            }
        }
        Pattern numbers = Pattern.compile("^\\d+$");
        if (numbers.matcher(what).find()) {
            try {
                int ord = Integer.parseInt(what);
                HarnessLogLevel[] all = values();
                if (ord >= all.length) {
                    throw new IllegalArgumentException(
                            "Ordinal out of range for HarnessLogLevel: '"
                            + what + "'");
                }
                return all[ord];
            } catch (IllegalArgumentException nfe) {
                System.err.println("Invalid log level value '"
                        + what + "'");
                nfe.printStackTrace(System.err);
            }
        }
        return DETAIL;
    }
}
