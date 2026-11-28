package com.presence.attendance.course.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class MemberTest {

    private final Member joined =
            new Member("studentA", "meera j", "mjoshi_mc26@mc.vjti.ac.in", "262011001");

    @Test
    public void theRostersNameAndLabGroupReplaceWhatTheStudentJoinedWith() {
        final Member member = joined.withRoster("Meera Joshi", 2);
        assertEquals("Meera Joshi", member.getName());
        assertEquals(Integer.valueOf(2), member.getLabGroup());
        assertEquals("262011001", member.getRollNo());
    }

    @Test
    public void anEntryAddedBeforeNamesKeepsTheJoinedName() {
        final Member member = joined.withRoster(null, null);
        assertEquals("meera j", member.getName());
        assertNull(member.getLabGroup());
    }
}
