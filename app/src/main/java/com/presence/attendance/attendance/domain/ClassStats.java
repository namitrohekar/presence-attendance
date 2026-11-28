package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.session.domain.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class's figures, from its students' own: the average attendance (proxy penalties included, as
 * in each student's figure) and how many were present in each of the latest sessions. Immutable.
 */
public final class ClassStats {

    /** How many of the latest sessions the turnout covers. */
    public static final int RECENT_SESSIONS = 5;

    private final int present;
    private final int total;

    /** Students present in each of the latest ended sessions, oldest first. */
    @NonNull public final List<Integer> recentTurnout;

    private ClassStats(int present, int total, @NonNull List<Integer> recentTurnout) {
        this.present = present;
        this.total = total;
        this.recentTurnout = Collections.unmodifiableList(recentTurnout);
    }

    /**
     * @param students each student's figures for the period
     * @param heldNewestFirst the sessions of the period, newest first, as the course lists them
     * @param ended the ids of those sessions that are over
     * @param records every record of the course
     */
    @NonNull
    public static ClassStats of(
            @NonNull Collection<AttendanceStats> students,
            @NonNull List<Session> heldNewestFirst,
            @NonNull Set<String> ended,
            @NonNull Collection<AttendanceRecord> records) {
        int present = 0;
        int total = 0;
        for (AttendanceStats student : students) {
            present += student.getPresent();
            total += student.getTotal();
        }
        final List<Integer> turnout = new ArrayList<>();
        for (Session session : heldNewestFirst) {
            if (turnout.size() == RECENT_SESSIONS) {
                break;
            }
            if (!ended.contains(session.getId())) {
                continue;
            }
            final Set<String> there = new HashSet<>();
            for (AttendanceRecord record : records) {
                if (record.getSessionId().equals(session.getId())
                        && record.getStatus().countsAsPresent()) {
                    there.add(record.getStudentId());
                }
            }
            turnout.add(there.size());
        }
        Collections.reverse(turnout);
        return new ClassStats(present, total, turnout);
    }

    /** False until a session is over; there is no average before that. */
    public boolean hasSessions() {
        return total > 0;
    }

    /** The class's average attendance, whole percent with halves rounded up. */
    public int averagePercent() {
        return total == 0 ? 0 : (200 * present + total) / (2 * total);
    }
}
