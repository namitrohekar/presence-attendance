package com.presence.attendance.roster.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.presence.attendance.roster.domain.RosterCsv.Check;
import com.presence.attendance.roster.domain.RosterCsv.FileException;
import com.presence.attendance.roster.domain.RosterCsv.FileProblem;
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
    private static final String HEADER = "email,roll no,name,cohort\n";
    private static final String FULL_HEADER = "email,roll no,name,cohort,batch,emoji\n";
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String LION = new String(Character.toChars(0x1F981));

    /** The checks of the rows under the four required columns. */
    private static List<Check> checks(String body) {
        return checksOf(HEADER + body);
    }

    private static List<Check> checksOf(String text) {
        final List<Check> checks = new ArrayList<>();
        for (Row row : RosterCsv.parse(text)) {
            checks.add(row.check);
        }
        return checks;
    }

    private static FileProblem problem(String text) {
        return assertThrows(FileException.class, () -> RosterCsv.parse(text)).problem;
    }

    @Test
    public void validRowsAreReadyAndNormalised() {
        final List<Row> rows =
                RosterCsv.parse(
                        FULL_HEADER
                                + "  APawar_MC26@mc.vjti.ac.in , 262011005 ,  Aditi   Pawar ,"
                                + " mc26 , 2 , fox\n"
                                + B
                                + ",262010006,Bhavesh Kale,MC26,,");
        assertEquals(2, rows.size());
        final Row first = rows.get(0);
        assertEquals(Check.READY, first.check);
        assertEquals(A, first.email);
        assertEquals("262011005", first.rollNo);
        assertEquals("MC26", first.cohort);
        assertEquals("Aditi Pawar", first.name);
        assertEquals(new StudentDetails("Aditi Pawar", 2, StudentEmoji.FOX), first.details);
        assertEquals(1, first.line);
        assertEquals(2, rows.get(1).line);
    }

    @Test
    public void anEmptyEmojiIsChosenByTheRollNumberAndAnEmptyBatchIsNone() {
        final Row row = RosterCsv.parse(FULL_HEADER + A + ",262011005,Aditi Pawar,MC26,,").get(0);
        // The last digit 5 is the sixth emoji.
        assertEquals(new StudentDetails("Aditi Pawar", null, StudentEmoji.OWL), row.details);
        assertNull("the file gave no emoji", row.emoji);
    }

    @Test
    public void columnsMayComeInAnyOrderWithOtherColumnsIgnored() {
        final List<Row> rows =
                RosterCsv.parse(
                        "Sr. No.,Name of Student,Roll No.,Email ID,Class,Phone,Batch\n"
                                + "1,Aditi Pawar,262011005,"
                                + A
                                + ",MC26,9800000000,3\n");
        assertEquals(Check.READY, rows.get(0).check);
        assertEquals(A, rows.get(0).email);
        assertEquals(new StudentDetails("Aditi Pawar", 3, StudentEmoji.OWL), rows.get(0).details);
    }

    @Test
    public void anEmojiMayBeThePictureItsNameOrItsNumber() {
        final String body =
                A
                        + ",262011005,Aditi Pawar,MC26,,"
                        + LION
                        + "\n"
                        + B
                        + ",262010006,Bhavesh Kale,MC26,,Peacock\n"
                        + "cmore_mc26@mc.vjti.ac.in,262010007,Chirag More,MC26,,5\n";
        final List<Row> rows = RosterCsv.parse(FULL_HEADER + body);
        assertEquals(StudentEmoji.LION, rows.get(0).details.getEmoji());
        assertEquals(StudentEmoji.PEACOCK, rows.get(1).details.getEmoji());
        assertEquals(StudentEmoji.FOX, rows.get(2).details.getEmoji());
    }

    @Test
    public void aBomBlankLinesAndWindowsLineEndingsAreHandled() {
        final String text =
                BOM
                        + "email,roll no,name,cohort\r\n"
                        + A
                        + ",262011005,Aditi Pawar,MC26\r\n\r\n   \r\n,,,\r\n"
                        + B
                        + ",262010006,Bhavesh Kale,MC26\r\n";
        assertEquals(Arrays.asList(Check.READY, Check.READY), checksOf(text));
        assertEquals(
                "headings are compared without case",
                1,
                RosterCsv.parse("EMAIL,ROLLNO,NAME,COHORT\n" + A + ",262011005,A P,MC26").size());
    }

    @Test
    public void quotedCellsMayHoldCommasQuotesAndLineBreaks() {
        final List<Row> rows =
                RosterCsv.parse(HEADER + "\"" + A + "\",\"262011005\",\"Pawar, Aditi\",\"MC26\"");
        assertEquals(Check.READY, rows.get(0).check);
        assertEquals("Pawar, Aditi", rows.get(0).name);
        // A quoted comma stays inside its cell, so this is a bad address.
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("\"a,b@mc.vjti.ac.in\",262011005,A P,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_COHORT),
                checks(A + ",262011005,A P,\"MC\n26\""));
        assertEquals(
                "a line break inside a name becomes a space",
                "Aditi Pawar",
                RosterCsv.parse(HEADER + A + ",262011005,\"Aditi\nPawar\",MC26").get(0).name);
    }

    @Test
    public void everyKindOfBadRowIsIdentified() {
        assertEquals(Collections.singletonList(Check.MALFORMED), checks(A + ",262011005,A P"));
        assertEquals(Collections.singletonList(Check.MALFORMED), checks("just some text"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@gmail.com,262011005,A P,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@x.mc.vjti.ac.in,262011005,A P,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@mc.vjti.ac.in.evil.com,262011005,A P,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",26201100,A P,MC26"));
        assertEquals(Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",,A P,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_NAME), checks(A + ",262011005,  ,MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_NAME),
                checks(A + ",262011005," + "a".repeat(81) + ",MC26"));
        assertEquals(
                Collections.singletonList(Check.INVALID_COHORT), checks(A + ",262011005,A P,M"));
        assertEquals(
                Collections.singletonList(Check.INVALID_BATCH),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26,0,"));
        assertEquals(
                Collections.singletonList(Check.INVALID_BATCH),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26,21,"));
        assertEquals(
                Collections.singletonList(Check.INVALID_BATCH),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26,B2,"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMOJI),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26,,cat"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMOJI),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26,,11"));
    }

    @Test
    public void aShortRowIsMalformedOnlyWhenARequiredCellIsMissing() {
        assertEquals(
                "missing optional cells at the end are fine",
                Collections.singletonList(Check.READY),
                checksOf(FULL_HEADER + A + ",262011005,A P,MC26"));
    }

    @Test
    public void duplicatesInsideTheFileAreFlaggedOnTheLaterRow() {
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_EMAIL),
                checks(A + ",262011005,A P,MC26\n" + A + ",262010006,A P,MC26"));
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_ROLL_NO),
                checks(A + ",262011005,A P,MC26\n" + B + ",262011005,B K,MC26"));
        assertEquals(
                "the same roll number in another cohort is not a duplicate",
                Arrays.asList(Check.READY, Check.READY),
                checks(A + ",262011005,A P,MC26\n" + B + ",262011005,B K,MC25"));
        assertEquals(
                "an invalid row does not reserve its address",
                Arrays.asList(Check.INVALID_ROLL_NO, Check.READY),
                checks(A + ",bad,A P,MC26\n" + A + ",262011005,A P,MC26"));
    }

    @Test
    public void aFileThatCannotBeUsedIsRefusedOutright() {
        assertEquals(FileProblem.NO_HEADER, problem(""));
        assertEquals(FileProblem.NO_HEADER, problem("\n\n  \n"));
        assertEquals(
                "rows with no header line",
                FileProblem.NO_HEADER,
                problem(A + ",262011005,A P,MC26"));
        assertEquals(
                "the old three columns, without a name",
                FileProblem.NO_HEADER,
                problem("email,rollNo,cohort\n" + A + ",262011005,MC26"));
        assertEquals(FileProblem.EMPTY, problem(HEADER));
        final StringBuilder big = new StringBuilder(HEADER);
        for (int i = 0; i <= RosterCsv.MAX_ROWS; i++) {
            big.append(row(i));
        }
        assertEquals(FileProblem.TOO_LARGE, problem(big.toString()));
    }

    @Test
    public void exactlyTheMaximumNumberOfRowsIsAccepted() {
        final StringBuilder max = new StringBuilder(HEADER);
        for (int i = 0; i < RosterCsv.MAX_ROWS; i++) {
            max.append(row(i));
        }
        assertEquals(RosterCsv.MAX_ROWS, RosterCsv.parse(max.toString()).size());
    }

    private static String row(int i) {
        return "s" + i + "@mc.vjti.ac.in," + String.format("%09d", i) + ",Student " + i + ",MC26\n";
    }

    @Test
    public void rowsAreCheckedAgainstTheExistingRoster() {
        final List<RosterEntry> roster = Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY);
        final String text =
                FULL_HEADER
                        + Fixtures.ENTRY.getEmail()
                        + ",262011001,Meera Joshi,MC26,1,owl\n" // identical: skipped
                        + Fixtures.OTHER_ENTRY.getEmail()
                        + ",262019999,Rohan Patil,MC26,,\n" // same address, other roll
                        + A
                        + ",262010002,Aditi Pawar,MC26,,\n" // roll belongs to someone else
                        + B
                        + ",262011001,Bhavesh Kale,MC25,,\n" // same roll, other cohort: fine
                        + "bad@gmail.com,262011005,X Y,MC26,,"; // already rejected: unchanged
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

    @Test
    public void emptyCellsKeepTheRostersBatchAndEmoji() {
        final List<Row> rows =
                RosterCsv.checkAgainst(
                        RosterCsv.parse(
                                FULL_HEADER
                                        + Fixtures.ENTRY.getEmail()
                                        + ",262011001,Meera Joshi,MC26,,"),
                        Collections.singletonList(Fixtures.ENTRY));
        assertEquals(Check.ALREADY_ON_ROSTER, rows.get(0).check);
    }

    @Test
    public void aChangedNameBatchOrEmojiIsAnUpdateThatKeepsTheRest() {
        final List<Row> rows =
                RosterCsv.checkAgainst(
                        RosterCsv.parse(
                                FULL_HEADER
                                        + Fixtures.ENTRY.getEmail()
                                        + ",262011001,Meera S. Joshi,MC26,,\n"
                                        + Fixtures.OTHER_ENTRY.getEmail()
                                        + ",262010002,Rohan Patil,MC26,4,\n"),
                        Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY));
        assertEquals(Check.DETAILS_CHANGED, rows.get(0).check);
        assertEquals(
                "the batch and emoji stay as the roster has them",
                new StudentDetails("Meera S. Joshi", 1, StudentEmoji.OWL),
                rows.get(0).details);
        assertEquals(Check.DETAILS_CHANGED, rows.get(1).check);
        assertEquals(new StudentDetails("Rohan Patil", 4, StudentEmoji.TIGER), rows.get(1).details);
    }

    @Test
    public void anEntryAddedBeforeNamesGetsItsDetailsFromTheFile() {
        final List<Row> rows =
                RosterCsv.checkAgainst(
                        RosterCsv.parse(
                                HEADER
                                        + Fixtures.LEGACY_ENTRY.getEmail()
                                        + ",262010004,Kunal Desai,MC26\n"),
                        Collections.singletonList(Fixtures.LEGACY_ENTRY));
        assertEquals(Check.DETAILS_CHANGED, rows.get(0).check);
        // 262010004 ends in 4: the fifth emoji.
        assertEquals(
                new StudentDetails("Kunal Desai", null, StudentEmoji.FOX), rows.get(0).details);
    }
}
