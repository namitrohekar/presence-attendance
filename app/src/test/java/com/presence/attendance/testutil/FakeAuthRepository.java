package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;

/** In-memory {@link AuthRepository}. Answers synchronously with whatever the test set. */
public final class FakeAuthRepository implements AuthRepository {

    public Outcome<User> userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
    public Outcome<User> signInOutcome = Outcome.failure(AppError.SIGN_IN_FAILED);
    public Outcome<User> registerOutcome = Outcome.failure(AppError.NOT_ON_ROSTER);
    public boolean sessionExists;
    public String email;
    public String lastIdToken;
    public String lastRollNo;
    public boolean signedOut;

    public void signedInAs(@NonNull User user) {
        userOutcome = Outcome.success(user);
        sessionExists = true;
        email = user.getEmail();
    }

    @Override
    public boolean isSignedIn() {
        return sessionExists;
    }

    @Nullable
    @Override
    public String signedInEmail() {
        return email;
    }

    @Override
    public void signInWithGoogle(@NonNull String googleIdToken, @NonNull Callback<User> callback) {
        lastIdToken = googleIdToken;
        callback.onResult(signInOutcome);
    }

    @Override
    public void loadUser(@NonNull Callback<User> callback) {
        callback.onResult(userOutcome);
    }

    @Override
    public void register(@NonNull String enteredRollNo, @NonNull Callback<User> callback) {
        lastRollNo = enteredRollNo;
        callback.onResult(registerOutcome);
    }

    @Override
    public void signOut() {
        signedOut = true;
        sessionExists = false;
        userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
    }
}
