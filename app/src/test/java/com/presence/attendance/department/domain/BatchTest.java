package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BatchTest {

    private final Batch mca = new Batch("MCA", 2025, 2, 3, 70, 4);

    @Test
    public void theIdIsTheProgrammeAndTheYearsLastTwoDigits() {
        assertEquals("MCA25", mca.getId());
        assertEquals("MCA10", Batch.idOf("MCA", 2010));
    }

    @Test
    public void theNameShowsBothYears() {
        assertEquals("MCA 2025–27", mca.displayName());
        assertEquals("MCA 2098–01", new Batch("MCA", 2098, 3, 1, 70, 4).displayName());
    }

    @Test
    public void aTwoYearProgrammeHasFourSemesters() {
        assertEquals(4, mca.semesterCount());
        assertFalse(mca.isInLastSemester());
        assertTrue(new Batch("MCA", 2025, 2, 4, 70, 4).isInLastSemester());
    }

    @Test
    public void theRulesLimitsAreCheckedHereToo() {
        assertTrue(Batch.isProgramme("MCA"));
        assertFalse(Batch.isProgramme("mca"));
        assertFalse(Batch.isProgramme("M"));
        assertFalse(Batch.isProgramme("MCAPLUS"));
        assertEquals("MCA", Batch.normaliseProgramme(" mca "));
        assertTrue(Batch.isStartYear(2010));
        assertFalse(Batch.isStartYear(2009));
        assertFalse(Batch.isStartYear(2100));
        assertFalse(Batch.isYears(0));
        assertFalse(Batch.isYears(6));
        assertTrue(Batch.isIntake(500));
        assertFalse(Batch.isIntake(501));
        assertTrue(Batch.isLabGroups(10));
        assertFalse(Batch.isLabGroups(11));
    }
}
