package com.presence.attendance.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Strict field readers for the explicit Firestore mappers. A field of the wrong type reads as null,
 * so a malformed document is rejected by its mapper instead of throwing or being coerced.
 */
public final class Snapshots {

    private Snapshots() {}

    @Nullable
    public static String string(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        final Object value = snapshot.get(field);
        return value instanceof String ? (String) value : null;
    }

    @Nullable
    public static Timestamp timestamp(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        final Object value = snapshot.get(field);
        return value instanceof Timestamp ? (Timestamp) value : null;
    }

    @Nullable
    public static Long integer(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        final Object value = snapshot.get(field);
        return value instanceof Long ? (Long) value : null;
    }
}
