package com.presence.attendance.core.ui;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;

/** The one place where an {@link AppError} becomes the sentence shown to the user. */
public final class AppErrorMessages {

    private AppErrorMessages() {}

    // No default branch: adding an AppError without a message is a compile error.
    @StringRes
    public static int of(@NonNull AppError error) {
        return switch (error) {
            case NOT_CONFIGURED -> R.string.error_not_configured;
            case OFFLINE -> R.string.error_offline;
            case TIMEOUT -> R.string.error_timeout;
            case PERMISSION_DENIED -> R.string.error_permission_denied;
            case UNAUTHENTICATED -> R.string.error_unauthenticated;
            case SIGN_IN_CANCELLED, SIGN_IN_FAILED -> R.string.error_sign_in_failed;
            case NO_GOOGLE_ACCOUNT -> R.string.error_no_google_account;
            case ACCOUNT_NOT_ALLOWED -> R.string.error_account_not_allowed;
            case JOIN_CODE_NOT_FOUND -> R.string.error_join_code_not_found;
            case ALREADY_JOINED -> R.string.error_already_joined;
            case OWN_COURSE -> R.string.error_own_course;
            case UNKNOWN -> R.string.error_unknown;
        };
    }
}
