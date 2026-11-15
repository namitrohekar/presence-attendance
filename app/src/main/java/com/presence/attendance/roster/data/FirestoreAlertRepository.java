package com.presence.attendance.roster.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.roster.domain.AlertIds;
import com.presence.attendance.roster.domain.AlertRepository;
import com.presence.attendance.roster.domain.IdentityAlert;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** {@link AlertRepository} backed by Cloud Firestore. */
public final class FirestoreAlertRepository implements AlertRepository {

    private static final String TAG = "AlertRepository";
    private static final String ALERTS = "identityAlerts";
    private static final int PAGE = 50;

    private final FirebaseFirestore firestore;

    public FirestoreAlertRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void report(
            @NonNull String uid,
            @NonNull String email,
            @NonNull IdentityAlert.Type type,
            @Nullable String enteredRollNo,
            @NonNull String audience,
            @NonNull Runnable whenDone) {
        // The id must match the one the rules rebuild from the server date. If the device
        // clock is on a different UTC day the write is refused, and the alert is simply lost.
        final DocumentReference alert = alerts().document(AlertIds.of(uid, type, Instant.now()));
        final Map<String, Object> data =
                RosterMappers.newAlert(uid, email, type, enteredRollNo, audience);
        // A transaction, not a plain set: offline it fails at once instead of being queued,
        // so whenDone always runs.
        firestore
                .runTransaction(transaction -> {
                    transaction.set(alert, data);
                    return null;
                })
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Alert was not recorded", task.getException());
                    }
                    whenDone.run();
                });
    }

    @NonNull
    @Override
    public LiveData<Outcome<List<IdentityAlert>>> observe(@NonNull String teacherUid) {
        return new AlertsLiveData(
                inbox(teacherUid)
                        .orderBy(RosterMappers.CREATED_AT, Query.Direction.DESCENDING)
                        .limit(PAGE));
    }

    @Override
    public void purgeExpired(@NonNull String teacherUid) {
        final Date cutoff =
                Date.from(Instant.now().minus(Constants.ALERT_RETENTION_DAYS, ChronoUnit.DAYS));
        inbox(teacherUid)
                .whereLessThan(RosterMappers.CREATED_AT, new Timestamp(cutoff))
                .limit(PAGE)
                .get()
                .addOnSuccessListener(expired -> {
                    if (expired.isEmpty()) {
                        return;
                    }
                    final WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot alert : expired.getDocuments()) {
                        batch.delete(alert.getReference());
                    }
                    batch.commit().addOnFailureListener(error -> Log.w(TAG, "Purge failed", error));
                })
                .addOnFailureListener(error -> Log.w(TAG, "Purge query failed", error));
    }

    /** The rules only allow queries constrained to this teacher's audience or ALL. */
    private Query inbox(@NonNull String teacherUid) {
        return alerts().whereIn(
                        RosterMappers.AUDIENCE,
                        Arrays.asList(teacherUid, IdentityAlert.AUDIENCE_ALL));
    }

    private CollectionReference alerts() {
        return firestore.collection(ALERTS);
    }

    /** Attaches the snapshot listener only while something is observing. */
    private static final class AlertsLiveData extends LiveData<Outcome<List<IdentityAlert>>> {

        private final Query query;
        @Nullable private ListenerRegistration registration;

        AlertsLiveData(@NonNull Query query) {
            this.query = query;
        }

        @Override
        protected void onActive() {
            registration =
                    query.addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) {
                            setValue(Outcome.failure(FirebaseErrors.toAppError(error)));
                            return;
                        }
                        final List<IdentityAlert> alerts = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            final IdentityAlert alert = RosterMappers.alert(document);
                            if (alert != null) {
                                alerts.add(alert);
                            }
                        }
                        setValue(Outcome.success(alerts));
                    });
        }

        @Override
        protected void onInactive() {
            if (registration != null) {
                registration.remove();
                registration = null;
            }
        }
    }
}
