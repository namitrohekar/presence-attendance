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
    @Nullable private final String name;
    @Nullable private final Integer batch;
    @Nullable private final StudentEmoji emoji;

    /** An entry added before names, batches and emojis were kept. */
    public RosterEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String addedBy,
            int revision,
            @Nullable String registeredUid) {
        this(email, rollNo, cohort, addedBy, revision, registeredUid, null, null, null);
    }

    public RosterEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String addedBy,
            int revision,
            @Nullable String registeredUid,
            @Nullable String name,
            @Nullable Integer batch,
            @Nullable StudentEmoji emoji) {
        this.email = email;
        this.rollNo = rollNo;
        this.cohort = cohort;
        this.addedBy = addedBy;
        this.revision = revision;
        this.registeredUid = registeredUid;
        this.name = name;
        this.batch = batch;
        this.emoji = emoji;
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

    @Nullable
    public Integer getBatch() {
        return batch;
    }

    /** The stored emoji, or null for an entry added before emojis were kept. */
    @Nullable
    public StudentEmoji getEmoji() {
        return emoji;
    }

    /** The emoji to show: the stored one, or the roll number's. */
    @NonNull
    public StudentEmoji shownEmoji() {
        return StudentEmoji.orForRollNo(emoji, rollNo);
    }

    /** The details as they are, or null if the entry has no name yet. */
    @Nullable
    public StudentDetails details() {
        return name == null ? null : new StudentDetails(name, batch, shownEmoji());
    }
}
