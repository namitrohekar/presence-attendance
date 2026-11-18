package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;
import com.presence.attendance.session.domain.Session;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One student's attendance in one course: sessions attended out of sessions held.
 *
 * <p>Only sessions that have ended are counted, so a class in progress never lowers anyone's
 * figure. A session with no record for the student counts as absent. A mark revoked as a proxy
 * counts as absent and takes {@link Constants#PROXY_PENALTY_SESSIONS} more attended sessions off,
 * never below zero.
 */
public final class AttendanceStats {

    private final int present;
    private final int total;
    private final int strikes;

    private AttendanceStats(int present, int total, int strikes) {
        this.present = present;
        this.total = total;
        this.strikes = strikes;
    }

    /** The ids of the sessions that are over at {@code nowMillis}. */
    @NonNull
    public static Set<String> ended(@NonNull List<Session> sessions, long nowMillis) {
        final Set<String> ids = new HashSet<>();
        for (Session session : sessions) {
            if (!session.isLive(nowMillis)) {
                ids.add(session.getId());
            }
        }
        return ids;
    }

    /** The figures for one student, from that student's records. */
    @NonNull
    public static AttendanceStats of(
            @NonNull Set<String> endedSessionIds,
            @NonNull Collection<AttendanceRecord> studentRecords) {
        final Set<String> attended = new HashSet<>();
        final Set<String> revoked = new HashSet<>();
        for (AttendanceRecord record : studentRecords) {
            if (!endedSessionIds.contains(record.getSessionId())) {
                continue;
            }
            if (record.getStatus().countsAsPresent()) {
                attended.add(record.getSessionId());
            } else if (record.getStatus() == AttendanceRecord.Status.REVOKED) {
                revoked.add(record.getSessionId());
            }
        }
        // A revoked session is already absent; each one also costs an attended session.
        final int penalty = revoked.size() * Constants.PROXY_PENALTY_SESSIONS;
        return new AttendanceStats(
                Math.max(0, attended.size() - penalty), endedSessionIds.size(), revoked.size());
    }

    public int getPresent() {
        return present;
    }

    public int getTotal() {
        return total;
    }

    /** Marks revoked as a proxy, in sessions that are over. */
    public int getStrikes() {
        return strikes;
    }

    /** False until the course has held a session; there is no percentage before that. */
    public boolean hasSessions() {
        return total > 0;
    }

    /** Whole percent, halves rounded up. Zero when no session has been held. */
    public int percent() {
        return total == 0 ? 0 : (200 * present + total) / (2 * total);
    }

    /**
     * Whether the student is below the course's required percentage. Compared exactly, not on the
     * rounded figure: 74.6% is below 75 even though it displays as 75.
     */
    public boolean isBelow(int thresholdPercent) {
        return total > 0 && present * 100 < thresholdPercent * total;
    }
}
