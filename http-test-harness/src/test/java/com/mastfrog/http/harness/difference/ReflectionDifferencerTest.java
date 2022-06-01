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

import static com.mastfrog.http.harness.difference.ReflectionDifferencer.absent;
import static com.mastfrog.http.harness.difference.ReflectionDifferencer.added;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
final class ReflectionDifferencerTest {

    static final OtherThing OTHER_THING_ONE = new OtherThing("a", 23);
    static final OtherThing OTHER_THING_TWO = new OtherThing("b", 42);
    static final OtherThing OTHER_THING_THREE = new OtherThing("c", 157);

    static final HooberGoober HOOBER_ONE = new HooberGoober("x", new long[]{200, 330, 800});
    static final HooberGoober HOOBER_TWO = new HooberGoober("y", new long[]{1200, 340, 800});
    static final HooberGoober HOOBER_THREE = new HooberGoober("y", new long[]{12001, 1340, 12034824});

    static final Thing THING_ONE = new Thing(23, (short) 25000,
            new int[]{13, 6, 17, 21}, "wookies", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE);

    static final Thing THING_TWO = new Thing(42, (short) 25000,
            new int[]{13, 6, 17, 21}, "wookies", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE);

    static final Thing THING_THREE = new Thing(23, (short) 25001,
            new int[]{13, 6, 17, 21}, "wookies", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE);

    static final Thing THING_FOUR = new Thing(23, (short) 25001,
            new int[]{13, 6, 21, 17}, "wookies", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE);

    static final Thing THING_FIVE = new Thing(23, (short) 25001,
            new int[]{13, 6, 21, 17}, "hurbles", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE);

    static final Thing THING_SIX = new Thing(23, (short) 25001,
            new int[]{13, 6, 21, 17}, "hurbles", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_THREE},
            HOOBER_ONE);

    static final Thing THING_SEVEN = new Thing(23, (short) 25001,
            new int[]{13, 6, 21, 17}, "hurbles", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_THREE},
            HOOBER_TWO);

    static final Thing THING_EIGHT = new Thing(23, (short) 25000,
            new int[]{13, 6, 17, 21}, "wookies", new OtherThing[]{OTHER_THING_ONE, OTHER_THING_TWO},
            HOOBER_ONE.withPwee("wurgles"));

    static final Thing THING_NINE = new Thing(527, (short) 303,
            new int[]{5, 13, 22}, "gurgles", new OtherThing[]{OTHER_THING_THREE},
            HOOBER_THREE);

    static ReflectionDifferencer DIFFS = new ReflectionDifferencer();

    @Test
    public void test() throws Exception {

        testOne(THING_ONE, THING_ONE);

        testOne(THING_ONE, THING_TWO, "intVal");

        testOne(THING_ONE, THING_NINE, "hoober.nums", "hoober.pwee", "intVal",
                "ints", "otherThings", "s", "shortValue",
                "hoober.nums.0",
                "hoober.nums.1", "hoober.nums.2", "otherThings.0.bar", "otherThings.0.foo",
                "otherThings.size", "ints.size"
        );

        testOne(THING_ONE.withInts(new int[]{1, 2, 3, 14, 15, 16}),
                THING_ONE.withInts(new int[]{2, 3, 4, 5, 13, 14, 15, 16, 17, 18, 19}),
                "ints", "ints.size");
        testOne(THING_ONE.withInts(new int[]{1, 2, 3, 14, 15, 16, 100}),
                THING_ONE.withInts(new int[]{1, 2, 3, 24, 25, 26, 100}),
                "ints", "ints.3", "ints.4", "ints.5");

    }

    private void testOne(Object a, Object b, String... expected) throws Exception {
        Map<String, Set<Difference<?>>> differences = DIFFS.difference(a, b);
//        differences.forEach((path, diffs) -> {
//            System.out.println(" * " + path + " " + diffs);
//        });
        Set<String> exp = new TreeSet<>(Arrays.asList(expected));
        Set<String> got = new TreeSet<>(differences.keySet());
        if (!got.equals(exp)) {
            Set<Object> ab = absent(exp, differences.keySet());
            Set<Object> added = added(exp, differences.keySet());
            fail("Detected difference keys differ - absent: " + ab + " added " + added
                    + "\nExp: " + exp + "\nGot: " + got);
        }
    }

    static class Thing {

        private final int intVal;
        private final short shortValue;
        private final int[] ints;
        private final String s;
        private final OtherThing[] otherThings;
        private final HooberGoober hoober;

        public Thing(int intVal, short shortValue, int[] ints,
                String s, OtherThing[] otherThings, HooberGoober hoober) {
            this.intVal = intVal;
            this.shortValue = shortValue;
            this.ints = ints;
            this.s = s;
            this.otherThings = otherThings;
            this.hoober = hoober;
        }

        public Thing copy() {
            OtherThing[] ots = otherThings == null
                    ? null
                    : new OtherThing[otherThings.length];
            if (ots != null) {
                for (int i = 0; i < otherThings.length; i++) {
                    ots[i] = otherThings[i].copy();
                }
            }
            return new Thing(intVal, shortValue,
                    ints == null ? null : Arrays.copyOf(ints, ints.length),
                    s, ots, hoober.copy());
        }

        public Thing withIntVal(int intVal) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        public Thing withShortValue(short shortValue) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        public Thing withInts(int[] ints) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        public Thing withS(String s) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        public Thing withOtherThings(OtherThing[] otherThings) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        public Thing withHooberGoober(OtherThing[] otherThings) {
            return new Thing(intVal, shortValue, ints, s, otherThings, hoober);
        }

        @Override
        public String toString() {
            return "Thing{" + "intVal=" + intVal + ", shortValue=" + shortValue
                    + ", ints=" + (ints == null ? "null" : Arrays.toString(ints))
                    + ", s=" + s + ", otherThings="
                    + (otherThings == null ? "null" : Arrays.toString(otherThings))
                    + ", hoober=" + hoober + '}';
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + this.intVal;
            hash = 89 * hash + this.shortValue;
            hash = 89 * hash + Arrays.hashCode(this.ints);
            hash = 89 * hash + Objects.hashCode(this.s);
            hash = 89 * hash + Arrays.deepHashCode(this.otherThings);
            hash = 89 * hash + Objects.hashCode(this.hoober);
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
            final Thing other = (Thing) obj;
            if (this.intVal != other.intVal) {
                return false;
            }
            if (this.shortValue != other.shortValue) {
                return false;
            }
            if (!Objects.equals(this.s, other.s)) {
                return false;
            }
            if (!Arrays.equals(this.ints, other.ints)) {
                return false;
            }
            if (!Arrays.deepEquals(this.otherThings, other.otherThings)) {
                return false;
            }
            return Objects.equals(this.hoober, other.hoober);
        }

    }

    static class OtherThing implements Comparable<OtherThing> {

        public final String foo;
        public final int bar;

        public OtherThing(String foo, int bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public OtherThing copy() {
            return new OtherThing(new String(foo.toCharArray()), bar);
        }

        @Override
        public String toString() {
            return "OtherThing{" + "foo=" + foo + ", bar=" + bar + '}';
        }

        @Override
        public int compareTo(OtherThing o) {
            int result = foo.compareTo(o.foo);
            if (result == 0) {
                result = Integer.compare(bar, o.bar);
            }
            return result;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + Objects.hashCode(this.foo);
            hash = 37 * hash + this.bar;
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
            final OtherThing other = (OtherThing) obj;
            if (this.bar != other.bar) {
                return false;
            }
            return Objects.equals(this.foo, other.foo);
        }
    }

    static class HooberGoober {

        private final String pwee;
        private final long[] nums;

        public HooberGoober(String pwee, long[] nums) {
            this.pwee = pwee;
            this.nums = nums;
        }

        public HooberGoober withPwee(String pwee) {
            return new HooberGoober(pwee, nums == null ? null : Arrays.copyOf(nums, nums.length));
        }

        public HooberGoober copy() {
            return new HooberGoober(pwee, Arrays.copyOf(nums, nums.length));
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 31 * hash + Objects.hashCode(this.pwee);
            hash = 31 * hash + Arrays.hashCode(this.nums);
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
            final HooberGoober other = (HooberGoober) obj;
            if (!Objects.equals(this.pwee, other.pwee)) {
                return false;
            }
            return Arrays.equals(this.nums, other.nums);
        }

        @Override
        public String toString() {
            return "HooberGoober{" + "pwee=" + pwee + ", nums="
                    + (nums == null ? "null" : Arrays.toString(nums)) + '}';
        }
    }
}
