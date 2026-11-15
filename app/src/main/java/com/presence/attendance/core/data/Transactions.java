package com.presence.attendance.core.data;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

/** Transaction helpers shared by the repositories. */
public final class Transactions {

    private Transactions() {}

    /**
     * Runs a transaction and, if the security rules refuse it, runs it once more.
     *
     * <p>When two clients race, the loser's write is evaluated against the winner's committed data
     * and is denied by the rules rather than retried by the SDK. The denial carries no reason, so
     * the body is run again: the second run reads the new state and returns the precise outcome
     * without writing. Mirrors {@code decideAgainIfDenied} in the rules suite's {@code flows.js}.
     */
    @NonNull
    public static <T> Task<T> runDecidingAgainIfDenied(
            @NonNull FirebaseFirestore firestore, @NonNull Transaction.Function<T> body) {
        return firestore
                .runTransaction(body)
                .continueWithTask(first -> {
                    final Exception error = first.getException();
                    final boolean denied =
                            error instanceof FirebaseFirestoreException
                                    && ((FirebaseFirestoreException) error).getCode()
                                            == FirebaseFirestoreException.Code.PERMISSION_DENIED;
                    return denied ? firestore.runTransaction(body) : first;
                });
    }
}
