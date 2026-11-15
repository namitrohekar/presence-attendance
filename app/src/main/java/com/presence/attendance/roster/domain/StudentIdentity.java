package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.util.Locale;

/**
 * Strict validation of the three things that identify a student: official address, roll number and
 * cohort. The security rules apply the same tests; these exist to give precise messages.
 */
public final class StudentIdentity {

    private static final int LOCAL_PART_MAX = 64;
    private static final String LOCAL_PART_EXTRA = "._-";

    private StudentIdentity() {}

    /** Trimmed and lower-cased, as roster document ids are. The result may still be invalid. */
    @NonNull
    public static String normaliseEmail(@NonNull String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether an address of any case is in exactly the student domain. */
    public static boolean hasStudentDomain(@NonNull String email) {
        final String[] parts = email.toLowerCase(Locale.ROOT).split("@", -1);
        return parts.length == 2 && parts[1].equals(Constants.STUDENT_DOMAIN);
    }

    /** Whether a normalised address can be a roster entry: one '@', the exact student domain. */
    public static boolean isStudentEmail(@NonNull String email) {
        final String[] parts = email.split("@", -1);
        if (parts.length != 2 || !parts[1].equals(Constants.STUDENT_DOMAIN)) {
            return false;
        }
        final String local = parts[0];
        if (local.isEmpty() || local.length() > LOCAL_PART_MAX) {
            return false;
        }
        for (int i = 0; i < local.length(); i++) {
            final char c = local.charAt(i);
            final boolean allowed =
                    (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || LOCAL_PART_EXTRA.indexOf(c) >= 0;
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** Exactly {@link Constants#ROLL_NO_LENGTH} ASCII digits. The number is never decoded. */
    public static boolean isRollNo(@NonNull String rollNo) {
        if (rollNo.length() != Constants.ROLL_NO_LENGTH) {
            return false;
        }
        for (int i = 0; i < rollNo.length(); i++) {
            final char c = rollNo.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static String normaliseCohort(@NonNull String input) {
        return input.trim().toUpperCase(Locale.ROOT);
    }

    /** Capital letters and digits only, within the contract's length bounds. */
    public static boolean isCohort(@NonNull String cohort) {
        if (cohort.length() < Constants.COHORT_MIN_LEN
                || cohort.length() > Constants.COHORT_MAX_LEN) {
            return false;
        }
        for (int i = 0; i < cohort.length(); i++) {
            final char c = cohort.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }
}
