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
package com.mastfrog.http.test.microframework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A dirt-simple abstraction for double-hyphen-prefixed line switches. This is
 * used to allow tests to process line switches.
 *
 * @author Tim Boudreau
 */
public final class Arguments {

    public static String SKIP = "skip";
    public static String ONLY = "only";

    private final String[] args;

    private Arguments(String[] args) {
        this.args = args;
    }

    public static Arguments create(String... args) {
        return new Arguments(args);
    }

    /**
     * Test if a switch <code>--arg</code> is present.
     *
     * @param arg The line switch, sans the preceding double-dashes
     * @return true if it is present
     */
    public boolean getBoolean(String arg) {
        return contains(arg);
    }

    /**
     * Get the value immediately following the switch value for <code>arg</code>
     * parsed as an integer, returning the default value if it is not present or
     * unparseable.
     *
     * @param arg A switch name
     * @param defaultValue The default value
     * @return An int
     */
    public int getInt(String arg, int defaultValue) {
        try {
            return getInt(arg).orElse(defaultValue);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
            return defaultValue;
        }
    }

    /**
     * Get the argument immediately following the switch corresponding to arg,
     * if any, parsed as an integer.
     *
     * @param arg A line switch name
     * @return An integer if present
     */
    public Optional<Integer> getInt(String arg) {
        try {
            return getSingle(arg).map(val -> {
                return Integer.parseInt(arg);
            });
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Get all arguments subsequent to a line switch, up to the last one, or the
     * next argument starting with <code>--</code>.
     *
     * @param arg A line switch
     * @return A list of arguments, empty if none are present
     */
    public List<String> getAll(String arg) {
        int ix = indexOf(arg);
        if (ix < 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (int i = ix + 1; i < args.length; i++) {
            String s = args[i];
            if (s.startsWith("--")) {
                break;
            }
            result.add(s);
        }
        return result;
    }

    /**
     * Determine if an item exists under the --skip argument.
     *
     * @param what A line switch name
     * @return true if the item is skipped
     */
    public boolean isSkipped(String what) {
        List<String> skips = getAll(SKIP);
        return skips.contains(what);
    }

    /**
     * Determine if an item is included - not skipped, and if the "only" line
     * switch is passed, if it is included in that list.
     *
     * @param what A line switch name
     * @return true if the item is not skipped and included in the list or the
     * list is empty
     */
    public boolean isIncluded(String what) {
        boolean result = !isSkipped(what);
        if (result) {
            List<String> included = getAll(ONLY);
            if (!included.isEmpty()) {
                result = included.contains(what);
            }
        }
        return result;
    }

    /**
     * Get the value immediately following a line switch, returning the default
     * if not present.
     *
     * @param arg A line switch name
     * @param defaultValue A fallback value
     * @return A string
     */
    public String getSingle(String arg, String defaultValue) {
        return getSingle(arg).orElse(defaultValue);
    }

    /**
     * Get the value immediately following a line switch, if present.
     *
     * @param arg A line switch name
     * @return An optional string
     */
    public Optional<String> getSingle(String arg) {
        int ix = indexOf(arg);
        if (ix < 0 || ix == args.length - 1) {
            return Optional.empty();
        }
        String next = args[ix + 1];
        if (next.startsWith("--")) {
            return Optional.empty();
        }
        return Optional.of(next);
    }

    /**
     * Determine if the set of arguments contains the passed string.
     *
     * @param arg An argument value
     * @return true if it is present
     */
    public boolean contains(String arg) {
        return indexOf(arg) >= 0;
    }

    /**
     * Get the location in the set of arguments of an argument.
     *
     * @param lineSwitchName A line switch name
     * @return an integer, negative if not present
     */
    public int indexOf(String lineSwitchName) {
        if (!lineSwitchName.startsWith("--")) {
            lineSwitchName = "--" + lineSwitchName;
        }
        return arguments().indexOf(lineSwitchName);
    }

    /**
     * Get the set of application arguments.
     * @return A list of strings
     */
    public List<String> arguments() {
        return Arrays.asList(args);
    }

    /**
     * Returns the set of arguments as a space-separated string.
     * 
     * @return A string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]);
            if (i < args.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
