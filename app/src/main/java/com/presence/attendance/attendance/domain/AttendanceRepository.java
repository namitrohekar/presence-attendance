package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.presence.attendance.core.Outcome;
import java.util.List;

/** Attendance records. */
public interface AttendanceRepository {

    /**
     * A session's records for its teacher, newest first. Listens only while observed, so the live
     * roster updates without polling and costs nothing when the screen is closed.
     */
    @NonNull
    LiveData<Outcome<List<AttendanceRecord>>> observeSession(
            @NonNull String sessionId, @NonNull String teacherId);
}
