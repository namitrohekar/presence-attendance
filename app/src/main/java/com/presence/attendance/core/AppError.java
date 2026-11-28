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
    /** Neither on the faculty list nor an address in the student domain. */
    NOT_VJTI_ACCOUNT,
    /** A student address that has not registered a roll number yet. Not shown as an error. */
    REGISTRATION_REQUIRED,
    NOT_ON_ROSTER,
    ROLL_MISMATCH,
    DUPLICATE_CLAIM,
    JOIN_CODE_NOT_FOUND,
    ALREADY_JOINED,
    OWN_COURSE,
    FILE_UNREADABLE,
    /** Precise location has not been granted. */
    LOCATION_PERMISSION_REQUIRED,
    /** No usable fix: location is off, or none arrived in time. */
    LOCATION_UNAVAILABLE,
    /** Fixes are arriving, but none is accurate enough to compare with the area. */
    LOCATION_INACCURATE,
    /** appConfig/campus has not been created in the console. */
    CAMPUS_NOT_CONFIGURED,
    SESSION_NOT_FOUND,
    /** The batch was not found: it was never created, or the link is stale. */
    BATCH_NOT_FOUND,
    /** Nobody has saved this room's location yet. */
    ROOM_NOT_SAVED,
    SESSION_ENDED,
    ALREADY_MARKED,
    /** Another student has already marked this session from this phone. */
    DEVICE_USED,
    /** The student's account is bound to another phone; the HOD can move it. */
    ACCOUNT_ON_OTHER_PHONE,
    NOT_ENROLLED,
    MOCK_LOCATION,
    OUTSIDE_AREA,
    /** The rules refused a mark that passed every check the app can make: the token is stale. */
    QR_EXPIRED,
    /** Location is switched off in system settings. */
    LOCATION_OFF,
    UNKNOWN
}
