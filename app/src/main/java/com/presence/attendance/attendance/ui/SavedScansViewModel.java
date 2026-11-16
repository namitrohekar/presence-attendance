package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMarkStore;
import java.util.List;

/**
 * A student's saved scans. Whenever one is still waiting, a sync is requested: the request is
 * cheap, and it runs only when there is a connection.
 */
public final class SavedScansViewModel extends ViewModel {

    private final PendingMarkStore store;
    private final MutableLiveData<String> studentId = new MutableLiveData<>();
    private final MediatorLiveData<List<PendingMark>> marks = new MediatorLiveData<>();

    public SavedScansViewModel(@NonNull PendingMarkStore store, @NonNull Runnable requestSync) {
        this.store = store;
        marks.addSource(
                Transformations.switchMap(studentId, store::observe),
                saved -> {
                    if (waiting(saved) > 0) {
                        requestSync.run();
                    }
                    marks.setValue(saved);
                });
    }

    /** Whose scans to show. Scans saved by another account on this device stay hidden. */
    public void setStudent(@NonNull String uid) {
        if (!uid.equals(studentId.getValue())) {
            studentId.setValue(uid);
        }
    }

    @NonNull
    public LiveData<List<PendingMark>> getMarks() {
        return marks;
    }

    /** Removes a settled scan from the list. One that is still waiting cannot be removed. */
    public void dismiss(@NonNull String sessionId) {
        final List<PendingMark> saved = marks.getValue();
        if (saved == null) {
            return;
        }
        for (PendingMark mark : saved) {
            if (mark.getSessionId().equals(sessionId)
                    && mark.getState() != PendingMark.State.PENDING) {
                store.dismiss(mark);
            }
        }
    }

    /** How many of the scans are still waiting to be sent. */
    public static int waiting(@NonNull List<PendingMark> saved) {
        int count = 0;
        for (PendingMark mark : saved) {
            if (mark.getState() == PendingMark.State.PENDING) {
                count++;
            }
        }
        return count;
    }
}
