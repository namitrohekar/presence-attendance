package com.presence.attendance.core.ui;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The initials shown on a person's disc in a list, like a directory's monogram: the first letter of
 * the first and of the last word of their name, "AP" for Aditi Pawar. Titles such as "Dr." are
 * skipped, so "Dr. Archana G. Pai" is "AP". Also used to suggest a teacher's initials.
 */
public final class Monogram {

    /** Titles that come before a name and are not part of the initials. */
    private static final List<String> TITLES =
            Arrays.asList("dr", "prof", "mr", "mrs", "ms", "miss", "shri", "smt", "sir");

    private Monogram() {}

    /** A title such as "Dr." or "Prof", without regard to case or the dot. */
    public static boolean isTitle(@NonNull String word) {
        return TITLES.contains(word.replace(".", "").toLowerCase(Locale.ROOT));
    }

    /** At most two letters, upper case; empty if the text has no letters. */
    @NonNull
    public static String of(@NonNull String name) {
        final List<String> words = new ArrayList<>();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && !isTitle(word) && firstLetter(word) != null) {
                words.add(word);
            }
        }
        if (words.isEmpty()) {
            return "";
        }
        final StringBuilder initials = new StringBuilder(firstLetter(words.get(0)));
        if (words.size() > 1) {
            initials.append(firstLetter(words.get(words.size() - 1)));
        }
        return initials.toString().toUpperCase(Locale.ROOT);
    }

    /** The first letter of a word as a whole code point, or null if it has none. */
    private static String firstLetter(@NonNull String word) {
        for (int i = 0; i < word.length(); ) {
            final int codePoint = word.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                return new String(Character.toChars(codePoint));
            }
            i += Character.charCount(codePoint);
        }
        return null;
    }
}
