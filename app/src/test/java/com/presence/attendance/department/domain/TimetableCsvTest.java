package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.presence.attendance.department.domain.TimetableCsv.Check;
import com.presence.attendance.department.domain.TimetableCsv.Row;
import com.presence.attendance.department.domain.TimetableSlot.Type;
import com.presence.attendance.roster.domain.RosterCsv.FileException;
import com.presence.attendance.roster.domain.RosterCsv.FileProblem;
import com.presence.attendance.testutil.TimetableFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class TimetableCsvTest {

    private static final String HEADER = "day,start,end,code,subject,type,batch,faculty,room\n";

    private static List<Check> checks(String body) {
        final List<Check> checks = new ArrayList<>();
        for (Row row : TimetableCsv.parse(HEADER + body, 4)) {
            checks.add(row.check);
        }
        return checks;
    }

    @Test
    public void theDepartmentsTimetableIsReadWhole() {
        final List<Row> rows = TimetableCsv.parse(TimetableFixture.FYMCA, 4);
        assertEquals(38, rows.size());
        for (Row row : rows) {
            assertEquals("row " + row.line, Check.READY, row.check);
        }
        final Row first = rows.get(0);
        assertEquals(1, first.day);
        assertEquals("10:30", first.start);
        assertEquals("12:25", first.end);
        assertEquals("R5MC5016L", first.code);
        assertEquals(Type.LAB, first.type);
        assertEquals(1, first.labGroup);
        assertEquals("NC", first.faculty);
        assertEquals("AL-301", first.room);
    }

    @Test
    public void aSubjectWithoutACodeGetsOneFromItsName() {
        final Row row =
                TimetableCsv.parse(HEADER + "FRI,13:30,14:25,,Accounting and Finance,Lecture,,,", 4)
                        .get(0);
        assertEquals("AF", row.code);
        assertEquals(0, row.labGroup);
    }

    @Test
    public void daysTimesAndTypesAreReadTheWayPeopleWriteThem() {
        assertEquals(1, TimetableCsv.day("Monday"));
        assertEquals(4, TimetableCsv.day("thu."));
        assertEquals(0, TimetableCsv.day("Mo"));
        assertEquals("09:30", TimetableCsv.time("9:30"));
        assertEquals("09:30", TimetableCsv.time("09.30"));
        assertEquals("a college day has no class at 1 a.m.", "13:30", TimetableCsv.time("1:30"));
        assertNull(TimetableCsv.time("25:00"));
        assertNull(TimetableCsv.time("noon"));
        assertEquals(Type.LAB, TimetableCsv.type("Practical"));
        assertEquals(Type.TUTORIAL, TimetableCsv.type("tut"));
        assertNull(TimetableCsv.type("seminar"));
    }

    @Test
    public void aRowWithoutATypeIsALabOnlyWhenItNamesALabGroup() {
        final List<Row> rows =
                TimetableCsv.parse(
                        "day,start,end,subject,batch\nMON,10:30,12:25,OS Lab,B2\nMON,13:30,14:25,OS,\n",
                        4);
        assertEquals(Type.LAB, rows.get(0).type);
        assertEquals(2, rows.get(0).labGroup);
        assertEquals(Type.LECTURE, rows.get(1).type);
    }

    @Test
    public void everyKindOfBadRowIsIdentified() {
        assertEquals(
                List.of(
                        Check.INVALID_DAY,
                        Check.INVALID_TIME,
                        Check.INVALID_TIME,
                        Check.INVALID_SUBJECT,
                        Check.INVALID_TYPE,
                        Check.INVALID_LAB_GROUP,
                        Check.MALFORMED),
                checks(
                        "XYZ,10:30,11:25,C,S,Lecture,,,\n"
                                + "MON,10:30,10:30,C,S,Lecture,,,\n"
                                + "MON,later,11:25,C,S,Lecture,,,\n"
                                + "MON,10:30,11:25,C,,Lecture,,,\n"
                                + "MON,10:30,11:25,C,S,Seminar,,,\n"
                                + "MON,10:30,11:25,C,S,Lab,5,,\n"
                                + "MON,10:30\n"));
    }

    @Test
    public void aFileThatCannotBeUsedIsRefused() {
        assertEquals(
                FileProblem.NO_HEADER,
                assertThrows(
                                FileException.class,
                                () -> TimetableCsv.parse("day,start,subject\n", 4))
                        .problem);
        assertEquals(
                FileProblem.EMPTY,
                assertThrows(FileException.class, () -> TimetableCsv.parse(HEADER, 4)).problem);
        final StringBuilder big = new StringBuilder(HEADER);
        for (int i = 0; i < 201; i++) {
            big.append("MON,10:30,11:25,C,S,Lecture,,,\n");
        }
        assertEquals(
                FileProblem.TOO_LARGE,
                assertThrows(FileException.class, () -> TimetableCsv.parse(big.toString(), 4))
                        .problem);
    }
}
