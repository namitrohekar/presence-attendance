package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One student's record for one session. Absent means there is no record. Immutable. */
public final class AttendanceRecord {

    /** Wire values are the constant names, as the security rules check them. */
    public enum Status {
        PRESENT,
        LATE_SYNC,
        MANUAL_PRESENT,
        MANUAL_ABSENT;

        @Nullable
        public static Status fromWire(@Nullable String value) {
            for (Status status : values()) {
                if (status.name().equals(value)) {
                    return status;
                }
            }
            return null;
        }

        /** Everything except an explicit manual absence counts towards attendance. */
        public boolean countsAsPresent() {
            return this != MANUAL_ABSENT;
        }
    }

    @NonNull private final String sessionId;
    @NonNull private final String studentId;
    @NonNull private final Status status;
    private final long markedAtMillis;

    public AttendanceRecord(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Status status,
            long markedAtMillis) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.markedAtMillis = markedAtMillis;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @NonNull
    public String getStudentId() {
        return studentId;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public long getMarkedAtMillis() {
        return markedAtMillis;
    }
}
