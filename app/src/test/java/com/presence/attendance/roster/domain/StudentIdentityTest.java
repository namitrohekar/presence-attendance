package com.presence.attendance.roster.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The same cases the rules suite checks in eligibility.test.js and roster.test.js. */
public final class StudentIdentityTest {

    @Test
    public void theStudentDomainIsMatchedExactly() {
        assertTrue(StudentIdentity.hasStudentDomain("nrohekar_mc26@mc.vjti.ac.in"));
        assertTrue(
                "case is ignored", StudentIdentity.hasStudentDomain("NRohekar_MC26@MC.VJTI.AC.IN"));
        for (String other :
                new String[] {
                    "someone@gmail.com",
                    "someone@vjti.ac.in",
                    "someone@ce.vjti.ac.in",
                    "someone@mail.mc.vjti.ac.in",
                    "someone@xmc.vjti.ac.in",
                    "someone@mc.vjti.ac.in.example.com",
                    "mc.vjti.ac.in@gmail.com",
                    "someone@mc.vjti.ac.in@gmail.com",
                    "someone@gmail.com@mc.vjti.ac.in",
                    "mc.vjti.ac.in",
                    ""
                }) {
            assertFalse(other, StudentIdentity.hasStudentDomain(other));
        }
    }

    @Test
    public void aRosterAddressIsLowerCaseWithAPlainLocalPart() {
        assertTrue(StudentIdentity.isStudentEmail("nrohekar_mc26@mc.vjti.ac.in"));
        assertTrue(StudentIdentity.isStudentEmail("a.b-c_1@mc.vjti.ac.in"));
        assertFalse("capital letters", StudentIdentity.isStudentEmail("NRohekar@mc.vjti.ac.in"));
        assertFalse("empty local part", StudentIdentity.isStudentEmail("@mc.vjti.ac.in"));
        assertFalse("a slash", StudentIdentity.isStudentEmail("a/b@mc.vjti.ac.in"));
        assertFalse("a plus", StudentIdentity.isStudentEmail("a+b@mc.vjti.ac.in"));
        assertFalse("a space", StudentIdentity.isStudentEmail("a b@mc.vjti.ac.in"));
        assertFalse("another domain", StudentIdentity.isStudentEmail("a@gmail.com"));
        assertFalse("a sub-domain", StudentIdentity.isStudentEmail("a@x.mc.vjti.ac.in"));
        assertFalse(
                "65 characters", StudentIdentity.isStudentEmail("a".repeat(65) + "@mc.vjti.ac.in"));
        assertEquals(
                "nrohekar_mc26@mc.vjti.ac.in",
                StudentIdentity.normaliseEmail("  NRohekar_MC26@MC.vjti.ac.in "));
    }

    @Test
    public void aRollNumberIsExactlyNineDigits() {
        assertTrue(StudentIdentity.isRollNo("262010016"));
        assertFalse(StudentIdentity.isRollNo("26201001"));
        assertFalse(StudentIdentity.isRollNo("2620100160"));
        assertFalse(StudentIdentity.isRollNo("26201001A"));
        assertFalse(StudentIdentity.isRollNo("26201 016"));
        assertFalse(StudentIdentity.isRollNo(""));
        assertFalse(
                "non-ASCII digits",
                StudentIdentity.isRollNo("\u0662\u0666\u0662\u0660\u0661\u0660\u0660\u0661\u0666"));
    }

    @Test
    public void aCohortIsTwoToEightCapitalsAndDigits() {
        assertTrue(StudentIdentity.isCohort("MC26"));
        assertTrue(StudentIdentity.isCohort("MC"));
        assertTrue(StudentIdentity.isCohort("MCA2026X"));
        assertFalse(StudentIdentity.isCohort("M"));
        assertFalse(StudentIdentity.isCohort("MCA2026XX"));
        assertFalse(StudentIdentity.isCohort("mc26"));
        assertFalse(StudentIdentity.isCohort("MC-26"));
        assertFalse(StudentIdentity.isCohort("MC 26"));
        assertEquals("MC26", StudentIdentity.normaliseCohort(" mc26 "));
    }

    @Test
    public void aNameIsTrimmedWithSpacesCollapsed() {
        assertEquals("Aditi Pawar", StudentIdentity.normaliseName("  Aditi \t\n  Pawar "));
        assertTrue(StudentIdentity.isName("Aditi Pawar"));
        assertTrue(StudentIdentity.isName("\u0906\u0926\u093f\u0924\u0940"));
        assertTrue(StudentIdentity.isName("a".repeat(80)));
        assertFalse(StudentIdentity.isName("a".repeat(81)));
        assertFalse(StudentIdentity.isName(""));
        assertFalse("not normalised", StudentIdentity.isName(" Aditi"));
    }

    @Test
    public void aBatchIsANumberFromOneToTwentyOrNothing() {
        assertEquals(StudentIdentity.NO_BATCH, StudentIdentity.parseBatch("  "));
        assertEquals(1, StudentIdentity.parseBatch("1"));
        assertEquals(20, StudentIdentity.parseBatch(" 20 "));
        assertEquals(4, StudentIdentity.parseBatch("04"));
        assertEquals(StudentIdentity.INVALID_BATCH, StudentIdentity.parseBatch("0"));
        assertEquals(StudentIdentity.INVALID_BATCH, StudentIdentity.parseBatch("21"));
        assertEquals(StudentIdentity.INVALID_BATCH, StudentIdentity.parseBatch("B2"));
        assertEquals(StudentIdentity.INVALID_BATCH, StudentIdentity.parseBatch("-1"));
        assertEquals(StudentIdentity.INVALID_BATCH, StudentIdentity.parseBatch("100"));
    }
}
