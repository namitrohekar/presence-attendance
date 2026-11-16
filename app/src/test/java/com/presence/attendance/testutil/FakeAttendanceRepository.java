package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Session;
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

    public Outcome<Boolean> hasMarkedOutcome = Outcome.success(false);
    public Outcome<MarkResult> markOutcome = Outcome.success(MarkResult.MARKED);
    public final List<String> marks = new ArrayList<>();
    public LocationFix lastFix;

    @Override
    public void hasMarked(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Callback<Boolean> callback) {
        callback.onResult(hasMarkedOutcome);
    }

    @Override
    public void markLive(
            @NonNull User student,
            @NonNull Session session,
            @NonNull String token,
            @NonNull LocationFix fix,
            @NonNull Callback<MarkResult> callback) {
        marks.add(session.getId() + ":" + token);
        lastFix = fix;
        callback.onResult(markOutcome);
    }

    @Override
    public void markQueued(
            @NonNull Session session,
            @NonNull PendingMark mark,
            @NonNull Callback<MarkResult> callback) {
        marks.add(session.getId() + ":" + mark.getToken() + ":queued");
        callback.onResult(markOutcome);
    }

    public Outcome<Boolean> correctOutcome = Outcome.success(true);
    public final List<String> corrections = new ArrayList<>();

    @Override
    public void correct(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            boolean hasRecord,
            boolean present,
            @NonNull String note,
            @NonNull Callback<Boolean> callback) {
        corrections.add(
                String.join(
                        ":",
                        teacherId,
                        courseId,
                        sessionId,
                        studentId,
                        hasRecord ? "update" : "create",
                        String.valueOf(present),
                        note));
        callback.onResult(correctOutcome);
    }
}
