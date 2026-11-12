package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.util.Locale;
import java.util.Random;

/** Generation and strict validation of course join codes. */
public final class JoinCodes {

    private JoinCodes() {}

    /** A new code of {@link Constants#JOIN_CODE_LENGTH} characters from the join code alphabet. */
    @NonNull
    public static String generate(@NonNull Random random) {
        final StringBuilder code = new StringBuilder(Constants.JOIN_CODE_LENGTH);
        for (int i = 0; i < Constants.JOIN_CODE_LENGTH; i++) {
            final int index = random.nextInt(Constants.JOIN_CODE_ALPHABET.length());
            code.append(Constants.JOIN_CODE_ALPHABET.charAt(index));
        }
        return code.toString();
    }

    /** What the user typed, trimmed and upper-cased. The result may still be invalid. */
    @NonNull
    public static String normalise(@NonNull String input) {
        return input.trim().toUpperCase(Locale.ROOT);
    }

    /** Whether the text is exactly a join code. Anything else is rejected, never repaired. */
    public static boolean isValid(@NonNull String code) {
        if (code.length() != Constants.JOIN_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (Constants.JOIN_CODE_ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
