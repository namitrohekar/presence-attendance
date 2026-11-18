package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.core.ui.Line;

/** The one place a record's status becomes words and a badge tone. */
final class AttendanceLabels {

    private AttendanceLabels() {}

    /** No record means the student never marked and nobody marked them: absent. */
    @StringRes
    static int label(@Nullable AttendanceRecord record) {
        if (record == null) {
            return R.string.status_absent;
        }
        switch (record.getStatus()) {
            case PRESENT:
                return R.string.status_present;
            case LATE_SYNC:
                return R.string.live_late_sync;
            case MANUAL_PRESENT:
                return R.string.status_manual_present;
            case REVOKED:
                return R.string.status_revoked;
            default:
                return R.string.status_manual_absent;
        }
    }

    @NonNull
    static Line.Tone tone(@Nullable AttendanceRecord record) {
        if (record == null) {
            return Line.Tone.NEUTRAL;
        }
        return record.getStatus().countsAsPresent() ? Line.Tone.SUCCESS : Line.Tone.DANGER;
    }
}
