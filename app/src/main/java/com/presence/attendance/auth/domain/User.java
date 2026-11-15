package com.presence.attendance.auth.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The signed-in person. Name and email are the values in their sign-in token; a student also has
 * the roll number and cohort the roster holds for that email. Immutable.
 */
public final class User {

    @NonNull private final String uid;
    @NonNull private final String name;
    @NonNull private final String email;
    @NonNull private final Role role;
    @Nullable private final String rollNo;
    @Nullable private final String cohort;

    /** A faculty member. */
    public User(
            @NonNull String uid, @NonNull String name, @NonNull String email, @NonNull Role role) {
        this(uid, name, email, role, null, null);
    }

    /** A registered student has both {@code rollNo} and {@code cohort}. */
    public User(
            @NonNull String uid,
            @NonNull String name,
            @NonNull String email,
            @NonNull Role role,
            @Nullable String rollNo,
            @Nullable String cohort) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.rollNo = rollNo;
        this.cohort = cohort;
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

    /** The student's roster roll number; null for faculty. */
    @Nullable
    public String getRollNo() {
        return rollNo;
    }

    /** The student's roster cohort; null for faculty. */
    @Nullable
    public String getCohort() {
        return cohort;
    }
}
