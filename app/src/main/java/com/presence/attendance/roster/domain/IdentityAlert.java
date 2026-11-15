package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A rejected identity attempt, as shown to faculty. Immutable. */
public final class IdentityAlert {

    /** Wire values are the constant names, as the security rules check them. */
    public enum Type {
        NON_VJTI_EMAIL,
        NOT_ON_REGISTER,
        ROLL_MISMATCH,
        DUPLICATE_CLAIM;

        @Nullable
        public static Type fromWire(@Nullable String value) {
            for (Type type : values()) {
                if (type.name().equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** Audience value for alerts every faculty member should see. */
    public static final String AUDIENCE_ALL = "ALL";

    @NonNull private final String id;
    @NonNull private final Type type;
    @NonNull private final String email;
    @Nullable private final String enteredRollNo;
    private final long createdAtMillis;

    public IdentityAlert(
            @NonNull String id,
            @NonNull Type type,
            @NonNull String email,
            @Nullable String enteredRollNo,
            long createdAtMillis) {
        this.id = id;
        this.type = type;
        this.email = email;
        this.enteredRollNo = enteredRollNo;
        this.createdAtMillis = createdAtMillis;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    /** The address that made the attempt, from its sign-in token. */
    @NonNull
    public String getEmail() {
        return email;
    }

    /** The roll number that was typed, or null for an attempt that never reached the form. */
    @Nullable
    public String getEnteredRollNo() {
        return enteredRollNo;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
