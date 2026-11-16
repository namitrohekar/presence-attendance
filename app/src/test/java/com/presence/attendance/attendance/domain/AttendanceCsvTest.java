package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.attendance.domain.AttendanceCsv.Row;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class AttendanceCsvTest {

    @Test
    public void theFileStartsWithABomAndAHeaderAndUsesCrLf() {
        final String csv =
                AttendanceCsv.build(
                        Collections.singletonList(
                                new Row(
                                        "262010016",
                                        "Meera Joshi",
                                        "m@mc.vjti.ac.in",
                                        3,
                                        4,
                                        75,
                                        false)));
        assertTrue(csv.startsWith(AttendanceCsv.BOM + "Roll number,Name,Email,"));
        final String[] lines = csv.split("\r\n", -1);
        assertEquals("header, one row, and the empty end after the last CRLF", 3, lines.length);
        assertEquals("262010016,Meera Joshi,m@mc.vjti.ac.in,3,4,75,No", lines[1]);
        assertEquals(7, lines[0].split(",").length);
    }

    @Test
    public void anEmptyReportIsJustTheHeader() {
        assertEquals(2, AttendanceCsv.build(Collections.emptyList()).split("\r\n", -1).length);
    }

    @Test
    public void commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"Joshi, Meera\"", AttendanceCsv.text("Joshi, Meera"));
        assertEquals("\"Meera \"\"MJ\"\" Joshi\"", AttendanceCsv.text("Meera \"MJ\" Joshi"));
        assertEquals("\"two\nlines\"", AttendanceCsv.text("two\nlines"));
        assertEquals("plain", AttendanceCsv.text("plain"));
        assertEquals("", AttendanceCsv.text(""));
    }

    @Test
    public void aNameThatLooksLikeAFormulaIsMadeInert() {
        for (String start : new String[] {"=", "+", "-", "@", "\t", "\r"}) {
            final String field = AttendanceCsv.text(start + "HYPERLINK(1)");
            final String unquoted = field.startsWith("\"") ? field.substring(1) : field;
            assertTrue(start, unquoted.startsWith("'" + start));
        }
        // Neutralised first, then quoted, so the quote cannot be used to hide the formula.
        assertEquals("\"'=1+1,2\"", AttendanceCsv.text("=1+1,2"));
        assertEquals("a=b is not at the start", "a=b", AttendanceCsv.text("a=b"));
    }

    @Test
    public void theBelowRequirementColumnIsInWords() {
        final String csv =
                AttendanceCsv.build(
                        Arrays.asList(
                                new Row("1", "A", "a@x", 1, 4, 25, true),
                                new Row("2", "B", "b@x", 4, 4, 100, false)));
        assertTrue(csv.contains("1,A,a@x,1,4,25,Yes\r\n"));
        assertTrue(csv.contains("2,B,b@x,4,4,100,No\r\n"));
    }
}
