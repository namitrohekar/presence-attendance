package com.presence.attendance.course.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.presence.attendance.roster.domain.StudentEmoji;
import org.junit.Test;

public final class MemberTest {

    private final Member joined =
            new Member("studentA", "meera j", "mjoshi_mc26@mc.vjti.ac.in", "262011001");

    @Test
    public void theRostersNameBatchAndEmojiReplaceWhatTheStudentJoinedWith() {
        final Member member = joined.withRoster("Meera Joshi", 2, StudentEmoji.PEACOCK);
        assertEquals("Meera Joshi", member.getName());
        assertEquals(Integer.valueOf(2), member.getBatch());
        assertEquals(StudentEmoji.PEACOCK, member.shownEmoji());
        assertEquals("studentA", member.getStudentId());
        assertEquals("262011001", member.getRollNo());
    }

    @Test
    public void anEntryAddedBeforeNamesKeepsTheJoinedNameAndTheRollNumbersEmoji() {
        final Member member = joined.withRoster(null, null, null);
        assertEquals("meera j", member.getName());
        assertNull(member.getBatch());
        // 262011001 ends in 1: the second emoji.
        assertEquals(StudentEmoji.TIGER, member.shownEmoji());
    }
}
