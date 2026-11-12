package com.presence.attendance.core.data;

import static org.junit.Assert.assertEquals;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.presence.attendance.core.AppError;
import org.junit.Test;

public final class FirebaseErrorsTest {

    @Test
    public void firestoreCodesMapToTheErrorsTheAppActsOn() {
        // The offline queue (Phase 5) depends on these two meaning "not delivered, try later".
        assertEquals(AppError.OFFLINE, FirebaseErrors.fromFirestoreCode(Code.UNAVAILABLE));
        assertEquals(AppError.TIMEOUT, FirebaseErrors.fromFirestoreCode(Code.DEADLINE_EXCEEDED));
        assertEquals(
                AppError.PERMISSION_DENIED,
                FirebaseErrors.fromFirestoreCode(Code.PERMISSION_DENIED));
        assertEquals(
                AppError.UNAUTHENTICATED, FirebaseErrors.fromFirestoreCode(Code.UNAUTHENTICATED));
    }

    @Test
    public void everyOtherFirestoreCodeFailsClosedToUnknown() {
        for (Code code : Code.values()) {
            final boolean mapped =
                    code == Code.UNAVAILABLE
                            || code == Code.DEADLINE_EXCEEDED
                            || code == Code.PERMISSION_DENIED
                            || code == Code.UNAUTHENTICATED;
            if (!mapped) {
                assertEquals(code.name(), AppError.UNKNOWN, FirebaseErrors.fromFirestoreCode(code));
            }
        }
    }

    @Test
    public void exceptionsAreMappedByType() {
        assertEquals(
                AppError.PERMISSION_DENIED,
                FirebaseErrors.toAppError(
                        new FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)));
        assertEquals(
                AppError.OFFLINE, FirebaseErrors.toAppError(new FirebaseNetworkException("down")));
        assertEquals(AppError.UNKNOWN, FirebaseErrors.toAppError(new IllegalStateException("x")));
        assertEquals(AppError.UNKNOWN, FirebaseErrors.toAppError(null));
    }
}
