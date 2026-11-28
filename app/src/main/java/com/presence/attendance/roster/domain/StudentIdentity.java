package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict validation of the three things that identify a student (official address, roll number and
 * cohort, which is the batch's id) and of the details kept about them (name and lab group). The
 * security rules apply the same tests; these exist to give precise messages.
 */
public final class StudentIdentity {

    /** {@link #parseLabGroup} of blank text: no lab group yet. */
    public static final int NO_GROUP = 0;

    /** {@link #parseLabGroup} of text that is not a lab group of the batch. */
    public static final int INVALID_GROUP = -1;

    private static final int LOCAL_PART_MAX = 64;
    private static final String LOCAL_PART_EXTRA = "._-";

    /** "2", "B2", "Batch 2", "Group 2", "Lab group 2": the timetable calls them batches. */
    private static final Pattern LAB_GROUP =
            Pattern.compile(
                    "^(?:(?:(?:lab\\s*)?(?:group|batch)|[bg])\\s*[-#:.]?\\s*)?([0-9]{1,2})$");

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

    /** Trimmed, with every run of spaces, tabs or line breaks made one space. */
    @NonNull
    public static String normaliseName(@NonNull String input) {
        return input.trim().replaceAll("\\s+", " ");
    }

    /** A normalised name of 1 to {@link Constants#NAME_MAX_LEN} characters. */
    public static boolean isName(@NonNull String name) {
        return !name.isEmpty()
                && name.length() <= Constants.NAME_MAX_LEN
                && name.equals(name.trim());
    }

    /** A lab group of a batch split into {@code groups}: 1 to groups. */
    public static boolean isLabGroup(int labGroup, int groups) {
        return labGroup >= 1 && labGroup <= groups;
    }

    /**
     * A lab group as the HOD or a class list wrote it.
     *
     * @param groups how many lab groups the batch has
     * @return the group, {@link #NO_GROUP} for blank text, or {@link #INVALID_GROUP}
     */
    public static int parseLabGroup(@NonNull String input, int groups) {
        final String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return NO_GROUP;
        }
        final Matcher matcher = LAB_GROUP.matcher(text);
        if (!matcher.matches()) {
            return INVALID_GROUP;
        }
        final int group = Integer.parseInt(matcher.group(1));
        return isLabGroup(group, groups) ? group : INVALID_GROUP;
    }
}
