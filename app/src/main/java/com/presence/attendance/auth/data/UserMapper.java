package com.presence.attendance.auth.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.data.Snapshots;
import java.util.HashMap;
import java.util.Map;

/** Explicit mapping between {@code users/{uid}} documents and {@link User}. */
final class UserMapper {

    static final String NAME = "name";
    static final String EMAIL = "email";
    static final String ROLE = "role";
    private static final String CREATED_AT = "createdAt";

    private UserMapper() {}

    /** The user in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    static User fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        final String name = Snapshots.string(snapshot, NAME);
        final String email = Snapshots.string(snapshot, EMAIL);
        final Role role = Role.fromWire(Snapshots.string(snapshot, ROLE));
        if (name == null || email == null || role == null) {
            return null;
        }
        return new User(snapshot.getId(), name, email, role);
    }

    @NonNull
    static Map<String, Object> newDocument(
            @NonNull String name, @NonNull String email, @NonNull Role role) {
        final Map<String, Object> document = new HashMap<>();
        document.put(NAME, name);
        document.put(EMAIL, email);
        document.put(ROLE, role.name());
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }
}
