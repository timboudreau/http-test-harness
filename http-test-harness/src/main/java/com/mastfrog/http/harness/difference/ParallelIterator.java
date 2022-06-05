/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
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
 /*
 * ParallelIterator.java
 *
 * Created on September 18, 2004, 6:37 PM
 */
package com.mastfrog.http.harness.difference;

import com.mastfrog.util.strings.Strings;
import static java.lang.Math.max;
import static java.lang.Math.min;
import java.util.*;

/**
 * (Ancient code from a circa 2004 NetBeans plugin)
 *
 * Processor class which uses the iterator pattern to iterate two lists and
 * build a list of Change objects representing a set of transformations which,
 * when applied in order, will generate the new list out of the old one.
 *
 * @author Tim Boudreau
 */
final class ParallelIterator<T extends Object> {

    // Some ancient borrowed code from my netbeans-contrib javanavigators plugin
    // for a fast (but not-duplicate-tolerant) list diff
    /**
     * Insertion type.
     */
    static final int INSERT = 1;
    /**
     * Deletion type.
     */
    static final int DELETE = 2;
    /**
     * Change type.
     */
    static final int CHANGE = 3;

    /**
     * The original list
     */
    private final List<T> old;
    /**
     * The modified list
     */
    private final List<T> nue;
    /**
     * The iterator of the original list
     */
    private Iterator<T> oi;
    /**
     * The iterator of the new list
     */
    private Iterator<T> ni;
    /**
     * A HashSet of the old list's contents for fast containment checks
     */
    private Set<T> h_old = null;
    /**
     * A HashSet of the new list's contents for fast containment checks
     */
    private Set<T> h_new = null;
    /**
     * The list of changes
     */
    private final List<ListDiffChange> changes = new ArrayList<>(5);
    /**
     * Most recently read item from the old array
     */
    private T lastOld;
    /**
     * Most recently read item from the new array
     */
    private T lastNue;
    /**
     * Flag used to determine if we're processing the final old item
     */
    private boolean oiHadNext = true;
    /**
     * Flag set to true if all processing has been completed during a call to
     * next()
     */
    private boolean done = false;
    /**
     * Amount we will add or subtract on the index of new changes to make sure
     * they reflect the effect of previous changes
     */
    private int offset = 0;
    /**
     * The current in the old array
     */
    private int index = 0;
    /**
     * The current change, which may in range as additional items are found
     */
    private ListDiffChange currChange = null;

    ParallelIterator(List<T> old, List<T> nue) {
        this.old = old;
        this.nue = nue;
        oi = old.iterator();
        ni = nue.iterator();
    }

    private void go() {
        if (oi == null) {
            throw new IllegalStateException("Cannot reuse");
        }
        //Optimizations
        if (old.isEmpty() && nue.isEmpty()) {
            //Both empty, no diff
            oi = null;
            ni = null;

        } else if (!old.isEmpty() && nue.isEmpty()) {
            //New is empty - one big deletion
            ListDiffChange change = new ListDiffChange(0, DELETE);
            change.setEnd(old.size() - 1);
            changes.add(change);
            oi = null;
            ni = null;

        } else if (old.isEmpty() && !nue.isEmpty()) {
            //Old is empty - one big addition
            ListDiffChange change = new ListDiffChange(0, INSERT);
            change.setEnd(nue.size() - 1);
            changes.add(change);
            oi = null;
            ni = null;

        } else {
            ensureInit();
            while (hasNext()) {
                next();
            }
            done();
        }
    }

    /**
     * See if we've processed all items in both arrays
     */
    private boolean hasNext() {
        //We have another item even if both iterators are done, if
        //the handled() has not been called for the last read items
        return !done && ((oi.hasNext() || ni.hasNext())
                || (lastOld != null || lastNue != null));
    }

    /**
     * Called when an item has been processed with that item. Will fetch the
     * next item into lastOld/lastNew or null it out if done.
     */
    private void handled(T o, List<T> src) {
        if (src == old) {
            lastOld = null;
            if (oi.hasNext()) {
                lastOld = oi.next();
                index++;
            }
        } else {
            lastNue = null;
            if (ni.hasNext()) {
                lastNue = ni.next();
            }
        }
    }

    /**
     * Handle the next items in the arrays
     */
    private void next() {
        //Flags if there are more items - we will put them into
        //oiHadNext/niHadNext at the end and use them to determine if
        //we're on the last item, which requires special handling
        boolean oiNext = oi.hasNext();
        boolean niNext = ni.hasNext();

        //See if the current items are equal
        boolean match = lastOld != null && lastNue != null && lastOld.equals(lastNue);
        if (match) {
            writeChange();
            handled(lastOld, old);
            handled(lastNue, nue);
        } else {
            //Make sure hash sets created
            ensureSets();
            //See who knows about what
            boolean nueHasIt = h_new.contains(lastOld);
            boolean oldHasIt = h_old.contains(lastNue);

            if (lastNue == null && lastOld != null) {
                //We're off the end of the new array, handle trailing deletions and finish
                writeChange();
                ListDiffChange last = new ListDiffChange(index + offset,
                        (old.size() - 1) + offset, DELETE);
                currChange = last;
                done = true;

            } else if (lastOld == null && lastNue != null) {
                //We're off the end of the old array, handle trailing insertions and finish
                for (int i = index + 1; i < (nue.size() - offset); i++) {
                    //TODO : Don't need a loop to do this
                    addChange(INSERT, i);
                }
                done = true;

            } else if (nueHasIt && !oldHasIt) {
                //Not done, not in the old array - an insertion
                addChange(INSERT, index);
                handled(lastNue, nue);

            } else if (!nueHasIt && oldHasIt) {
                //Not done, not in the new array - a deletion
                addChange(DELETE, index);
                handled(lastOld, old);

            } else if (nueHasIt && oldHasIt) {
                //Not done, occurs in both arrays - a change
                addChange(CHANGE, index);
                handled(lastOld, old);
                handled(lastNue, nue);

            } else if (!nueHasIt && !oldHasIt) {
                //Not in either array - a change, or we may be almost done
                if (oiNext || (!oiNext && oiHadNext)) { //Next to last or last element
                    //Add a change
                    addChange(CHANGE, index);
                    handled(lastOld, old);
                    handled(lastNue, nue);
                    //If we're done, we won't be back - run out to the end to
                    //handle any remaining stuff

                    if (!oiNext) {
                        //Handle trailing insertions
                        for (int i = index + 1; i < (nue.size() - offset); i++) {
                            //TODO : Don't need a loop to do this
                            addChange(INSERT, i);
                        }
                        //we're done
                        done = true;
                    } else if (!niNext) {
                        //Handle trailing deletions
                        for (int i = index; i < (old.size() - offset); i++) {
                            //TODO : Don't need a loop to do this
                            addChange(DELETE, i);
                        }
                        //We're done
                        done = true;
                    }
                }
            }
        }
        //Update the flags
        oiHadNext = oiNext;
    }

    /**
     * Ensure the HashSets used to check for containment are created
     */
    private void ensureSets() {
        if (h_old == null) {
            h_old = new HashSet<>(old);
            h_new = new HashSet<>(nue);
            if (h_old.size() != old.size()) {
                throw new IllegalStateException("Duplicate elements - "
                        + "size of list does not match size of equivalent "
                        + "HashSet " + identifyDuplicates(old));
            }
            if (h_new.size() != nue.size()) {
                throw new IllegalStateException("Duplicate elements - "
                        + "size of list does not match size of equivalent "
                        + "HashSet " + identifyDuplicates(nue));
            }
        }
    }

    /**
     * If there are duplicate elements either the list, an exception will be
     * thrown. Get a diagnostic string saying what was duplicated, for
     * debugging.
     */
    private String identifyDuplicates(List<T> l) {
        HashMap<T, Integer> map = new HashMap<>();
        for (T o : l) {
            Integer count = map.get(o);
            if (count == null) {
                count = 1;
            } else {
                count += 1;
            }
            map.put(o, count);
        }
        StringBuilder sb = new StringBuilder("Duplicates: "); //NOI18N
        for (T key : map.keySet()) {
            Integer ct = map.get(key);
            if (ct > 1) {
                sb.append("[").append(ct.intValue()).append(//NOI18N
                        " occurances of ").append(key).append("]"); //NOI18N
            }
        }
        return sb.toString();
    }

    /**
     * Populates the initial values of lastOld and lastNue
     */
    private void ensureInit() {
        if (lastOld == null) {
            lastOld = oi.next();
        }
        if (lastNue == null) {
            lastNue = ni.next();
        }
    }

    /**
     * Called when all processing has been completed to write any pending
     * changes from processing and clear state
     */
    private void done() {
        writeChange();
        currChange = null;
        oi = null;
        ni = null;
        h_old = null;
        h_new = null;
    }

    /**
     * Get the list of changes
     */
    List<? extends ListDifference> getChanges() {
        if (oi != null) {
            go();
        }
        return Collections.unmodifiableList(crystallize(changes));
    }

    /**
     * Adds a change to the current change if the current change's type is the
     * same (grow the current change), or write it and create a new Change
     * object if it has grown.
     */
    private void addChange(int type, int idx) {
        if (currChange == null) {
            currChange = new ListDiffChange(idx + offset, type);
        } else {
            if (currChange.getType() == type) {
                currChange.inc();
            } else {
                writeChange();
                currChange = new ListDiffChange(idx + offset, type);
            }
        }
    }

    /**
     * If any pending change, store it
     */
    private void writeChange() {
        if (currChange != null) {
            changes.add(currChange);
            int type = currChange.getType();
            if (type == INSERT) {
                offset += (currChange.getEnd() - currChange.getStart() + 1);
            } else if (type == DELETE) {
                offset -= currChange.getEnd() - currChange.getStart() + 1;
            }
            assert currChange.getStart() <= currChange.getEnd() :
                    "Start must be > end - " + currChange.getStart()
                    + " < " + currChange.getEnd();
            currChange = null;
        }
    }
    
    private List<ListDifference> crystallize(Collection<? extends ListDiffChange> changes) {
        List<ListDifference> result = new ArrayList<>(changes.size());
        for (ListDiffChange ch : changes) {
            result.add(ch.crystallize());
        }
        return result;
    }

    /*
     * Change.java
     *
     * Created on September 18, 2004, 6:42 PM
     */
    /**
     * Immutable class representing a single transformation to a data range in a
     * list indicating the addition, removal or modification of a range of
     * indices.
     *
     * @author Tim Boudreau
     * @see Diff
     */
    final class ListDiffChange implements Difference<List<?>> {

        private final int type;
        private final int start;
        private int end;

        /**
         * Create a new Change object with the given start, end and type
         */
        ListDiffChange(int start, int end, int type) {
            this.type = type;
            this.start = start;
            this.end = end;

            //Sanity check
            if (end < start) {
                throw new IllegalArgumentException("Start " + start //NOI18N
                        + " > " + end); //NOI18N
            }
            if (end < 0 || start < 0) {
                throw new IllegalArgumentException("Negative start "
                        + //NOI18N
                        start + " or end " + end); //NOI18N
            }
            if (type != DELETE && type != CHANGE && type != INSERT) {
                throw new IllegalArgumentException("Unknown change type " + type); //NOI18N
            }
        }

        /**
         * Constructor used by ListDiff
         */
        ListDiffChange(int start, int type) {
            this.start = start;
            end = start + 1;
            this.type = type;
            assert (type == DELETE || type == CHANGE || type == INSERT) : ""
                    + type;
        }
        
        ListDifference crystallize() {
            return new ListDifference(this);
        }

        /**
         * Grow the endpoint of the Change by one
         */
        void inc() {
            end++;
        }

        /**
         * Set the endpoint of the Change
         */
        void setEnd(int end) {
            assert end >= start;
            this.end = end;
        }

        /**
         * Get the change type
         */
        public int getType() {
            return type;
        }

        /**
         * Get the start index
         *
         * @return the first affected index in the list
         */
        public int getStart() {
            return start;
        }

        /**
         * Get the end index (inclusive)
         *
         * @return the last affected index in the list
         */
        public int getEnd() {
            return end;
        }

        /**
         * Get a string representation of this change.
         *
         * @return a string
         */
        @Override
        public String toString() {

            List<?> olds = oldValue();
            List<?> nues = newValue();
            StringBuilder sb = new StringBuilder(kind().name().toLowerCase())
                    .append('(')
                    .append(start)
                    .append('-')
                    .append(end)
                    .append(" of ")
                    .append(max(old.size(), nue.size()));

            switch (type) {
                case DELETE:
                    sb.append(": <")
                            .append(Strings.join(',', olds))
                            .append('>');
                    break;
                case INSERT:
                    sb.append(": <")
                            .append(Strings.join(',', nues))
                            .append('>');
                    break;
                case CHANGE:
                    sb.append(" from <")
                            .append(Strings.join(',', olds))
                            .append("> to <")
                            .append(Strings.join(',', nues))
                            .append('>');
                    break;
            }
            return sb.append(')').toString();
        }

        private int oldEnd() {
            return Math.min(old.size(), end);
        }

        @Override
        public List<?> oldValue() {
            switch (type) {
                case INSERT:
                    return Collections.emptyList();
                case DELETE:
                case CHANGE:
                    return old.subList(start, oldEnd());
                default:
                    throw new AssertionError(type);
            }
        }

        private int newEnd() {
            return Math.min(nue.size(), end);
        }

        @Override
        public List<?> newValue() {
            switch (type) {
                case DELETE:
                    return Collections.emptyList();
                case INSERT:
                case CHANGE:
                    return nue.subList(start, newEnd());
                default:
                    throw new AssertionError(type);
            }
        }

        @Override
        public DifferenceKind kind() {
            switch (type) {
                case CHANGE:
                    return DifferenceKind.CHANGE;
                case DELETE:
                    return DifferenceKind.DELETION;
                case INSERT:
                    return DifferenceKind.INSERTION;
                default:
                    throw new AssertionError(type);
            }
        }

        <P> void addChildDifferences(Differencer<Object> into, DifferencesBuilder<P> bldr) {
            if (type == CHANGE) {
                List<?> oldValues = this.oldValue();
                List<?> newValues = this.newValue();
                int max = min(oldValues.size(), newValues.size());
                for (int i = 0; i < max; i++) {
                    int index = start + i;
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
}
