package com.presence.attendance.auth.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;

/**
 * State machine of the sign-in screen: IDLE -> SIGNING_IN -> SIGNED_IN or NEEDS_REGISTRATION, or
 * back to IDLE with the reason.
 */
public final class SignInViewModel extends ViewModel {

    public enum Status {
        IDLE,
        SIGNING_IN,
        SIGNED_IN,
        /** A student address that has not linked a roll number yet. */
        NEEDS_REGISTRATION
    }

    /** Immutable screen state. {@code error} is only ever set together with IDLE. */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final AppError error;

        State(@NonNull Status status, @Nullable AppError error) {
            this.status = status;
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Status.IDLE, null));

    public SignInViewModel(@NonNull AuthRepository auth) {
        this.auth = auth;
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** The user tapped the button and Google's account sheet is opening. */
    public void onSignInStarted() {
        state.setValue(new State(Status.SIGNING_IN, null));
    }

    /** Google's account sheet closed, with an ID token or the reason there is none. */
    public void onGoogleIdToken(@NonNull Outcome<String> idToken) {
        if (!idToken.isSuccess()) {
            // Closing the sheet is a choice, not a failure, so it shows no message.
            final AppError error = idToken.getError();
            state.setValue(
                    new State(Status.IDLE, error == AppError.SIGN_IN_CANCELLED ? null : error));
            return;
        }
        auth.signInWithGoogle(
                idToken.getValue(),
                outcome -> {
                    if (outcome.isSuccess()) {
                        state.setValue(new State(Status.SIGNED_IN, null));
                    } else if (outcome.getError() == AppError.REGISTRATION_REQUIRED) {
                        state.setValue(new State(Status.NEEDS_REGISTRATION, null));
                    } else {
                        state.setValue(new State(Status.IDLE, outcome.getError()));
                    }
                });
    }
}
