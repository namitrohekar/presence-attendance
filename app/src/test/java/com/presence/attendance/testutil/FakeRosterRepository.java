package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.roster.domain.StudentDetails;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link RosterRepository}. Answers synchronously and records every call. Outcomes for
 * individual addresses can be set, so an import can be given a mix of results.
 */
public final class FakeRosterRepository implements RosterRepository {

    public Outcome<List<RosterEntry>> rosterOutcome = Outcome.success(new ArrayList<>());
    public Outcome<AddOutcome> addOutcome = Outcome.success(AddOutcome.ADDED);
    public final Map<String, Outcome<AddOutcome>> addOutcomeByEmail = new HashMap<>();
    public Outcome<CorrectOutcome> correctOutcome = Outcome.success(CorrectOutcome.DONE);
    public Outcome<CorrectOutcome> detailsOutcome = Outcome.success(CorrectOutcome.DONE);

    public final List<String> calls = new ArrayList<>();
    public int loads;

    public void failAdd(@NonNull String email, @NonNull AppError error) {
        addOutcomeByEmail.put(email, Outcome.failure(error));
    }

    @Override
    public void loadRoster(@NonNull Callback<List<RosterEntry>> callback) {
        loads++;
        callback.onResult(rosterOutcome);
    }

    /** The same entries as {@link #rosterOutcome}, kept to the batch asked for. */
    @Override
    public void loadBatchRoster(
            @NonNull String batchId, @NonNull Callback<List<RosterEntry>> callback) {
        loads++;
        if (!rosterOutcome.isSuccess()) {
            callback.onResult(rosterOutcome);
            return;
        }
        final List<RosterEntry> batch = new ArrayList<>();
        for (RosterEntry entry : rosterOutcome.getValue()) {
            if (entry.getCohort().equals(batchId)) {
                batch.add(entry);
            }
        }
        callback.onResult(Outcome.success(batch));
    }

    @Override
    public void addStudent(
            @NonNull User teacher,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull StudentDetails details,
            @NonNull Callback<AddOutcome> callback) {
        calls.add("add:" + email + ":" + rollNo + ":" + cohort + ":" + describe(details));
        final Outcome<AddOutcome> specific = addOutcomeByEmail.get(email);
        callback.onResult(specific != null ? specific : addOutcome);
    }

    @Override
    public void updateDetails(
            @NonNull String email,
            @NonNull StudentDetails details,
            @NonNull Callback<CorrectOutcome> callback) {
        calls.add("details:" + email + ":" + describe(details));
        callback.onResult(detailsOutcome);
    }

    /** "Name/labGroup", with "-" for no lab group. */
    @NonNull
    public static String describe(@NonNull StudentDetails details) {
        return details.getName()
                + "/"
                + (details.getLabGroup() == null ? "-" : details.getLabGroup());
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
        calls.add(action + ":" + email + ":" + newRollNo + ":" + newCohort + ":" + reason);
        callback.onResult(correctOutcome);
    }
}
