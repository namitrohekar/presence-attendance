package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One attendance session of a course. Immutable. */
public final class Session {

    /** Wire values are the constant names, as the security rules check them. */
    public enum Status {
        ACTIVE,
        ENDED
    }

    @NonNull private final String id;
    @NonNull private final String courseId;
    @NonNull private final String teacherId;
    @NonNull private final Status status;
    private final long startedAtMillis;
    private final long endsAtMillis;
    @NonNull private final Geofence geofence;
    @Nullable private final Integer headcount;

    public Session(
            @NonNull String id,
            @NonNull String courseId,
            @NonNull String teacherId,
            @NonNull Status status,
            long startedAtMillis,
            long endsAtMillis,
            @NonNull Geofence geofence) {
        this(id, courseId, teacherId, status, startedAtMillis, endsAtMillis, geofence, null);
    }

    /**
     * @param headcount how many people the teacher counted in the room; null if not entered
     */
    public Session(
            @NonNull String id,
            @NonNull String courseId,
            @NonNull String teacherId,
            @NonNull Status status,
            long startedAtMillis,
            long endsAtMillis,
            @NonNull Geofence geofence,
            @Nullable Integer headcount) {
        this.id = id;
        this.courseId = courseId;
        this.teacherId = teacherId;
        this.status = status;
        this.startedAtMillis = startedAtMillis;
        this.endsAtMillis = endsAtMillis;
        this.geofence = geofence;
        this.headcount = headcount;
    }

    /**
     * Whether students can still mark. Nothing flips the status when the end time passes, so a
     * session that is ACTIVE but past its end is treated as ended everywhere. The device clock
     * decides only what the screen shows; the server decides what is accepted.
     */
    public boolean isLive(long nowMillis) {
        return status == Status.ACTIVE && nowMillis < endsAtMillis;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getCourseId() {
        return courseId;
    }

    @NonNull
    public String getTeacherId() {
        return teacherId;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getEndsAtMillis() {
        return endsAtMillis;
    }

    @NonNull
    public Geofence getGeofence() {
        return geofence;
    }

    @Nullable
    public Integer getHeadcount() {
        return headcount;
    }
}
