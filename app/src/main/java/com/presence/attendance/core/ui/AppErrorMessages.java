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
            case NOT_VJTI_ACCOUNT -> R.string.error_not_vjti_account;
            case REGISTRATION_REQUIRED -> R.string.error_registration_required;
            case NOT_ON_ROSTER -> R.string.error_not_on_roster;
            case ROLL_MISMATCH -> R.string.error_roll_mismatch;
            case DUPLICATE_CLAIM -> R.string.error_duplicate_claim;
            case FILE_UNREADABLE -> R.string.error_file_unreadable;
            case LOCATION_PERMISSION_REQUIRED -> R.string.error_location_permission;
            case LOCATION_UNAVAILABLE -> R.string.error_location_unavailable;
            case CAMPUS_NOT_CONFIGURED -> R.string.error_campus_not_configured;
            case SESSION_NOT_FOUND -> R.string.error_session_not_found;
            case SESSION_ENDED -> R.string.error_session_ended;
            case JOIN_CODE_NOT_FOUND -> R.string.error_join_code_not_found;
            case ALREADY_JOINED -> R.string.error_already_joined;
            case OWN_COURSE -> R.string.error_own_course;
            case UNKNOWN -> R.string.error_unknown;
        };
    }
}
