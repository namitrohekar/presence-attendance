package com.presence.attendance.core;

/**
 * Contract values shared with the security rules.
 *
 * <p>Mirror of {@code contracts/constants.json}, which is the source of truth. {@code
 * ConstantsParityTest} fails if a value here differs from that file, and the rules suite does the
 * same for {@code firestore.rules}.
 */
public final class Constants {

    /** Seconds between QR rotations, counted from the previous token's server ack. */
    public static final int ROTATION_S = 12;

    /** Seconds a token is accepted after the server stamped it. */
    public static final int TOKEN_VALIDITY_S = 20;

    public static final int SESSION_MIN_MIN = 5;
    public static final int SESSION_MAX_MIN = 180;

    /** Minutes of slack the rules allow between the device clock and the server clock. */
    public static final int SESSION_SKEW_MIN = 1;

    public static final int GEO_RADIUS_MIN_M = 30;
    public static final int GEO_RADIUS_MAX_M = 1000;

    /** A location fix less accurate than this is rejected. */
    public static final int MAX_ACCURACY_M = 50;

    /** Minutes after a session ends during which an offline scan may still sync. */
    public static final int QUEUE_GRACE_MIN = 30;

    public static final int DEFAULT_THRESHOLD = 75;
    public static final int THRESHOLD_MIN = 1;
    public static final int THRESHOLD_MAX = 100;

    public static final int NOTE_MAX_LEN = 140;

    /** Metres per degree of latitude used by the flat-earth geofence approximation. */
    public static final double METERS_PER_DEGREE = 111320;

    public static final int JOIN_CODE_LENGTH = 6;

    /** No O, 0, I or 1: they are misread when a code is copied by hand. */
    public static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static final int TOKEN_BYTES = 16;

    /** Length of {@link #TOKEN_BYTES} random bytes in base64url without padding. */
    public static final int TOKEN_LENGTH = 22;

    public static final String QR_PREFIX = "prs1";

    /** Students must sign in with an address in exactly this domain. Faculty are allowlisted. */
    public static final String STUDENT_DOMAIN = "mc.vjti.ac.in";

    /** A roll number is this many digits. It is an opaque identifier and is never decoded. */
    public static final int ROLL_NO_LENGTH = 9;

    public static final int COHORT_MIN_LEN = 2;
    public static final int COHORT_MAX_LEN = 8;

    /** Days an identity alert must be kept before a faculty app may purge it. */
    public static final int ALERT_RETENTION_DAYS = 90;

    /** Days a roster correction record must be kept before a faculty app may purge it. */
    public static final int CORRECTION_RETENTION_DAYS = 365;

    /** The largest headcount a teacher can record for a session. */
    public static final int HEADCOUNT_MAX = 1000;

    /** Attended sessions taken off a student's figure for each mark revoked as a proxy. */
    public static final int PROXY_PENALTY_SESSIONS = 1;

    /** Longest official name on the roster. */
    public static final int NAME_MAX_LEN = 80;

    /** Lab batches are numbered from BATCH_MIN to BATCH_MAX. */
    public static final int BATCH_MIN = 1;

    public static final int BATCH_MAX = 20;

    /** The ten emojis a student can be shown with, by stored name, in their fixed order. */
    public static final String STUDENT_EMOJIS =
            "LION,TIGER,ELEPHANT,PANDA,FOX,OWL,DOLPHIN,TURTLE,BUTTERFLY,PEACOCK";

    private Constants() {}
}
