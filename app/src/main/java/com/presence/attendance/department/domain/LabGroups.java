package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.roster.domain.RosterEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits a batch into lab groups the way the college does: students in roll-number order, cut into
 * runs as equal as they can be, the earlier groups taking one extra student each when the numbers
 * do not divide (70 into 4 is 18, 18, 17, 17).
 */
public final class LabGroups {

    /** One group of a plan: how many students, and the first and last roll numbers in it. */
    public static final class Range {
        public final int group;
        public final int count;

        /** Null for an empty group, when there are fewer students than groups. */
        @Nullable public final String firstRollNo;

        @Nullable public final String lastRollNo;

        Range(int group, int count, @Nullable String firstRollNo, @Nullable String lastRollNo) {
            this.group = group;
            this.count = count;
            this.firstRollNo = firstRollNo;
            this.lastRollNo = lastRollNo;
        }
    }

    /** The split, and how it differs from the groups students have now. Immutable. */
    public static final class Plan {
        @NonNull public final List<Range> ranges;

        /** Each student's group by address. */
        @NonNull public final Map<String, Integer> groupByEmail;

        /** The students whose group would change, in roll-number order. */
        @NonNull public final List<RosterEntry> changes;

        Plan(
                @NonNull List<Range> ranges,
                @NonNull Map<String, Integer> groupByEmail,
                @NonNull List<RosterEntry> changes) {
            this.ranges = Collections.unmodifiableList(ranges);
            this.groupByEmail = Collections.unmodifiableMap(groupByEmail);
            this.changes = Collections.unmodifiableList(changes);
        }
    }

    private LabGroups() {}

    /**
     * @param students one batch's students, in any order
     * @param groups the batch's number of lab groups, at least 1
     */
    @NonNull
    public static Plan plan(@NonNull List<RosterEntry> students, int groups) {
        if (groups < 1) {
            throw new IllegalArgumentException("At least one group");
        }
        final List<RosterEntry> ordered = new ArrayList<>(students);
        ordered.sort(Comparator.comparing(RosterEntry::getRollNo));
        final int base = ordered.size() / groups;
        final int extra = ordered.size() % groups;
        final List<Range> ranges = new ArrayList<>();
        final Map<String, Integer> groupByEmail = new HashMap<>();
        final List<RosterEntry> changes = new ArrayList<>();
        int next = 0;
        for (int group = 1; group <= groups; group++) {
            final int count = base + (group <= extra ? 1 : 0);
            final List<RosterEntry> run = ordered.subList(next, next + count);
            for (RosterEntry student : run) {
                groupByEmail.put(student.getEmail(), group);
                if (!Objects.equals(student.getLabGroup(), group)) {
                    changes.add(student);
                }
            }
            ranges.add(
                    new Range(
                            group,
                            count,
                            run.isEmpty() ? null : run.get(0).getRollNo(),
                            run.isEmpty() ? null : run.get(run.size() - 1).getRollNo()));
            next += count;
        }
        return new Plan(ranges, groupByEmail, changes);
    }
}
