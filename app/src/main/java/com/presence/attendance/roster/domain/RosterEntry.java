package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One student on the roster the HOD keeps: the authoritative link between an official address and a
 * roll number within a batch (the cohort is the batch's id), with the student's official name and
 * lab group. Immutable.
 */
public final class RosterEntry {

    @NonNull private final String email;
    @NonNull private final String rollNo;
    @NonNull private final String cohort;
    @NonNull private final String addedBy;
    private final int revision;
    @Nullable private final String registeredUid;
    @Nullable private final String name;
    @Nullable private final Integer labGroup;

    /** An entry added before names were kept. */
    public RosterEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String addedBy,
            int revision,
            @Nullable String registeredUid) {
        this(email, rollNo, cohort, addedBy, revision, registeredUid, null, null);
    }

    public RosterEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String addedBy,
            int revision,
            @Nullable String registeredUid,
            @Nullable String name,
            @Nullable Integer labGroup) {
        this.email = email;
        this.rollNo = rollNo;
        this.cohort = cohort;
        this.addedBy = addedBy;
        this.revision = revision;
        this.registeredUid = registeredUid;
        this.name = name;
        this.labGroup = labGroup;
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

    /** The official name, or null for an entry added before names were kept. */
    @Nullable
    public String getName() {
        return name;
    }

    /** The lab group, or null before the HOD has split the batch. */
    @Nullable
    public Integer getLabGroup() {
        return labGroup;
    }

    /** The name to show: the official one, or the address for an entry added before names. */
    @NonNull
    public String displayName() {
        return name != null ? name : email;
    }

    /** The details as they are, or null if the entry has no name yet. */
    @Nullable
    public StudentDetails details() {
        return name == null ? null : new StudentDetails(name, labGroup);
    }

    /** This entry with other details, as it is after an update. */
    @NonNull
    public RosterEntry withDetails(@NonNull StudentDetails details) {
        return new RosterEntry(
                email,
                rollNo,
                cohort,
                addedBy,
                revision,
                registeredUid,
                details.getName(),
                details.getLabGroup());
    }
}
