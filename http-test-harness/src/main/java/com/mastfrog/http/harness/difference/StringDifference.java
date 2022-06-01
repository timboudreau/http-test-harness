
package com.mastfrog.http.harness.difference;

import com.mastfrog.function.IntTriConsumer;
import static com.mastfrog.util.strings.Escaper.NEWLINES_AND_OTHER_WHITESPACE;
import com.mastfrog.util.strings.LevenshteinDistance;
import com.mastfrog.util.strings.Strings;
import static java.lang.Integer.max;
import static java.lang.Math.min;
import java.text.NumberFormat;

/**
 * Computes detailed differences of small strings - not quite a diff algorithm,
 * but finds start and end of differences and can elide text to make a readable
 * indication of what changed.
 *
 * @author Tim Boudreau
 */
final class StringDifference implements Difference<CharSequence> {

    private static final char ELLIPSIS = '\u2026';
    private static final String ELLIPSIS_STRING = Character.toString(ELLIPSIS);
    private static final int STRING_LENGTH_LIMIT = 32;
    private final CharSequence old;
    private final CharSequence nue;

    StringDifference(CharSequence a, CharSequence b) {
        this.old = a;
        this.nue = b;
    }

    @Override
    public CharSequence oldValue() {
        return old;
    }

    @Override
    public CharSequence newValue() {
        return nue;
    }

    @Override
    public DifferenceKind kind() {
        return DifferenceKind.CHANGE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("change(levenshtein=");

        withDifferencesStartAndEnd((firstDifference, lastDifferenceOld, lastDifferenceNew) -> {

            float score = LevenshteinDistance.score(old.toString(), nue.toString(), true);
            sb.append(NumberFormat.getNumberInstance().format(score)).append(" '");

            CharSequence oldTailExemplar, newTailExemplar, oldExemplar, newExemplar;
            if (lastDifferenceOld == old.length() - 1) {
                oldTailExemplar = old;
            } else {
                oldTailExemplar = old.subSequence(0, min(old.length(), lastDifferenceOld + 2));
            }
            if (lastDifferenceNew == nue.length() - 1) {
                newTailExemplar = nue;
            } else {
                newTailExemplar = nue.subSequence(0, min(nue.length(), lastDifferenceNew + 2));
            }

            // Include at least one character of shared context in elided text
            if (firstDifference == 0) {
                oldExemplar = Strings.elide(oldTailExemplar, STRING_LENGTH_LIMIT, ELLIPSIS_STRING);
                newExemplar = Strings.elide(newTailExemplar, STRING_LENGTH_LIMIT, ELLIPSIS_STRING);
            } else {
                oldExemplar = ELLIPSIS_STRING
                        + Strings.elide(
                                oldTailExemplar.subSequence(
                                        max(0, firstDifference - 2), lastDifferenceOld + 1),
                                STRING_LENGTH_LIMIT);
                newExemplar = ELLIPSIS_STRING
                        + Strings.elide(newTailExemplar.subSequence(
                                max(0, firstDifference - 2), lastDifferenceNew + 1),
                                STRING_LENGTH_LIMIT);

            }
            oldExemplar = Strings.escape(oldExemplar, NEWLINES_AND_OTHER_WHITESPACE);
            newExemplar = Strings.escape(newExemplar, NEWLINES_AND_OTHER_WHITESPACE);

            if (lastDifferenceOld != old.length() - 1) {
                oldExemplar += ELLIPSIS_STRING;
            }
            if (lastDifferenceNew != nue.length() - 1) {
                newExemplar += ELLIPSIS_STRING;
            }

            sb.append(oldExemplar).append("' -> '").append(newExemplar).append("'");
            if (old.length() != nue.length()) {
                sb.append(" \u0394length ").append(nue.length() - old.length());
            }
            if (firstDifference != 0) {
                sb.append(" @").append(firstDifference);
            }
            if (lastDifferenceOld != old.length() - 1 || lastDifferenceNew != nue.length() - 1) {
                sb.append(" thru ")
                        .append(lastDifferenceOld).append('/').append(lastDifferenceNew);
            }
            // If we elided anything, print out the full text of each on
            // separate lines for visual comparison
            if (Strings.contains(ELLIPSIS, oldExemplar) || Strings.contains(ELLIPSIS, newExemplar)) {
                sb.append("\n\tWas: '").append(Strings.escape(old,
                        NEWLINES_AND_OTHER_WHITESPACE));
                sb.append("'\n\tNow: '").append(Strings.escape(nue,
                        NEWLINES_AND_OTHER_WHITESPACE))
                        .append('\'');
            }
        });
        return sb.append(")\n").toString();
    }

    void withDifferencesStartAndEnd(IntTriConsumer bc) {
        int first = 0;
        int maxTraverse = Math.min(old.length(), nue.length());
        for (int i = 0; i < maxTraverse; i++) {
            char a = old.charAt(i);
            char b = nue.charAt(i);
            if (a != b) {
                break;
            }
            first++;
        }
        for (int i = 1; i <= maxTraverse; i++) {
            int o = old.length() - i;
            int n = nue.length() - i;
            char a = old.charAt(o);
            char b = nue.charAt(n);
            if (a != b) {
                bc.accept(first, o, n);
                break;
            }
        }
    }
}
