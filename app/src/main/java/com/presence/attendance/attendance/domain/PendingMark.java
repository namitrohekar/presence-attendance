package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.AppError;

/**
 * A scan made while the server could not be reached, kept on the device until it can be sent. One
 * per student and session: the server accepts at most one record for that pair.
 */
public final class PendingMark {

    public enum State {
        /** Waiting for a connection. */
        PENDING,
        /** The server has the record. */
        SYNCED,
        /** The session closed, plus its grace period, before the scan could be sent. */
        EXPIRED,
        /** The server, or a check the server would make, refused it. */
        REJECTED
    }

    @NonNull private final String sessionId;
    @NonNull private final String studentId;
    @NonNull private final String token;
    private final double lat;
    private final double lng;
    private final float accuracyM;
    private final long scannedAtMillis;
    @NonNull private final State state;
    @Nullable private final AppError reason;

    public PendingMark(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull String token,
            double lat,
            double lng,
            float accuracyM,
            long scannedAtMillis,
            @NonNull State state,
            @Nullable AppError reason) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.token = token;
        this.lat = lat;
        this.lng = lng;
        this.accuracyM = accuracyM;
        this.scannedAtMillis = scannedAtMillis;
        this.state = state;
        this.reason = reason;
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
    public String getToken() {
        return token;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public float getAccuracyM() {
        return accuracyM;
    }

    /** The device clock when the code was read. */
    public long getScannedAtMillis() {
        return scannedAtMillis;
    }

    @NonNull
    public State getState() {
        return state;
    }

    /** Why it was rejected; null in every other state. */
    @Nullable
    public AppError getReason() {
        return reason;
    }
}
