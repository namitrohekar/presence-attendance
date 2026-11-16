package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.core.AppError;

/** Explicit mapping between the queue's rows and {@link PendingMark}. */
final class PendingMarkMapper {

    private PendingMarkMapper() {}

    @NonNull
    static PendingMarkEntity toEntity(@NonNull PendingMark mark) {
        final AppError reason = mark.getReason();
        return new PendingMarkEntity(
                mark.getSessionId(),
                mark.getStudentId(),
                mark.getToken(),
                mark.getLat(),
                mark.getLng(),
                mark.getAccuracyM(),
                mark.getScannedAtMillis(),
                mark.getState().name(),
                reason != null ? reason.name() : null);
    }

    /** A row with a state this build does not know is shown as rejected, never as pending. */
    @NonNull
    static PendingMark fromEntity(@NonNull PendingMarkEntity row) {
        PendingMark.State state = PendingMark.State.REJECTED;
        for (PendingMark.State known : PendingMark.State.values()) {
            if (known.name().equals(row.state)) {
                state = known;
            }
        }
        return new PendingMark(
                row.sessionId,
                row.studentId,
                row.token,
                row.lat,
                row.lng,
                row.accuracyM,
                row.scannedAtMillis,
                state,
                reason(row.reason, state));
    }

    @Nullable
    private static AppError reason(@Nullable String name, @NonNull PendingMark.State state) {
        if (state != PendingMark.State.REJECTED) {
            return null;
        }
        for (AppError error : AppError.values()) {
            if (error.name().equals(name)) {
                return error;
            }
        }
        return AppError.UNKNOWN;
    }
}
