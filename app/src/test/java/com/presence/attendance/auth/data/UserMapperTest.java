package com.presence.attendance.auth.data;

import static org.junit.Assert.assertEquals;

import com.presence.attendance.testutil.DocumentContract;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

public final class UserMapperTest {

    @Test
    public void aNewFacultyProfileHasExactlyTheContractFields() throws IOException {
        final Map<String, Object> document =
                UserMapper.newFacultyDocument("Asha Kulkarni", "asha.kulkarni@example.edu");
        assertEquals(DocumentContract.fields("users", false), new TreeSet<>(document.keySet()));
        assertEquals("FACULTY", document.get("role"));
    }

    @Test
    public void aNewStudentProfileAddsExactlyRollNumberAndCohort() throws IOException {
        final Map<String, Object> document =
                UserMapper.newStudentDocument(
                        "Meera Joshi", "mjoshi_mc26@mc.vjti.ac.in", "262011001", "MC26");
        assertEquals(
                DocumentContract.fields("users.student", false), new TreeSet<>(document.keySet()));
        assertEquals("STUDENT", document.get("role"));
        assertEquals("262011001", document.get("rollNo"));
        assertEquals("MC26", document.get("cohort"));
    }

    @Test
    public void updatesNeverTouchCreatedAt() throws IOException {
        final TreeSet<String> student =
                new TreeSet<>(DocumentContract.fields("users.student", false));
        student.remove("createdAt");
        assertEquals(
                student,
                new TreeSet<>(UserMapper.studentFields("n", "e", "262011001", "MC26").keySet()));
        // Faculty updates name the student fields too, in order to delete them.
        assertEquals(student, new TreeSet<>(UserMapper.facultyFields("n", "e").keySet()));
    }
}
