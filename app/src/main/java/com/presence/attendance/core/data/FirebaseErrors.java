package com.presence.attendance.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Log;

/** The one place where Firebase exceptions become {@link AppError}s. */
public final class FirebaseErrors {

    private static final String TAG = "FirebaseErrors";

    private FirebaseErrors() {}

    /** Maps a failed Task's exception. Unknown and null exceptions fail closed to UNKNOWN. */
    @NonNull
    public static AppError toAppError(@Nullable Exception error) {
        Log.w(TAG, "Firebase call failed", error);
        if (error instanceof FirebaseFirestoreException) {
            return fromFirestoreCode(((FirebaseFirestoreException) error).getCode());
        }
        if (error instanceof FirebaseNetworkException) {
            return AppError.OFFLINE;
        }
        if (error instanceof FirebaseAuthException) {
            return AppError.SIGN_IN_FAILED;
        }
        return AppError.UNKNOWN;
    }

    @NonNull
    public static AppError fromFirestoreCode(@NonNull FirebaseFirestoreException.Code code) {
        return switch (code) {
            case UNAVAILABLE -> AppError.OFFLINE;
            case DEADLINE_EXCEEDED -> AppError.TIMEOUT;
            case PERMISSION_DENIED -> AppError.PERMISSION_DENIED;
            case UNAUTHENTICATED -> AppError.UNAUTHENTICATED;
            default -> AppError.UNKNOWN;
        };
    }
}
