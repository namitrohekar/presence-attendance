package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMarkStore;
import com.presence.attendance.core.AppError;
import java.util.ArrayList;
import java.util.List;

/** In-memory {@link PendingMarkStore} with the same replace, resolve and dismiss rules as Room. */
public final class FakePendingMarkStore implements PendingMarkStore {

    public final List<PendingMark> marks = new ArrayList<>();
    public final MutableLiveData<List<PendingMark>> feed = new MutableLiveData<>();
    public String observedStudent;

    @Override
    public void add(@NonNull PendingMark mark, @NonNull Runnable saved) {
        marks.removeIf(m -> sameKey(m, mark));
        marks.add(mark);
        saved.run();
    }

    @Override
    public void dismiss(@NonNull PendingMark mark) {
        marks.removeIf(m -> sameKey(m, mark) && m.getState() != PendingMark.State.PENDING);
    }

    @NonNull
    @Override
    public LiveData<List<PendingMark>> observe(@NonNull String studentId) {
        observedStudent = studentId;
        return feed;
    }

    @NonNull
    @Override
    public List<PendingMark> pending(@NonNull String studentId) {
        final List<PendingMark> pending = new ArrayList<>();
        for (PendingMark mark : marks) {
            if (mark.getStudentId().equals(studentId)
                    && mark.getState() == PendingMark.State.PENDING) {
                pending.add(mark);
            }
        }
        return pending;
    }

    @Override
    public void resolve(
            @NonNull PendingMark mark,
            @NonNull PendingMark.State state,
            @Nullable AppError reason) {
        for (int i = 0; i < marks.size(); i++) {
            final PendingMark stored = marks.get(i);
            if (sameKey(stored, mark)
                    && stored.getToken().equals(mark.getToken())
                    && stored.getState() == PendingMark.State.PENDING) {
                marks.set(
                        i,
                        new PendingMark(
                                stored.getSessionId(),
                                stored.getStudentId(),
                                "",
                                0,
                                0,
                                0,
                                stored.getScannedAtMillis(),
                                state,
                                reason));
            }
        }
    }

    /** The one stored scan for a session. */
    public PendingMark only(@NonNull String sessionId) {
        PendingMark found = null;
        for (PendingMark mark : marks) {
            if (mark.getSessionId().equals(sessionId)) {
                if (found != null) {
                    throw new AssertionError("more than one scan for " + sessionId);
                }
                found = mark;
            }
        }
        if (found == null) {
            throw new AssertionError("no scan for " + sessionId);
        }
        return found;
    }

    private static boolean sameKey(@NonNull PendingMark a, @NonNull PendingMark b) {
        return a.getSessionId().equals(b.getSessionId())
                && a.getStudentId().equals(b.getStudentId());
    }
}
