package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * What the roster keeps about a student besides who they are: the official name and, once the HOD
 * has split the batch, the lab group. Changing these never touches the registration. Immutable.
 */
public final class StudentDetails {

    @NonNull private final String name;
    @Nullable private final Integer labGroup;

    /**
     * @param name a normalised, valid name ({@link StudentIdentity#isName})
     * @param labGroup a lab group of the student's batch, or null for none yet
     */
    public StudentDetails(@NonNull String name, @Nullable Integer labGroup) {
        this.name = name;
        this.labGroup = labGroup;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public Integer getLabGroup() {
        return labGroup;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StudentDetails)) {
            return false;
        }
        final StudentDetails details = (StudentDetails) other;
        return name.equals(details.name) && Objects.equals(labGroup, details.labGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, labGroup);
    }
}
