package com.presence.attendance.roster.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.presence.attendance.roster.domain.RosterCsv.Check;
import com.presence.attendance.roster.domain.RosterCsv.Row;
import com.presence.attendance.testutil.Fixtures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class RosterCsvTest {

    private static final String A = "apawar_mc26@mc.vjti.ac.in";
    private static final String B = "bkale_mc26@mc.vjti.ac.in";

    private static List<Check> checks(String text) {
        final List<Check> checks = new ArrayList<>();
        for (Row row : RosterCsv.parse(text)) {
            checks.add(row.check);
        }
        return checks;
    }

    @Test
    public void validRowsAreReadyAndNormalised() {
        final List<Row> rows =
                RosterCsv.parse(
                        "  APawar_MC26@mc.vjti.ac.in , 262011005 , mc26 \n"
                                + B
                                + ",262010006,MC26");
        assertEquals(2, rows.size());
        assertEquals(Check.READY, rows.get(0).check);
        assertEquals(A, rows.get(0).email);
        assertEquals("262011005", rows.get(0).rollNo);
        assertEquals("MC26", rows.get(0).cohort);
        assertEquals(1, rows.get(0).line);
        assertEquals(2, rows.get(1).line);
    }

    @Test
    public void aHeaderABomBlankLinesAndWindowsLineEndingsAreHandled() {
        final String text =
                "\uFEFFemail,rollNo,cohort\r\n"
                        + A
                        + ",262011005,MC26\r\n\r\n   \r\n"
                        + B
                        + ",262010006,MC26\r\n";
        assertEquals(Arrays.asList(Check.READY, Check.READY), checks(text));
        assertEquals(
                "the header is case-insensitive",
                1,
                RosterCsv.parse("EMAIL,ROLLNO,COHORT\n" + A + ",262011005,MC26").size());
    }

    @Test
    public void quotedCellsMayHoldCommasQuotesAndLineBreaks() {
        final List<Row> rows = RosterCsv.parse("\"" + A + "\",\"262011005\",\"MC26\"");
        assertEquals(Check.READY, rows.get(0).check);
        // A quoted comma stays inside its cell, so this is three cells with a bad address.
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("\"a,b@mc.vjti.ac.in\",262011005,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("\"a\"\"b@mc.vjti.ac.in\",262011005,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_COHORT),
                checks(A + ",262011005,\"MC\n26\""));
    }

    @Test
    public void everyKindOfBadRowIsIdentified() {
        assertEquals(Collections.singletonList(Check.MALFORMED), checks(A + ",262011005"));
        assertEquals(
                Collections.singletonList(Check.MALFORMED), checks(A + ",262011005,MC26,extra"));
        assertEquals(Collections.singletonList(Check.MALFORMED), checks("just some text"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@gmail.com,262011005,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@x.mc.vjti.ac.in,262011005,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@mc.vjti.ac.in.evil.com,262011005,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",26201100,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",26201100A,MC26"));
        assertEquals(Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",,MC26"));
        assertEquals(Collections.singletonList(Check.INVALID_COHORT), checks(A + ",262011005,M"));
        assertEquals(
                Collections.singletonList(Check.INVALID_COHORT), checks(A + ",262011005,MC-26"));
    }

    @Test
    public void duplicatesInsideTheFileAreFlaggedOnTheLaterRow() {
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_EMAIL),
                checks(A + ",262011005,MC26\n" + A + ",262010006,MC26"));
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_ROLL_NO),
                checks(A + ",262011005,MC26\n" + B + ",262011005,MC26"));
        assertEquals(
                "the same roll number in another cohort is not a duplicate",
                Arrays.asList(Check.READY, Check.READY),
                checks(A + ",262011005,MC26\n" + B + ",262011005,MC25"));
        assertEquals(
                "case differences do not hide a duplicate",
                Arrays.asList(Check.READY, Check.DUPLICATE_EMAIL),
                checks(
                        A
                                + ",262011005,MC26\n"
                                + A.toUpperCase().replace("MC.VJTI.AC.IN", "mc.vjti.ac.in")
                                + ",262010006,MC26"));
        assertEquals(
                "an invalid row does not reserve its address",
                Arrays.asList(Check.INVALID_ROLL_NO, Check.READY),
                checks(A + ",bad,MC26\n" + A + ",262011005,MC26"));
    }

    @Test
    public void emptyAndOversizedFilesAreRefusedOutright() {
        assertThrows(IllegalArgumentException.class, () -> RosterCsv.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RosterCsv.parse("\n\n  \n"));
        assertThrows(
                IllegalArgumentException.class, () -> RosterCsv.parse("email,rollNo,cohort\n"));
        final StringBuilder big = new StringBuilder();
        for (int i = 0; i <= RosterCsv.MAX_ROWS; i++) {
            big.append("s")
                    .append(i)
                    .append("@mc.vjti.ac.in,")
                    .append(String.format("%09d", i))
                    .append(",MC26\n");
        }
        assertThrows(IllegalArgumentException.class, () -> RosterCsv.parse(big.toString()));
    }

    @Test
    public void exactlyTheMaximumNumberOfRowsIsAccepted() {
        final StringBuilder max = new StringBuilder();
        for (int i = 0; i < RosterCsv.MAX_ROWS; i++) {
            max.append("s")
                    .append(i)
                    .append("@mc.vjti.ac.in,")
                    .append(String.format("%09d", i))
                    .append(",MC26\n");
        }
        assertEquals(RosterCsv.MAX_ROWS, RosterCsv.parse(max.toString()).size());
    }

    @Test
    public void rowsAreCheckedAgainstTheExistingRoster() {
        final List<RosterEntry> roster = Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY);
        final String text =
                Fixtures.ENTRY.getEmail()
                        + ",262011001,MC26\n" // identical: skipped
                        + Fixtures.OTHER_ENTRY.getEmail()
                        + ",262019999,MC26\n" // same address, other roll
                        + A
                        + ",262010002,MC26\n" // roll belongs to someone else
                        + B
                        + ",262011001,MC25\n" // same roll, other cohort: fine
                        + "bad@gmail.com,262011005,MC26"; // already rejected: unchanged
        final List<Check> checks = new ArrayList<>();
        for (Row row : RosterCsv.checkAgainst(RosterCsv.parse(text), roster)) {
            checks.add(row.check);
        }
        assertEquals(
                Arrays.asList(
                        Check.ALREADY_ON_ROSTER,
                        Check.EMAIL_ON_ROSTER,
                        Check.ROLL_TAKEN,
                        Check.READY,
                        Check.INVALID_EMAIL),
                checks);
    }
}
