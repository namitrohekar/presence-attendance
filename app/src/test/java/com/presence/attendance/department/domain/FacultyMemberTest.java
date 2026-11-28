package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FacultyMemberTest {

    @Test
    public void anAddressInAnyDomainButOneShapeOnly() {
        assertTrue(FacultyMember.isStaffEmail("gsidam@vjti.ac.in"));
        assertTrue(FacultyMember.isStaffEmail("namit.r+hod@gmail.com"));
        assertFalse(FacultyMember.isStaffEmail("Gsidam@vjti.ac.in"));
        assertFalse(FacultyMember.isStaffEmail("gsidam"));
        assertFalse(FacultyMember.isStaffEmail("g s@vjti.ac.in"));
        assertFalse(FacultyMember.isStaffEmail("gsidam@vjti"));
        assertEquals("gsidam@vjti.ac.in", FacultyMember.normaliseEmail(" GSidam@VJTI.ac.in "));
    }

    @Test
    public void initialsAreOneToFourCapitals() {
        assertEquals("SJ", FacultyMember.normaliseInitials(" s. j. "));
        assertTrue(FacultyMember.isInitials("SJ"));
        assertTrue(FacultyMember.isInitials("ABCD"));
        assertFalse(FacultyMember.isInitials("ABCDE"));
        assertFalse(FacultyMember.isInitials(""));
        assertFalse(FacultyMember.isInitials("S1"));
    }
}
