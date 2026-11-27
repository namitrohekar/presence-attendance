package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * What the roster keeps about a student besides who they are: the official name, the lab batch if
 * any, and the emoji they are shown with. Changing these never touches the registration. Immutable.
 */
public final class StudentDetails {

    @NonNull private final String name;
    @Nullable private final Integer batch;
    @NonNull private final StudentEmoji emoji;

    /**
     * @param name a normalised, valid name ({@link StudentIdentity#isName})
     * @param batch a valid batch ({@link StudentIdentity#isBatch}), or null for none
     */
    public StudentDetails(
            @NonNull String name, @Nullable Integer batch, @NonNull StudentEmoji emoji) {
        this.name = name;
        this.batch = batch;
        this.emoji = emoji;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public Integer getBatch() {
        return batch;
    }

    @NonNull
    public StudentEmoji getEmoji() {
        return emoji;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StudentDetails)) {
            return false;
        }
        final StudentDetails details = (StudentDetails) other;
        return name.equals(details.name)
                && Objects.equals(batch, details.batch)
                && emoji == details.emoji;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, batch, emoji);
    }
}
