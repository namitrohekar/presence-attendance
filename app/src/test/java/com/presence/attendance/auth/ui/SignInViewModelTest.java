package com.presence.attendance.auth.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.auth.ui.SignInViewModel.State;
import com.presence.attendance.auth.ui.SignInViewModel.Status;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.Fixtures;
import org.junit.Rule;
import org.junit.Test;

public final class SignInViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final SignInViewModel viewModel = new SignInViewModel(auth);

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void startsIdleWithoutAnError() {
        assertEquals(Status.IDLE, state().status);
        assertNull(state().error);
    }

    @Test
    public void tappingTheButtonShowsProgress() {
        viewModel.onSignInStarted();
        assertEquals(Status.SIGNING_IN, state().status);
    }

    @Test
    public void aGoogleTokenIsExchangedAndEndsSignedIn() {
        auth.signInOutcome = Outcome.success(Fixtures.STUDENT);
        viewModel.onSignInStarted();
        viewModel.onGoogleIdToken(Outcome.success("google-id-token"));
        assertEquals("google-id-token", auth.lastIdToken);
        assertEquals(Status.SIGNED_IN, state().status);
    }

    @Test
    public void closingTheAccountSheetReturnsToIdleWithoutAMessage() {
        viewModel.onSignInStarted();
        viewModel.onGoogleIdToken(Outcome.failure(AppError.SIGN_IN_CANCELLED));
        assertEquals(Status.IDLE, state().status);
        assertNull(state().error);
        assertNull("nothing was sent to Firebase", auth.lastIdToken);
    }

    @Test
    public void aCredentialFailureIsShown() {
        viewModel.onSignInStarted();
        viewModel.onGoogleIdToken(Outcome.failure(AppError.NO_GOOGLE_ACCOUNT));
        assertEquals(Status.IDLE, state().status);
        assertEquals(AppError.NO_GOOGLE_ACCOUNT, state().error);
    }

    @Test
    public void aRejectedAccountIsShownAndStaysOnSignIn() {
        auth.signInOutcome = Outcome.failure(AppError.NOT_VJTI_ACCOUNT);
        viewModel.onSignInStarted();
        viewModel.onGoogleIdToken(Outcome.success("google-id-token"));
        assertEquals(Status.IDLE, state().status);
        assertEquals(AppError.NOT_VJTI_ACCOUNT, state().error);
    }

    @Test
    public void aStudentWhoHasNotRegisteredIsSentToRegistrationNotShownAnError() {
        auth.signInOutcome = Outcome.failure(AppError.REGISTRATION_REQUIRED);
        viewModel.onSignInStarted();
        viewModel.onGoogleIdToken(Outcome.success("google-id-token"));
        assertEquals(Status.NEEDS_REGISTRATION, state().status);
        assertNull(state().error);
    }

    @Test
    public void retryingClearsThePreviousError() {
        viewModel.onGoogleIdToken(Outcome.failure(AppError.OFFLINE));
        viewModel.onSignInStarted();
        assertNull(state().error);
    }
}
