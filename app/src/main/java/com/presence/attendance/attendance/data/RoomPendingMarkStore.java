package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMarkStore;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.AppExecutors;
import java.util.ArrayList;
import java.util.List;

/** {@link PendingMarkStore} on Room. Main-thread calls do their disk work on the io executor. */
public final class RoomPendingMarkStore implements PendingMarkStore {

    private final PendingMarkDao dao;
    private final AppExecutors executors;

    public RoomPendingMarkStore(@NonNull QueueDatabase database, @NonNull AppExecutors executors) {
        this.dao = database.pendingMarks();
        this.executors = executors;
    }

    @Override
    public void add(@NonNull PendingMark mark, @NonNull Runnable saved) {
        executors
                .io()
                .execute(() -> {
                    dao.put(PendingMarkMapper.toEntity(mark));
                    executors.main().execute(saved);
                });
    }

    @Override
    public void dismiss(@NonNull PendingMark mark) {
        executors.io().execute(() -> dao.dismiss(mark.getSessionId(), mark.getStudentId()));
    }

    @NonNull
    @Override
    public LiveData<List<PendingMark>> observe(@NonNull String studentId) {
        return Transformations.map(dao.observe(studentId), RoomPendingMarkStore::marks);
    }

    @NonNull
    @Override
    public List<PendingMark> pending(@NonNull String studentId) {
        return marks(dao.pending(studentId));
    }

    @Override
    public void resolve(
            @NonNull PendingMark mark,
            @NonNull PendingMark.State state,
            @Nullable AppError reason) {
        dao.resolve(
                mark.getSessionId(),
                mark.getStudentId(),
                mark.getToken(),
                state.name(),
                reason != null ? reason.name() : null);
    }

    @NonNull
    private static List<PendingMark> marks(@NonNull List<PendingMarkEntity> rows) {
        final List<PendingMark> marks = new ArrayList<>(rows.size());
        for (PendingMarkEntity row : rows) {
            marks.add(PendingMarkMapper.fromEntity(row));
        }
        return marks;
    }
}
