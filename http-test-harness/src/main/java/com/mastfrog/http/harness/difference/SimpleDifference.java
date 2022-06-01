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

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
final class SimpleDifference<T> implements Difference<T>{
    
    private final T old;
    private final T nue;
    private final DifferenceKind kind;

    SimpleDifference(T old, T nue, DifferenceKind kind) {
        this.old = old;
        this.nue = nue;
        this.kind = kind;
    }
    

    @Override
    public T oldValue() {
        return old;
    }

    @Override
    public T newValue() {
        return nue;
    }

    @Override
    public DifferenceKind kind() {
        return kind;
    }
    
    @Override
    public String toString() {
        return kind.format(old, nue);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.old);
        hash = 71 * hash + Objects.hashCode(this.nue);
        hash = 71 * hash + Objects.hashCode(this.kind);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SimpleDifference<?> other = (SimpleDifference<?>) obj;
        if (!Objects.equals(this.old, other.old)) {
            return false;
        }
        if (!Objects.equals(this.nue, other.nue)) {
            return false;
        }
        return Objects.equals(this.kind, other.kind);
    }
}
