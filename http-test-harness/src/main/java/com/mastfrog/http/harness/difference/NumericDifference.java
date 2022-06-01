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

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.lang.Math.abs;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 *
 * @author Tim Boudreau
 */
final class NumericDifference implements Difference<Number> {

    private final Number old;
    private final Number nue;

    NumericDifference(Number old, Number nue) {
        this.old = notNull("old", old);
        this.nue = notNull("nue", nue);
    }

    @Override
    public Number oldValue() {
        return old;
    }

    @Override
    public Number newValue() {
        return nue;
    }

    boolean isFloatingPoint() {
        return (old instanceof Float || old instanceof Double)
                || (nue instanceof Float || nue instanceof Double);
    }

    @Override
    public DifferenceKind kind() {
        return DifferenceKind.CHANGE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("change(");
        if (isFloatingPoint()) {
            double a = old.doubleValue();
            double b = nue.doubleValue();
            double diff = abs(a - b);
            NumberFormat fmt = DecimalFormat.getNumberInstance();
            sb.append(fmt.format(a)).append(" -> ").append(fmt.format(b))
                    .append(" \u0394 ").append(fmt.format(diff));
        } else {
            long a = old.longValue();
            long b = nue.longValue();
            long diff = abs(a - b);
            NumberFormat fmt = DecimalFormat.getIntegerInstance();
            sb.append(fmt.format(a)).append(" -> ").append(fmt.format(b))
                    .append(" \u0394 ").append(fmt.format(diff));
        }
        return sb.append(')').toString();
    }
}
