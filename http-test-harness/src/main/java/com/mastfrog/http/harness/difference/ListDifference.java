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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import static java.lang.Math.min;
import java.util.List;

/**
 * To make Jackson-friendly, extracts the relevant info from a
 * ParallelIterator.ListDiffChange, which references parent class values.
 *
 * @author Tim Boudreau
 */
final class ListDifference implements Difference<List<?>> {

    private final List<?> oldValue;
    private final List<?> newValue;
    private final String stringValue;
    private final DifferenceKind kind;
    private final int start;

    ListDifference(ParallelIterator.ListDiffChange change) {
        this(change.oldValue(), change.newValue(), change.toString(), change.kind(),
                change.getStart());
    }

    @JsonCreator
    ListDifference(
            @JsonProperty("oldValue") List<?> oldValue,
            @JsonProperty("newValue") List<?> newValue,
            @JsonProperty("stringValue") String stringValue,
            @JsonProperty("kind") DifferenceKind kind,
            @JsonProperty("start") int start) {
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.kind = kind;
        this.stringValue = stringValue;
        this.start = start;
    }

    @JsonProperty("start")
    public int start() {
        return start;
    }

    @Override
    @JsonProperty("oldValue")
    public List<?> oldValue() {
        return oldValue;
    }

    @Override
    @JsonProperty("newValue")
    public List<?> newValue() {
        return newValue;
    }

    @Override
    @JsonProperty("kind")
    public DifferenceKind kind() {
        return kind;
    }

    @Override
    @JsonProperty("stringValue")
    public String toString() {
        return stringValue;
    }

    <P> void addChildDifferences(Differencer<Object> into, DifferencesBuilder<P> bldr) {
        if (kind() == DifferenceKind.CHANGE) {
            List<?> oldValues = this.oldValue();
            List<?> newValues = this.newValue();
            int max = min(oldValues.size(), newValues.size());
            for (int i = 0; i < max; i++) {
                Object oldObj = oldValues.get(i);
                Object newObj = newValues.get(i);
                String ixString = Integer.toString(start + i);
                DifferencesBuilder<DifferencesBuilder<P>> child
                        = bldr.child(ixString);
                into.differenceIfPossible(ixString, oldObj, newObj, child);
                child.build();
            }
        }
    }
}
