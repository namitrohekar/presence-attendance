package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One student on the faculty-managed roster: the authoritative link between an official address and
 * a roll number within a cohort. Immutable.
 */
public final class RosterEntry {

    @NonNull private final String email;
    @NonNull private final String rollNo;
    @NonNull private final String cohort;
    @NonNull private final String addedBy;
    private final int revision;
    @Nullable private final String registeredUid;

    public RosterEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String addedBy,
            int revision,
            @Nullable String registeredUid) {
        this.email = email;
        this.rollNo = rollNo;
        this.cohort = cohort;
        this.addedBy = addedBy;
        this.revision = revision;
        this.registeredUid = registeredUid;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    @NonNull
    public String getRollNo() {
        return rollNo;
    }

    @NonNull
    public String getCohort() {
        return cohort;
    }

    /** Uid of the faculty member who added the entry; mismatch alerts are addressed to them. */
    @NonNull
    public String getAddedBy() {
        return addedBy;
    }

    /** Number of audited corrections applied to this entry. */
    public int getRevision() {
        return revision;
    }

    /** The sign-in the student registered with, or null if they have not registered. */
    @Nullable
    public String getRegisteredUid() {
        return registeredUid;
    }

    public boolean isRegistered() {
        return registeredUid != null;
    }
}
