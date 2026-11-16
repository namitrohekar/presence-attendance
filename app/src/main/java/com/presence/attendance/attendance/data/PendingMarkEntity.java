package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;

/** One row of the offline queue. Enum values are stored by name. */
@Entity(
        tableName = "pending_marks",
        primaryKeys = {"sessionId", "studentId"})
final class PendingMarkEntity {
    @NonNull final String sessionId;
    @NonNull final String studentId;
    @NonNull final String token;
    final double lat;
    final double lng;
    final float accuracyM;
    final long scannedAtMillis;
    @NonNull final String state;
    @Nullable final String reason;

    PendingMarkEntity(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull String token,
            double lat,
            double lng,
            float accuracyM,
            long scannedAtMillis,
            @NonNull String state,
            @Nullable String reason) {
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
}
