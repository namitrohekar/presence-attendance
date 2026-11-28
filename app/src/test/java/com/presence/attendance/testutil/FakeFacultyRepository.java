package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.FacultyRepository;
import java.util.ArrayList;
import java.util.List;

/** In-memory {@link FacultyRepository}. Answers synchronously and records every write. */
public final class FakeFacultyRepository implements FacultyRepository {

    public final List<FacultyMember> members = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();

    /** When set, every call fails with it. */
    public AppError failure;

    @Override
    public void loadFaculty(@NonNull Callback<List<FacultyMember>> callback) {
        callback.onResult(
                failure != null
                        ? Outcome.failure(failure)
                        : Outcome.success(new ArrayList<>(members)));
    }

    @Override
    public void addFaculty(
            @NonNull User hod,
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<AddOutcome> callback) {
        calls.add("add:" + email + ":" + name + ":" + initials);
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
        } else if (find(email) != null) {
            callback.onResult(Outcome.success(AddOutcome.EXISTS));
        } else {
            members.add(new FacultyMember(email, name, initials));
            callback.onResult(Outcome.success(AddOutcome.ADDED));
        }
    }

    @Override
    public void updateFaculty(
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<ChangeOutcome> callback) {
        calls.add("update:" + email + ":" + name + ":" + initials);
        change(email, new FacultyMember(email, name, initials), callback);
    }

    @Override
    public void removeFaculty(@NonNull String email, @NonNull Callback<ChangeOutcome> callback) {
        calls.add("remove:" + email);
        change(email, null, callback);
    }

    private void change(
            @NonNull String email,
            FacultyMember replacement,
            @NonNull Callback<ChangeOutcome> callback) {
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        final FacultyMember before = find(email);
        if (before == null) {
            callback.onResult(Outcome.success(ChangeOutcome.NOT_FOUND));
            return;
        }
        members.remove(before);
        if (replacement != null) {
            members.add(replacement);
        }
        callback.onResult(Outcome.success(ChangeOutcome.DONE));
    }

    private FacultyMember find(@NonNull String email) {
        for (FacultyMember member : members) {
            if (member.getEmail().equals(email)) {
                return member;
            }
        }
        return null;
    }
}
