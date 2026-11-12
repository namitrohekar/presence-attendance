package com.presence.attendance.core;

/**
 * Every failure the app can show. Exceptions from Firebase and the platform are mapped to one of
 * these in the data layer; the UI turns each into a plain-language message.
 */
public enum AppError {
    NOT_CONFIGURED,
    OFFLINE,
    TIMEOUT,
    PERMISSION_DENIED,
    UNAUTHENTICATED,
    SIGN_IN_CANCELLED,
    SIGN_IN_FAILED,
    NO_GOOGLE_ACCOUNT,
    ACCOUNT_NOT_ALLOWED,
    JOIN_CODE_NOT_FOUND,
    ALREADY_JOINED,
    OWN_COURSE,
    UNKNOWN
}
