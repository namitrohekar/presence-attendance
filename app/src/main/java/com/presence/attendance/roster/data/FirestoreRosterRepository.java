package com.presence.attendance.roster.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.core.data.Transactions;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link RosterRepository} backed by Cloud Firestore. The transactions are the ones replayed
 * against the security rules by {@code firebase/test/flows.js}.
 */
public final class FirestoreRosterRepository implements RosterRepository {

    private static final String TAG = "RosterRepository";
    public static final String REGISTRY = "studentRegistry";
    private static final String ROLL_INDEX = "rollIndex";
    private static final String CORRECTIONS = "registryCorrections";

    private final FirebaseFirestore firestore;

    public FirestoreRosterRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void loadRoster(@NonNull Callback<List<RosterEntry>> callback) {
        registry()
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<RosterEntry> roster = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final RosterEntry entry = RosterMappers.entry(document);
                        if (entry == null) {
                            Log.w(TAG, "Skipping a malformed roster entry", null);
                        } else {
                            roster.add(entry);
                        }
                    }
                    roster.sort(
                            Comparator.comparing(RosterEntry::getCohort)
                                    .thenComparing(RosterEntry::getRollNo));
                    callback.onResult(Outcome.success(roster));
                });
    }

    @Override
    public void addStudent(
            @NonNull User teacher,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull Callback<AddOutcome> callback) {
        final DocumentReference entry = registry().document(email);
        final DocumentReference index = index(cohort, rollNo);
        Transactions.runDecidingAgainIfDenied(
                        firestore,
                        transaction -> {
                            // The rules refuse both cases too; reading first tells them apart
                            // and guarantees nothing is overwritten.
                            if (transaction.get(entry).exists()) {
                                return AddOutcome.EMAIL_EXISTS;
                            }
                            if (transaction.get(index).exists()) {
                                return AddOutcome.ROLL_TAKEN;
                            }
                            transaction.set(
                                    entry,
                                    RosterMappers.newEntry(
                                            email, rollNo, cohort, teacher.getUid()));
                            transaction.set(index, RosterMappers.index(email));
                            return AddOutcome.ADDED;
                        })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    @Override
    public void correct(
            @NonNull User teacher,
            @NonNull String email,
            @NonNull CorrectionAction action,
            @NonNull String reason,
            @Nullable String newRollNo,
            @Nullable String newCohort,
            @NonNull Callback<CorrectOutcome> callback) {
        final DocumentReference entryRef = registry().document(email);
        Transactions.runDecidingAgainIfDenied(
                        firestore,
                        transaction -> {
                            final DocumentSnapshot snapshot = transaction.get(entryRef);
                            final RosterEntry before = RosterMappers.entry(snapshot);
                            final Timestamp addedAt =
                                    Snapshots.timestamp(snapshot, RosterMappers.ADDED_AT);
                            if (before == null || addedAt == null) {
                                return CorrectOutcome.NOT_FOUND;
                            }
                            final boolean update = action == CorrectionAction.UPDATE;
                            final String rollNo =
                                    update && newRollNo != null ? newRollNo : before.getRollNo();
                            final String cohort =
                                    update && newCohort != null ? newCohort : before.getCohort();
                            final DocumentReference oldIndex =
                                    index(before.getCohort(), before.getRollNo());
                            final DocumentReference newIndex = index(cohort, rollNo);
                            // All reads come before the first write.
                            if (update && transaction.get(newIndex).exists()) {
                                return CorrectOutcome.ROLL_TAKEN;
                            }
                            final boolean remove = action == CorrectionAction.REMOVE;
                            transaction.set(
                                    corrections()
                                            .document(RosterMappers.correctionId(before, addedAt)),
                                    RosterMappers.correction(
                                            before,
                                            action,
                                            reason,
                                            teacher.getUid(),
                                            remove ? null : rollNo,
                                            remove ? null : cohort));
                            if (remove) {
                                transaction.delete(entryRef);
                                transaction.delete(oldIndex);
                                return CorrectOutcome.DONE;
                            }
                            transaction.set(
                                    entryRef,
                                    RosterMappers.correctedEntry(before, addedAt, rollNo, cohort));
                            if (update) {
                                transaction.delete(oldIndex);
                                transaction.set(newIndex, RosterMappers.index(email));
                            }
                            return CorrectOutcome.DONE;
                        })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    private static <T> void fail(@NonNull Callback<T> callback, @Nullable Exception error) {
        callback.onResult(Outcome.failure(FirebaseErrors.toAppError(error)));
    }

    private CollectionReference registry() {
        return firestore.collection(REGISTRY);
    }

    private CollectionReference corrections() {
        return firestore.collection(CORRECTIONS);
    }

    private DocumentReference index(@NonNull String cohort, @NonNull String rollNo) {
        return firestore.collection(ROLL_INDEX).document(RosterMappers.indexKey(cohort, rollNo));
    }
}
