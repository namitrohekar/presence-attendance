package com.presence.attendance.department.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import java.util.List;

/** The faculty list. Only the HOD may write it; faculty may read it. */
public interface FacultyRepository {

    enum AddOutcome {
        ADDED,
        /** That address is on the list already; it is not overwritten. */
        EXISTS
    }

    enum ChangeOutcome {
        DONE,
        NOT_FOUND
    }

    /** Everyone on the list, by name. */
    @MainThread
    void loadFaculty(@NonNull Callback<List<FacultyMember>> callback);

    /** Adds someone, who may never have signed in. Inputs must already be valid. */
    @MainThread
    void addFaculty(
            @NonNull User hod,
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<AddOutcome> callback);

    @MainThread
    void updateFaculty(
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull Callback<ChangeOutcome> callback);

    /** Removes someone: they lose faculty access the next time they sign in or write. */
    @MainThread
    void removeFaculty(@NonNull String email, @NonNull Callback<ChangeOutcome> callback);
}
