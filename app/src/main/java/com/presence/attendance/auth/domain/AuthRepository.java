package com.presence.attendance.auth.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.core.Callback;

/** Sign-in state and the signed-in user's profile. */
public interface AuthRepository {

    /** Whether a sign-in session exists on this device. Does not touch the network. */
    boolean isSignedIn();

    /**
     * Signs in with a Google ID token and writes the user's profile. The role in the result is the
     * one the security rules accepted, never one the app chose.
     */
    @MainThread
    void signInWithGoogle(@NonNull String googleIdToken, @NonNull Callback<User> callback);

    /**
     * Loads the signed-in user's profile, creating it if it is missing. Fails with {@code
     * UNAUTHENTICATED} when nobody is signed in.
     */
    @MainThread
    void loadUser(@NonNull Callback<User> callback);

    @MainThread
    void signOut();
}
