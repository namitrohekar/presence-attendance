package com.presence.attendance.auth.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.Callback;

/** Sign-in state, the signed-in user's profile, and student registration. */
public interface AuthRepository {

    /** Whether a sign-in session exists on this device. Does not touch the network. */
    boolean isSignedIn();

    /** The signed-in account's address, for display. Null when nobody is signed in. */
    @Nullable
    String signedInEmail();

    /**
     * Signs in with a Google ID token and resolves the user. The role is the one the security rules
     * accepted, never one the app chose.
     *
     * <p>Fails with {@code REGISTRATION_REQUIRED} for a student address that has not registered
     * (the session is kept), and with {@code NOT_VJTI_ACCOUNT} for any other account that is not
     * faculty (the session is ended).
     */
    @MainThread
    void signInWithGoogle(@NonNull String googleIdToken, @NonNull Callback<User> callback);

    /**
     * Resolves the signed-in user, with the same outcomes as {@link #signInWithGoogle}. Fails with
     * {@code UNAUTHENTICATED} when nobody is signed in.
     */
    @MainThread
    void loadUser(@NonNull Callback<User> callback);

    /**
     * Registers the signed-in student with the roll number they typed. The address comes from the
     * sign-in token. Fails with {@code NOT_ON_ROSTER}, {@code ROLL_MISMATCH} or {@code
     * DUPLICATE_CLAIM}; each rejection is also reported to faculty.
     */
    @MainThread
    void register(@NonNull String enteredRollNo, @NonNull Callback<User> callback);

    @MainThread
    void signOut();
}
