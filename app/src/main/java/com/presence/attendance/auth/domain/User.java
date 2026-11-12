package com.presence.attendance.auth.domain;

import androidx.annotation.NonNull;

/** The signed-in person. Name and email are the values in their sign-in token. Immutable. */
public final class User {

    @NonNull private final String uid;
    @NonNull private final String name;
    @NonNull private final String email;
    @NonNull private final Role role;

    public User(
            @NonNull String uid, @NonNull String name, @NonNull String email, @NonNull Role role) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    @NonNull
    public Role getRole() {
        return role;
    }
}
