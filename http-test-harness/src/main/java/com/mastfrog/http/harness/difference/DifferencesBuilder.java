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
package com.mastfrog.http.harness.difference;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class DifferencesBuilder<P> {

    private final String path;
    private final Function<DifferencesBuilder<P>, P> converter;
    private final Map<String, Set<Difference<?>>> differences = new TreeMap<>();

    DifferencesBuilder(String path, Function<DifferencesBuilder<P>, P> converter) {
        this.path = path;
        this.converter = converter;
    }

    DifferencesBuilder<P> add(String childPath, Collection<? extends Difference<?>> coll) {
        if (coll.isEmpty()) {
            return this;
        }
        String key = path.isEmpty() ? childPath : path + "." + childPath;
        Set<Difference<?>> diffs = differences.computeIfAbsent(key, k -> new HashSet<>());
        diffs.addAll(coll);
        return this;
    }

    public DifferencesBuilder<P> add(String name, Difference<?> diff) {
        String key = path.isEmpty() ? name : path + "." + name;
        Set<Difference<?>> diffs = differences.computeIfAbsent(key, k -> new HashSet<>());
        diffs.add(diff);
        return this;
    }

    public DifferencesBuilder<P> add(Difference<?> diff) {
        Set<Difference<?>> diffs = differences.computeIfAbsent(path, k -> new HashSet<>());
        diffs.add(diff);
        return this;
    }

    public static DifferencesBuilder<Map<String, Set<Difference<?>>>> root() {
        return new DifferencesBuilder<>("", bldr -> {
            return new TreeMap<>(bldr.differences);
        });
    }

    public String path() {
        return path;
    }

    public DifferencesBuilder<DifferencesBuilder<P>> child(String childName) {
        return new DifferencesBuilder<>(childName, bldr -> {
            bldr.differences.forEach((key, coll) -> {
                add(key, coll);
            });
            return this;
        });
    }

    public P build() {
        return converter.apply(this);
    }
}
