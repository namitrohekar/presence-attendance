package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.Constants;

/**
 * The text inside the QR: {@code prs1:<sessionId>:<token>}. Parsing is strict: anything that is not
 * exactly this shape is rejected, never repaired. Immutable.
 */
public final class QrPayload {

    private static final char SEPARATOR = ':';
    private static final int PARTS = 3;
    private static final int SESSION_ID_MAX = 128;

    @NonNull private final String sessionId;
    @NonNull private final String token;

    private QrPayload(@NonNull String sessionId, @NonNull String token) {
        this.sessionId = sessionId;
        this.token = token;
    }

    /** The QR text for a session and one of its tokens. */
    @NonNull
    public static String build(@NonNull String sessionId, @NonNull String token) {
        if (!isSessionId(sessionId) || !Tokens.isValid(token)) {
            throw new IllegalArgumentException("Not a valid session id and token");
        }
        return Constants.QR_PREFIX + SEPARATOR + sessionId + SEPARATOR + token;
    }

    /** The payload in scanned text, or null if the text is anything else. */
    @Nullable
    public static QrPayload parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        final String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        final boolean valid =
                parts.length == PARTS
                        && parts[0].equals(Constants.QR_PREFIX)
                        && isSessionId(parts[1])
                        && Tokens.isValid(parts[2]);
        return valid ? new QrPayload(parts[1], parts[2]) : null;
    }

    /** A Firestore auto id: letters and digits only, so it is safe inside a document path. */
    private static boolean isSessionId(@NonNull String id) {
        if (id.isEmpty() || id.length() > SESSION_ID_MAX) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @NonNull
    public String getToken() {
        return token;
    }
}
