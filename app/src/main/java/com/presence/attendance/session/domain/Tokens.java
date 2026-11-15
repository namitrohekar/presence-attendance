package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.security.SecureRandom;
import java.util.Base64;

/** One-time tokens shown in the rotating QR. Unguessable: 128 random bits each. */
public final class Tokens {

    private static final String ALPHABET_EXTRA = "_-";

    private Tokens() {}

    /**
     * {@link Constants#TOKEN_BYTES} random bytes as base64url without padding, which is {@link
     * Constants#TOKEN_LENGTH} characters and safe as a Firestore document id.
     */
    @NonNull
    public static String generate(@NonNull SecureRandom random) {
        final byte[] bytes = new byte[Constants.TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Exactly a token: the right length, base64url characters only. */
    public static boolean isValid(@NonNull String token) {
        if (token.length() != Constants.TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            final char c = token.charAt(i);
            final boolean allowed =
                    (c >= 'A' && c <= 'Z')
                            || (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || ALPHABET_EXTRA.indexOf(c) >= 0;
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
