package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;

/** In-memory {@link AuthRepository}. Answers synchronously with whatever the test set. */
public final class FakeAuthRepository implements AuthRepository {

    public Outcome<User> userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
    public Outcome<User> signInOutcome = Outcome.failure(AppError.SIGN_IN_FAILED);
    public String lastIdToken;
    public boolean signedOut;

    public void signedInAs(@NonNull User user) {
        userOutcome = Outcome.success(user);
    }

    @Override
    public boolean isSignedIn() {
        return userOutcome.isSuccess();
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
    public void signOut() {
        signedOut = true;
        userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
    }
}
