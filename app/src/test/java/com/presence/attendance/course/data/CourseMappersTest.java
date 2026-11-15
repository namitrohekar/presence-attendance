package com.presence.attendance.course.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.presence.attendance.testutil.DocumentContract;
import com.presence.attendance.testutil.Fixtures;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

/** The documents the app writes must have exactly the fields the security rules whitelist. */
public final class CourseMappersTest {

    @Test
    public void aNewCourseHasExactlyTheContractFields() throws IOException {
        final Map<String, Object> document = CourseMappers.newCourse(Fixtures.COURSE);
        assertEquals(DocumentContract.fields("courses", false), new TreeSet<>(document.keySet()));
        assertEquals(Fixtures.TEACHER.getUid(), document.get("teacherId"));
        assertEquals("the rules require an integer threshold", 75, document.get("threshold"));
    }

    @Test
    public void aNewJoinCodeHasExactlyTheContractFields() throws IOException {
        final Map<String, Object> document =
                CourseMappers.newJoinCode(Fixtures.COURSE, Fixtures.TEACHER);
        assertEquals(DocumentContract.fields("joinCodes", false), new TreeSet<>(document.keySet()));
        assertEquals(Fixtures.COURSE.getId(), document.get("courseId"));
        assertEquals(Fixtures.COURSE.getName(), document.get("courseName"));
        assertEquals(Fixtures.TEACHER.getName(), document.get("teacherName"));
        assertEquals(Fixtures.TEACHER.getUid(), document.get("teacherId"));
    }

    @Test
    public void aMembershipCarriesTheStudentsRegisteredRollNumberAndCohort() throws IOException {
        final Map<String, Object> document =
                CourseMappers.newMembership(Fixtures.STUDENT, Fixtures.JOIN_CODE);
        assertEquals(
                DocumentContract.fields("memberships", false), new TreeSet<>(document.keySet()));
        assertEquals("the teacher comes from the join code", "teacherA", document.get("teacherId"));
        assertEquals("studentA", document.get("studentId"));
        assertEquals(Fixtures.STUDENT.getName(), document.get("name"));
        assertEquals(Fixtures.STUDENT.getEmail(), document.get("email"));
        assertEquals("262011001", document.get("rollNo"));
        assertEquals("MC26", document.get("cohort"));
        assertEquals("ABC234", document.get("joinCode"));
    }

    @Test
    public void anUnregisteredAccountCannotBuildAMembership() {
        assertThrows(
                IllegalStateException.class,
                () -> CourseMappers.newMembership(Fixtures.TEACHER, Fixtures.JOIN_CODE));
    }
}
