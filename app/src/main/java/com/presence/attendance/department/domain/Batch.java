package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import java.util.Locale;

/**
 * One intake of a programme, such as MCA 2025-27, with its current semester. Its id, the programme
 * and the start year's last two digits (MCA25), is also the cohort of its students on the roster.
 * Immutable.
 */
public final class Batch {

    /** Between the two years of a batch's name: "MCA 2025–27". */
    private static final char EN_DASH = (char) 0x2013;

    private static final int CENTURY = 100;

    @NonNull private final String id;
    @NonNull private final String programme;
    private final int startYear;
    private final int years;
    private final int semester;
    private final int intake;
    private final int labGroups;

    public Batch(
            @NonNull String programme,
            int startYear,
            int years,
            int semester,
            int intake,
            int labGroups) {
        this.id = idOf(programme, startYear);
        this.programme = programme;
        this.startYear = startYear;
        this.years = years;
        this.semester = semester;
        this.intake = intake;
        this.labGroups = labGroups;
    }

    /** The id the rules require: the programme and the start year's last two digits. */
    @NonNull
    public static String idOf(@NonNull String programme, int startYear) {
        return programme + String.format(Locale.ROOT, "%02d", startYear % CENTURY);
    }

    /** Trimmed and upper case. The result may still be invalid. */
    @NonNull
    public static String normaliseProgramme(@NonNull String input) {
        return input.trim().toUpperCase(Locale.ROOT);
    }

    /** Two to six capital letters, such as MCA. */
    public static boolean isProgramme(@NonNull String programme) {
        if (programme.length() < Constants.PROGRAMME_MIN_LEN
                || programme.length() > Constants.PROGRAMME_MAX_LEN) {
            return false;
        }
        for (int i = 0; i < programme.length(); i++) {
            final char c = programme.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return true;
    }

    public static boolean isStartYear(int year) {
        return year >= Constants.BATCH_YEAR_MIN && year <= Constants.BATCH_YEAR_MAX;
    }

    public static boolean isYears(int years) {
        return years >= 1 && years <= Constants.PROGRAMME_YEARS_MAX;
    }

    public static boolean isIntake(int intake) {
        return intake >= 1 && intake <= Constants.INTAKE_MAX;
    }

    public static boolean isLabGroups(int labGroups) {
        return labGroups >= 1 && labGroups <= Constants.LAB_GROUPS_MAX;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getProgramme() {
        return programme;
    }

    public int getStartYear() {
        return startYear;
    }

    public int getYears() {
        return years;
    }

    /** The current semester, from 1. */
    public int getSemester() {
        return semester;
    }

    /** The planned number of students. Admissions are cancelled, so the roster may hold fewer. */
    public int getIntake() {
        return intake;
    }

    public int getLabGroups() {
        return labGroups;
    }

    /** "MCA 2025–27". */
    @NonNull
    public String displayName() {
        return programme
                + " "
                + startYear
                + EN_DASH
                + String.format(Locale.ROOT, "%02d", (startYear + years) % CENTURY);
    }

    /** How many semesters the programme has: two a year. */
    public int semesterCount() {
        return years * Constants.SEMESTERS_PER_YEAR;
    }

    /** Whether the batch is in its programme's last semester. */
    public boolean isInLastSemester() {
        return semester >= semesterCount();
    }
}
