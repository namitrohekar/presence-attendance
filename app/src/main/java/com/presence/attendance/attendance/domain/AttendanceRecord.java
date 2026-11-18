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
        MANUAL_ABSENT,
        /** A scan the teacher found to be a proxy. Absent, plus a penalty. */
        REVOKED;

        @Nullable
        public static Status fromWire(@Nullable String value) {
            for (Status status : values()) {
                if (status.name().equals(value)) {
                    return status;
                }
            }
            return null;
        }

        public boolean countsAsPresent() {
            return this == PRESENT || this == LATE_SYNC || this == MANUAL_PRESENT;
        }
    }

    @NonNull private final String sessionId;
    @NonNull private final String studentId;
    @NonNull private final Status status;
    private final long markedAtMillis;
    private final boolean fromScan;
    @Nullable private final String note;
    @Nullable private final Double lat;
    @Nullable private final Double lng;
    private final int revision;

    /** A record with no detail beyond its status, as made by the student's own scan or not. */
    public AttendanceRecord(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Status status,
            long markedAtMillis) {
        this(
                sessionId,
                studentId,
                status,
                markedAtMillis,
                status == Status.PRESENT || status == Status.LATE_SYNC,
                null,
                null,
                null,
                0);
    }

    /**
     * @param fromScan the record was created by the student's scan, live or sent late, rather than
     *     by the teacher; only such a record can be revoked
     * @param note the teacher's reason for the latest change, if any
     * @param revision how many times the teacher has changed it
     */
    public AttendanceRecord(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Status status,
            long markedAtMillis,
            boolean fromScan,
            @Nullable String note,
            @Nullable Double lat,
            @Nullable Double lng,
            int revision) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.markedAtMillis = markedAtMillis;
        this.fromScan = fromScan;
        this.note = note;
        this.lat = lat;
        this.lng = lng;
        this.revision = revision;
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

    public boolean isFromScan() {
        return fromScan;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    /** Where the scan was made; null for a record the teacher created. */
    @Nullable
    public Double getLat() {
        return lat;
    }

    @Nullable
    public Double getLng() {
        return lng;
    }

    public int getRevision() {
        return revision;
    }
}
