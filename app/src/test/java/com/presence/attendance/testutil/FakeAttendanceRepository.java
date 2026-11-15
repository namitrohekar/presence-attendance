package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.core.Outcome;
import java.util.ArrayList;
import java.util.List;

/** In-memory {@link AttendanceRepository}: the test pushes rosters into {@link #feed}. */
public final class FakeAttendanceRepository implements AttendanceRepository {

    public final MutableLiveData<Outcome<List<AttendanceRecord>>> feed = new MutableLiveData<>();
    public final List<String> observed = new ArrayList<>();

    @NonNull
    @Override
    public LiveData<Outcome<List<AttendanceRecord>>> observeSession(
            @NonNull String sessionId, @NonNull String teacherId) {
        observed.add(sessionId + ":" + teacherId);
        return feed;
    }
}
