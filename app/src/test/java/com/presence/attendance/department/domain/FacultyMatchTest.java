package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class FacultyMatchTest {

    static final FacultyMember NC = new FacultyMember("nc@vjti.ac.in", "Nilima Chavan", "NC");
    static final FacultyMember SJ = new FacultyMember("sj@vjti.ac.in", "Sanjay Joshi", "SJ");
    static final FacultyMember GS = new FacultyMember("gs@vjti.ac.in", "Prof. Gajanan Sidam", "GS");
    static final FacultyMember AP = new FacultyMember("ap@vjti.ac.in", "Amruta Patil", "AP");
    static final FacultyMember AB = new FacultyMember("ab@vjti.ac.in", "Dr. Archana Bhatkar", "AB");
    static final List<FacultyMember> FACULTY = Arrays.asList(NC, SJ, GS, AP, AB);

    @Test
    public void initialsMatchTheFacultyListsInitials() {
        assertEquals(SJ, FacultyMatch.find("SJ", FACULTY));
        assertEquals(GS, FacultyMatch.find(" GS ", FACULTY));
    }

    @Test
    public void aNameMatchesWithoutItsTitleAndMayBeJustTheFirstName() {
        assertEquals(AP, FacultyMatch.find("Dr. Amruta Patil", FACULTY));
        assertEquals(NC, FacultyMatch.find("Prof. Nilima", FACULTY));
        assertEquals(AB, FacultyMatch.find("Prof. Archana", FACULTY));
    }

    @Test
    public void theFirstOfAPairTeachesAndANoteInBracketsIsIgnored() {
        assertEquals(AP, FacultyMatch.find("AP+GS (Rishab Mishra)", FACULTY));
        assertEquals(AB, FacultyMatch.find("Prof. Archana+RM", FACULTY));
    }

    @Test
    public void nobodyOrMoreThanOneIsNoMatch() {
        assertNull(FacultyMatch.find("RM", FACULTY));
        assertNull(FacultyMatch.find("Prof. Bhakti", FACULTY));
        assertNull(FacultyMatch.find("", FACULTY));
        final FacultyMember otherNilima = new FacultyMember("n2@vjti.ac.in", "Nilima Rao", "NR");
        assertNull(FacultyMatch.find("Prof. Nilima", Arrays.asList(NC, otherNilima)));
    }
}
