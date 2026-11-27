package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.roster.domain.StudentEmoji;

/**
 * A student enrolled in a course, as their teacher sees them: the name from the roster when it has
 * one, else the name they joined with. Immutable.
 */
public final class Member {

    @NonNull private final String studentId;
    @NonNull private final String name;
    @NonNull private final String email;
    @NonNull private final String rollNo;
    @Nullable private final Integer batch;
    @Nullable private final StudentEmoji emoji;

    public Member(
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo) {
        this(studentId, name, email, rollNo, null, null);
    }

    public Member(
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo,
            @Nullable Integer batch,
            @Nullable StudentEmoji emoji) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.rollNo = rollNo;
        this.batch = batch;
        this.emoji = emoji;
    }

    /**
     * This member with what the roster says about them now. A detail the roster lacks keeps the
     * member's own: an entry added before names were kept leaves the name they joined with.
     */
    @NonNull
    public Member withRoster(
            @Nullable String rosterName,
            @Nullable Integer rosterBatch,
            @Nullable StudentEmoji rosterEmoji) {
        return new Member(
                studentId,
                rosterName != null ? rosterName : name,
                email,
                rollNo,
                rosterBatch,
                rosterEmoji != null ? rosterEmoji : emoji);
    }

    @NonNull
    public String getStudentId() {
        return studentId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    /** The roll number recorded when the student joined; empty for a pre-roster membership. */
    @NonNull
    public String getRollNo() {
        return rollNo;
    }

    /** The lab batch on the roster, or null. */
    @Nullable
    public Integer getBatch() {
        return batch;
    }

    /** The emoji to show beside the name: the roster's, or the roll number's. */
    @NonNull
    public StudentEmoji shownEmoji() {
        return StudentEmoji.orForRollNo(emoji, rollNo);
    }
}
