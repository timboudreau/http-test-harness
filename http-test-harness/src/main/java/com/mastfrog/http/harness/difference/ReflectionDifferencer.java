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

import static com.mastfrog.http.harness.difference.DifferenceKind.INSERTION;
import com.mastfrog.util.strings.Strings;
import static java.lang.Math.abs;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * General-purpose differencer. Mows through the fields of two objects
 * (presumably of the same type), recursively, collecting differences between
 * them.
 *
 * @author Tim Boudreau
 */
final class ReflectionDifferencer implements Differencer<Object> {

    private final List<Differencer<?>> differencers = new ArrayList<>();

    ReflectionDifferencer() {
        // Differences for any case where one item is null and the other is not
        differencers.add(new OneNullDifferencer());
        // Compares numbers and includes a delta in the difference text
        differencers.add(new NumberDifferencer());
        // For performance
        differencers.add(new BooleanDifferencer());
        // Compares strings and gives useful before/after comparison output
        differencers.add(new StringDifferencer());
        // Compares things like Enum and Class which must be instance equality
        differencers.add(new InstanceEqualityDifferencer());
        // Compares maps and objects which have been converted into maps for
        // comparison
        differencers.add(new MapDifferencer());
        // Compares arrays by converting them into lists and using collection
        // differencing
        differencers.add(new ArrayDifferencer());
        // Reflectively drills through a random type, grabbing field values
        // and recursively comparing those
        differencers.add(new DissectingDifferencer());
        // Compares sets
        differencers.add(new SetDifferencer());
        // Compares collections, with special diff-generation handling for lists
        differencers.add(new CollectionDifferencer());
        // Compares objects on the value of toString()
        differencers.add(new ToStringDifferencer());
    }

    private static boolean isPrimitiveLike(Object o) {
        return o instanceof String || o instanceof Number
                || o instanceof Boolean || o instanceof Character
                || o instanceof BigInteger || o instanceof BigDecimal
                || o instanceof CharSequence;
    }

    public Map<String, Set<Difference<?>>> difference(Object a, Object b) {
        DifferencesBuilder<Map<String, Set<Difference<?>>>> db
                = DifferencesBuilder.root();
        difference("", a, b, db);
        return db.build();
    }

    @Override
    public <P> void difference(String name, Object a, Object b,
            DifferencesBuilder<P> bldr) {
        if (Objects.equals(a, b)) {
            return;
        }

        for (Differencer<?> diff : differencers) {
            if (diff.differenceIfPossible(name, a, b, bldr)) {
                return;
            }
        }
        bldr.add(Difference.create(a, b));
    }

    private static abstract class MatchingDifferencer<T> implements Differencer<T> {

        private final Class<? super T> type;

        MatchingDifferencer(Class<? super T> type) {
            this.type = type;
        }

        @Override
        public <R> boolean canDifference(R a, R b) {
            return type.isInstance(a) && type.isInstance(b);
        }
    }

    private static final class OneNullDifferencer implements Differencer<Object> {

        @Override
        public <P> void difference(String name, Object a, Object b,
                DifferencesBuilder<P> bldr) {
            bldr.add(DifferenceKind.CHANGE.newDifference(a, b));
        }

        @Override
        public <R> boolean canDifference(R a, R b) {
            return (a == null) != (b == null);
        }
    }

    private static final class StringDifferencer extends MatchingDifferencer<CharSequence> {

        StringDifferencer() {
            super(CharSequence.class);
        }

        @Override
        public <P> void difference(String name, CharSequence a, CharSequence b, DifferencesBuilder<P> bldr) {
            if (!Strings.charSequencesEqual(a, b)) {
                bldr.add(new StringDifference(a, b));
            }
        }
    }

    private static final class NumberDifferencer extends MatchingDifferencer<Number> {

        NumberDifferencer() {
            super(Number.class);
        }

        @Override
        public <P> void difference(String name, Number a, Number b, DifferencesBuilder<P> bldr) {
            boolean isChange;
            if (isFloatingPoint(a, b)) {
                double aval = a.doubleValue();
                double bval = b.doubleValue();
                isChange = abs(aval - bval) != 0D;
            } else {
                long aval = a.longValue();
                long bval = b.longValue();
                isChange = abs(aval - bval) != 0L;
            }
            if (isChange) {
                bldr.add(new NumericDifference(a, b));
            }
        }

        private static boolean isFloatingPoint(Number a, Number b) {
            return isFloatingPoint(a) || isFloatingPoint(b);
        }

        private static boolean isFloatingPoint(Number num) {
            return num instanceof Float || num instanceof Double;
        }
    }

    static class InstanceEqualityDifferencer implements Differencer<Object> {

        @Override
        public <P> void difference(String name, Object a, Object b, DifferencesBuilder<P> bldr) {
            if (a != b) {
                bldr.add(DifferenceKind.CHANGE.newDifference(a, b));
            }
        }

        @Override
        public <R> boolean canDifference(R a, R b) {
            return a != null && b != null
                    && canDifference(a) && canDifference(b)
                    && a.getClass() == b.getClass();
        }

        private boolean canDifference(Object o) {
            return o instanceof Class<?> || o instanceof Enum<?>;
        }
    }

    private static final class BooleanDifferencer extends MatchingDifferencer<Boolean> {

        BooleanDifferencer() {
            super(Boolean.class);
        }

        @Override
        public <P> void difference(String name, Boolean a, Boolean b, DifferencesBuilder<P> bldr) {
            if (a.booleanValue() != b.booleanValue()) {
                bldr.add(DifferenceKind.CHANGE.newDifference(a, b));
            }
        }
    }

    private final class ArrayDifferencer implements Differencer<Object> {

        @Override
        public <P> void difference(String name, Object a, Object b,
                DifferencesBuilder<P> bldr) {
            if (a != null && a.getClass().isArray() && b != null && b.getClass().isArray()) {
                List<Object> alist = arrayToList(a);
                List<Object> blist = arrayToList(b);
                ReflectionDifferencer.this.difference(name, alist, blist, bldr);
            }
        }

        @Override
        public <R> boolean canDifference(R a, R b) {
            return a != null && a.getClass().isArray()
                    && b != null && b.getClass().isArray();
        }
    }

    private final class DissectingDifferencer extends MatchingDifferencer<Object> {

        DissectingDifferencer() {
            super(Object.class);
        }

        @Override
        public <P> void difference(String name, Object a, Object b,
                DifferencesBuilder<P> bldr) {
            Map<String, Object> amap = toMap(a);
            Map<String, Object> bmap = toMap(b);
            ReflectionDifferencer.this.difference(name, amap, bmap, bldr);
        }

        private boolean isCollectionOrArrayMap(Object o) {
            return o != null && (o.getClass().isArray()
                    || o instanceof Map<?, ?>
                    || o instanceof Collection<?>);
        }

        @Override
        public <R> boolean canDifference(R a, R b) {
            return !isPrimitiveLike(a) && !isPrimitiveLike(b)
                    && !isCollectionOrArrayMap(a) && !isCollectionOrArrayMap(b);
        }

        Map<String, Object> toMap(Object o) {
            Map<String, Object> m = new LinkedHashMap<>();
            dissect(o, m::put);
            return m;
        }

        private boolean dissect(Object o, BiConsumer<String, Object> c) {
            if (!isPrimitiveLike(o)) {
                dissect(o, o.getClass(), c);
                return true;
            } else {
                c.accept("value", o);
            }
            return false;
        }

        private void dissect(Object o, Class<?> type, BiConsumer<String, Object> c) {
            if (type == Object.class || type == Class.class) {
                return;
            }
            try {
                Field[] flds = type.getDeclaredFields();
                for (Field f : flds) {
                    if ((f.getModifiers() & Modifier.STATIC) == 0) {
                        f.setAccessible(true);
                        Object value = f.get(o);
                        c.accept(f.getName(), value);
                    }
                }
                dissect(o, type.getSuperclass(), c);
            } catch (Exception | Error e) {
                e.printStackTrace();
            }
        }
    }

    private final class MapDifferencer extends MatchingDifferencer<Map<?, ?>> {

        MapDifferencer() {
            super(Map.class);
        }

        @Override
        public <P> void difference(String name, Map<?, ?> a, Map<?, ?> b,
                DifferencesBuilder<P> bldr) {
            withKeyDivergence(a, b, (removed, added) -> {
                for (Object rem : removed) {
                    bldr.add(DifferenceKind.DELETION.newDifference(rem));
                }
                for (Object add : added) {
                    bldr.add(DifferenceKind.INSERTION.newDifference(add));
                }
                Set<Object> remainder = intersection(a.keySet(), b.keySet());
                for (Object key : remainder) {
                    String kname = key.toString();
                    DifferencesBuilder<DifferencesBuilder<P>> ch
                            = bldr.child(kname);
                    try {
                        ReflectionDifferencer.this.difference(kname, a.get(key),
                                b.get(key), ch);
                    } finally {
                        ch.build();
                    }
                }
            });
        }
    }

    private final class SetDifferencer extends MatchingDifferencer<Set<?>> {

        SetDifferencer() {
            super(Set.class);
        }

        @Override
        public <P> void difference(String name, Set<?> a, Set<?> b,
                DifferencesBuilder<P> bldr) {
            Set<Object> aset = setFrom(a);
            Set<Object> bset = setFrom(b);
            withKeyDivergence(aset, bset, (removed, added) -> {
                for (Object o : bset) {
                    bldr.add(name + ".added", INSERTION.newDifference(o));
                }
                for (Object o : aset) {
                    bldr.add(name + ".removed", INSERTION.newDifference(o));
                }
            });
        }
    }

    private final class CollectionDifferencer extends MatchingDifferencer<Collection<?>> {

        CollectionDifferencer() {
            super(Collection.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <P> void difference(String name, Collection<?> a, Collection<?> b,
                DifferencesBuilder<P> bldr) {

            if (a instanceof List<?> && b instanceof List<?>) {
                try {

                    // Sort by toString()?
                    new ParallelIterator<>((List<Object>) a, (List<Object>) b)
                            .getChanges()
                            .forEach(diff -> {
                                bldr.add(diff);
                                diff.addChildDifferences(ReflectionDifferencer.this, bldr);
                            });
                    if (a.size() != b.size()) {
                        bldr.add("size", DifferenceKind.CHANGE.newDifference(a.size(), b.size()));
                    }
                    return;
                } catch (IllegalStateException ex) {
                    // The algorithm is not duplicate-tolerant, and this may be
                    // thrown if the collection is not a set
                }
            }
            List<Object> aa = toList(a);
            List<Object> bb = toList(b);

            if (aa.size() != bb.size()) {
                bldr.add("size", DifferenceKind.CHANGE.newDifference(aa.size(),
                        bb.size()));
            }
            for (int i = 0; i < Math.min(aa.size(), bb.size()); i++) {
                Object aaa = aa.get(i);
                Object bbb = bb.get(i);
                if (!Objects.equals(aaa, bbb)) {
                    ReflectionDifferencer.this.difference(Integer.toString(i),
                            aaa, bbb, bldr.child(Integer.toString(i)));
                }
            }
            Set<Object> aset = setFrom(aa);
            Set<Object> bset = setFrom(bb);
            withKeyDivergence(aset, bset, (removed, added) -> {
                for (Object o : bset) {
                    bldr.add("added", DifferenceKind.INSERTION.newDifference(o));
                }
                for (Object o : aset) {
                    bldr.add("removed", DifferenceKind.DELETION.newDifference(o));
                }
            });
        }
    }

    static List<Object> arrayToList(Object o) {
        int size = Array.getLength(o);
        List<Object> objs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            objs.add(Array.get(o, i));
        }
        return objs;
    }

    static List<Object> toList(Collection<?> what) {
        return new ArrayList<>(what);
    }

    private static boolean keysComparable(Map<?, ?> aKeys) {
        for (Map.Entry<?, ?> e : aKeys.entrySet()) {
            if (!(e.getKey() instanceof Comparable<?>)) {
                return false;
            }
        }
        return true;
    }

    static boolean isComparable(Collection<? extends Object> coll) {
        for (Object o : coll) {
            if (!(o instanceof Comparable<?>)) {
                return false;
            }
        }
        return true;
    }

    static Map<Object, Object> copyOf(Map<?, ?> map) {
        if (keysComparable(map)) {
            return new TreeMap<>(map);
        } else {
            return new LinkedHashMap<>(map);
        }
    }

    static Set<Object> setFrom(Collection<?> set) {
        if (isComparable(set)) {
            return new TreeSet<>(set);
        } else {
            return new LinkedHashSet<>(set);
        }
    }

    static Set<Object> absent(Set<? extends Object> from, Set<? extends Object> in) {
        Set<Object> fromCopy = setFrom(from);
        fromCopy.removeAll(in);
        return fromCopy;
    }

    static Set<Object> added(Set<? extends Object> from, Set<? extends Object> to) {
        Set<Object> inCopy = setFrom(to);
        inCopy.removeAll(from);
        return inCopy;
    }

    static Set<Object> intersection(Set<?> a, Set<?> b) {
        Set<Object> set = setFrom(a);
        set.retainAll(b);
        return set;
    }

    static void withKeyDivergence(Map<?, ?> a, Map<?, ?> b,
            BiConsumer<Set<Object>, Set<Object>> c) {
        Map<Object, Object> aa = copyOf(a);
        Map<Object, Object> bb = copyOf(b);
        Set<Object> akeys = aa.keySet();
        Set<Object> bkeys = bb.keySet();
        Set<Object> removals = absent(bkeys, akeys);
        Set<Object> additions = added(akeys, bkeys);
        c.accept(removals, additions);
    }

    static void withKeyDivergence(Set<?> a, Set<?> b,
            BiConsumer<Set<Object>, Set<Object>> c) {
        Set<Object> akeys = setFrom(a);
        Set<Object> bkeys = setFrom(b);
        Set<Object> removals = absent(bkeys, akeys);
        Set<Object> additions = added(akeys, bkeys);
        c.accept(removals, additions);
    }

    private static final class ToStringDifferencer extends
            MatchingDifferencer<Object> {

        ToStringDifferencer() {
            super(Object.class);
        }

        @Override
        public <P> void difference(String name, Object a, Object b,
                DifferencesBuilder<P> bldr) {
            String astr = Objects.toString(a);
            String bstr = Objects.toString(b);
            if (!astr.equals(bstr)) {
                bldr.add(DifferenceKind.CHANGE.newDifference(a, b));
            }
        }
    }
}
