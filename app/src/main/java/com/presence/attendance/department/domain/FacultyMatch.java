package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.ui.Monogram;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds the teacher a timetable names. Timetables write initials ("SJ"), a title and a first name
 * ("Prof. Nilima") or a full name ("Dr. Amruta Patil"), and pairs ("AP+GS (Rishab Mishra)"). The
 * first name in a pair is the subject's teacher. A guess only: the HOD sees every match and can
 * change it.
 */
public final class FacultyMatch {

    private FacultyMatch() {}

    /** The one faculty member the text names, or null if none or more than one fits. */
    @Nullable
    public static FacultyMember find(@NonNull String text, @NonNull List<FacultyMember> faculty) {
        final String first = text.split("[+/,&]")[0].replaceAll("\\(.*?\\)", "").trim();
        if (first.isEmpty()) {
            return null;
        }
        final List<FacultyMember> found = new ArrayList<>();
        final String initials = FacultyMember.normaliseInitials(first);
        if (first.equals(first.toUpperCase(Locale.ROOT)) && FacultyMember.isInitials(initials)) {
            for (FacultyMember member : faculty) {
                if (member.getInitials().equals(initials)) {
                    found.add(member);
                }
            }
        } else {
            final List<String> wanted = words(first);
            for (FacultyMember member : faculty) {
                if (!wanted.isEmpty() && words(member.getName()).containsAll(wanted)) {
                    found.add(member);
                }
            }
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    /** The words of a name without titles, in lower case. */
    @NonNull
    private static List<String> words(@NonNull String name) {
        final List<String> words = new ArrayList<>();
        for (String word : Arrays.asList(name.trim().split("[\\s.]+"))) {
            if (!word.isEmpty() && !Monogram.isTitle(word)) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }
}
