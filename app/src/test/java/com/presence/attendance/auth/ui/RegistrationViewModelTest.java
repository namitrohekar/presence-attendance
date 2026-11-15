package com.presence.attendance.auth.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.auth.ui.RegistrationViewModel.State;
import com.presence.attendance.auth.ui.RegistrationViewModel.Step;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.Fixtures;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class RegistrationViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private RegistrationViewModel viewModel;

    @Before
    public void setUp() {
        auth.sessionExists = true;
        auth.email = Fixtures.STUDENT.getEmail();
        auth.registerOutcome = Outcome.success(Fixtures.STUDENT);
        viewModel = new RegistrationViewModel(auth, online);
        // The connectivity source only feeds the state while something observes it.
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void theAddressShownIsTheSignedInAccounts() {
        assertEquals(Fixtures.STUDENT.getEmail(), state().email);
        assertEquals(Step.FORM, state().step);
    }

    @Test
    public void aMatchingRollNumberRegisters() {
        viewModel.submit(" 262011001 ");
        assertEquals("input is trimmed", "262011001", auth.lastRollNo);
        assertEquals(Step.REGISTERED, state().step);
    }

    @Test
    public void aMalformedRollNumberNeverReachesTheServer() {
        for (String typed : new String[] {"", "12345", "2620110011", "26201100A", "262 11001"}) {
            viewModel.submit(typed);
            assertTrue(typed, state().rollNoInvalid);
        }
        assertNull(auth.lastRollNo);
    }

    @Test
    public void eachRejectionIsShownWithItsOwnReasonAndCanBeRetried() {
        for (AppError reason :
                new AppError[] {
                    AppError.NOT_ON_ROSTER, AppError.ROLL_MISMATCH, AppError.DUPLICATE_CLAIM
                }) {
            auth.registerOutcome = Outcome.failure(reason);
            viewModel.submit("262011001");
            assertEquals(reason, state().error);
            assertEquals(Step.FORM, state().step);
            assertFalse(state().busy);
        }
        auth.registerOutcome = Outcome.success(Fixtures.STUDENT);
        viewModel.submit("262011001");
        assertEquals(Step.REGISTERED, state().step);
    }

    @Test
    public void offlineBlocksRegistration() {
        online.setValue(false);
        viewModel.submit("262011001");
        assertEquals(AppError.OFFLINE, state().error);
        assertNull("nothing is queued for later", auth.lastRollNo);
    }

    @Test
    public void aLostSessionReturnsToSignIn() {
        auth.registerOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        viewModel.submit("262011001");
        assertEquals(Step.SIGNED_OUT, state().step);
    }

    @Test
    public void usingADifferentAccountSignsOut() {
        viewModel.signOut();
        assertTrue(auth.signedOut);
        assertEquals(Step.SIGNED_OUT, state().step);
    }

    @Test
    public void openedWithoutASessionGoesStraightBackToSignIn() {
        final FakeAuthRepository nobody = new FakeAuthRepository();
        final RegistrationViewModel orphan = new RegistrationViewModel(nobody, online);
        assertEquals(Step.SIGNED_OUT, orphan.getState().getValue().step);
    }
}
