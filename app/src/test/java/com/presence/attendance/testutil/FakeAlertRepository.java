package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.AlertRepository;
import com.presence.attendance.roster.domain.IdentityAlert;
import java.util.ArrayList;
import java.util.List;

/** In-memory {@link AlertRepository}: the test pushes values into {@link #feed}. */
public final class FakeAlertRepository implements AlertRepository {

    public final MutableLiveData<Outcome<List<IdentityAlert>>> feed = new MutableLiveData<>();
    public final List<String> reports = new ArrayList<>();
    public final List<String> observed = new ArrayList<>();
    public int purges;

    @Override
    public void report(
            @NonNull String uid,
            @NonNull String email,
            @NonNull IdentityAlert.Type type,
            @Nullable String enteredRollNo,
            @NonNull String audience,
            @NonNull Runnable whenDone) {
        reports.add(type + ":" + email + ":" + enteredRollNo + ":" + audience);
        whenDone.run();
    }

    @NonNull
    @Override
    public LiveData<Outcome<List<IdentityAlert>>> observe(@NonNull String teacherUid) {
        observed.add(teacherUid);
        return feed;
    }

    @Override
    public void purgeExpired(@NonNull String teacherUid) {
        purges++;
    }
}
