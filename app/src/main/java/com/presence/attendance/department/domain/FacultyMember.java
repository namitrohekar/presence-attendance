package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Someone on the faculty list the HOD keeps: they sign in as faculty, and the timetable's initials
 * (SJ, GS) are matched to them. Immutable.
 */
public final class FacultyMember {

    /** The same shape the rules require: lower case, one '@', a dotted domain. */
    private static final Pattern STAFF_EMAIL =
            Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+([.][a-z0-9-]+)+$");

    private static final int EMAIL_MAX = 100;

    @NonNull private final String email;
    @NonNull private final String name;
    @NonNull private final String initials;

    public FacultyMember(@NonNull String email, @NonNull String name, @NonNull String initials) {
        this.email = email;
        this.name = name;
        this.initials = initials;
    }

    /** Trimmed and lower case, as faculty document ids are. The result may still be invalid. */
    @NonNull
    public static String normaliseEmail(@NonNull String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isStaffEmail(@NonNull String email) {
        return email.length() <= EMAIL_MAX && STAFF_EMAIL.matcher(email).matches();
    }

    /** Upper case with spaces and dots removed: "s.j." is "SJ". The result may still be invalid. */
    @NonNull
    public static String normaliseInitials(@NonNull String input) {
        return input.replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
    }

    /** 1 to {@link Constants#INITIALS_MAX_LEN} capital letters. */
    public static boolean isInitials(@NonNull String initials) {
        if (initials.isEmpty() || initials.length() > Constants.INITIALS_MAX_LEN) {
            return false;
        }
        for (int i = 0; i < initials.length(); i++) {
            final char c = initials.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getInitials() {
        return initials;
    }
}
