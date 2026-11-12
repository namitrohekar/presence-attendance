package com.presence.attendance.auth.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Wire values are the constant names, as enforced by the security rules. */
public enum Role {
    FACULTY,
    STUDENT;

    /** The role for a stored value, or null if the value is not a known role. */
    @Nullable
    public static Role fromWire(@Nullable String value) {
        for (Role role : values()) {
            if (role.name().equals(value)) {
                return role;
            }
        }
        return null;
    }

    @NonNull
    public Role other() {
        return this == FACULTY ? STUDENT : FACULTY;
    }
}
