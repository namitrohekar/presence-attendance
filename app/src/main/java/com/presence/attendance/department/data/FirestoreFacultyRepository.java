package com.presence.attendance.department.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.FacultyRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@link FacultyRepository} backed by Cloud Firestore; the rules let only the HOD write. */
public final class FirestoreFacultyRepository implements FacultyRepository {

    private static final String TAG = "FacultyRepository";
    private static final String FACULTY = "faculty";

    private final FirebaseFirestore firestore;

    public FirestoreFacultyRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void loadFaculty(@NonNull Callback<List<FacultyMember>> callback) {
        faculty()
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<FacultyMember> members = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final FacultyMember member = DepartmentMappers.faculty(document);
                        if (member == null) {
                            Log.w(TAG, "Skipping a malformed faculty entry", null);
                        } else {
                            members.add(member);
                        }
                    }
                    members.sort(Comparator.comparing(FacultyMember::getName));
                    callback.onResult(Outcome.success(members));
                });
    }

    @Override
    public void addFaculty(
            @NonNull User hod,
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<AddOutcome> callback) {
        final DocumentReference ref = faculty().document(email);
        firestore
                .runTransaction(transaction -> {
                    if (transaction.get(ref).exists()) {
                        return AddOutcome.EXISTS;
                    }
                    transaction.set(
                            ref, DepartmentMappers.newFaculty(email, name, initials, hod.getUid()));
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
    public void updateFaculty(
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<ChangeOutcome> callback) {
        final DocumentReference ref = faculty().document(email);
        firestore
                .runTransaction(transaction -> {
                    if (!transaction.get(ref).exists()) {
                        return ChangeOutcome.NOT_FOUND;
                    }
                    transaction.update(ref, DepartmentMappers.facultyUpdate(name, initials));
                    return ChangeOutcome.DONE;
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
    public void removeFaculty(@NonNull String email, @NonNull Callback<ChangeOutcome> callback) {
        final DocumentReference ref = faculty().document(email);
        firestore
                .runTransaction(transaction -> {
                    if (!transaction.get(ref).exists()) {
                        return ChangeOutcome.NOT_FOUND;
                    }
                    transaction.delete(ref);
                    return ChangeOutcome.DONE;
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

    private CollectionReference faculty() {
        return firestore.collection(FACULTY);
    }
}
