package com.presence.attendance.roster.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import java.util.List;

/**
 * The faculty-managed student roster. Every write is one atomic transaction that the security rules
 * verify; nothing is ever silently overwritten.
 */
public interface RosterRepository {

    /** What happened to a request to add a student. */
    enum AddOutcome {
        ADDED,
        /** The address is already on the roster. */
        EMAIL_EXISTS,
        /** The roll number belongs to another address in that cohort. */
        ROLL_TAKEN
    }

    /** The three audited ways to change an existing entry. */
    enum CorrectionAction {
        /** Change the roll number and/or cohort. Releases the registration. */
        UPDATE,
        /** Free the registration so the student can register again, e.g. a new Google account. */
        RELEASE,
        REMOVE
    }

    /** What happened to a correction. */
    enum CorrectOutcome {
        DONE,
        NOT_FOUND,
        ROLL_TAKEN
    }

    /** The whole roster, sorted by cohort and then roll number. */
    @MainThread
    void loadRoster(@NonNull Callback<List<RosterEntry>> callback);

    /** Adds one student. Inputs must already be normalised and valid. */
    @MainThread
    void addStudent(
            @NonNull User teacher,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull StudentDetails details,
            @NonNull Callback<AddOutcome> callback);

    /**
     * Changes a student's name, batch and emoji. No reason is needed and the registration stays:
     * these describe the student, they do not identify them. Resolves to DONE or NOT_FOUND.
     */
    @MainThread
    void updateDetails(
            @NonNull String email,
            @NonNull StudentDetails details,
            @NonNull Callback<CorrectOutcome> callback);

    /**
     * Changes an existing entry together with its immutable audit record.
     *
     * @param newRollNo the corrected roll number for UPDATE; ignored otherwise
     * @param newCohort the corrected cohort for UPDATE; ignored otherwise
     */
    @MainThread
    void correct(
            @NonNull User teacher,
            @NonNull String email,
            @NonNull CorrectionAction action,
            @NonNull String reason,
            @Nullable String newRollNo,
            @Nullable String newCohort,
            @NonNull Callback<CorrectOutcome> callback);
}
