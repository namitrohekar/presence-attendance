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
    private static final String BATCH = "MC26";
    private static final int GROUPS = 4;
    private static final String HEADER = "email,roll no,name\n";
    private static final String WITH_GROUP = "email,roll no,name,lab group\n";
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static List<Row> parse(String text) {
        return RosterCsv.parse(text, BATCH, GROUPS);
    }

    private static List<Check> checks(String body) {
        return checksOf(HEADER + body);
    }

    private static List<Check> checksOf(String text) {
        final List<Check> checks = new ArrayList<>();
        for (Row row : parse(text)) {
            checks.add(row.check);
        }
        return checks;
    }

    private static FileProblem problem(String text) {
        return assertThrows(FileException.class, () -> parse(text)).problem;
    }

    @Test
    public void validRowsAreReadyAndNormalised() {
        final List<Row> rows =
                parse(
                        WITH_GROUP
                                + "  APawar_MC26@mc.vjti.ac.in , 262011005 ,  Aditi   Pawar , 2\n"
                                + B
                                + ",262010006,Bhavesh Kale,");
        assertEquals(2, rows.size());
        assertEquals(Check.READY, rows.get(0).check);
        assertEquals(A, rows.get(0).email);
        assertEquals(new StudentDetails("Aditi Pawar", 2), rows.get(0).details);
        assertEquals(new StudentDetails("Bhavesh Kale", null), rows.get(1).details);
    }

    @Test
    public void aLabGroupMayBeWrittenAsTheTimetableDoes() {
        final List<Row> rows =
                parse(
                        "email,roll no,name,batch\n"
                                + A
                                + ",262011005,A P,Batch 3\n"
                                + B
                                + ",262010006,B K,B4\n");
        assertEquals(Integer.valueOf(3), rows.get(0).labGroup);
        assertEquals(Integer.valueOf(4), rows.get(1).labGroup);
    }

    @Test
    public void columnsMayComeInAnyOrderWithOtherColumnsIgnored() {
        final List<Row> rows =
                parse(
                        "Sr. No.,Name of Student,Roll No.,Email ID,Phone,Lab Group\n"
                                + "1,Aditi Pawar,262011005,"
                                + A
                                + ",9800000000,3\n");
        assertEquals(new StudentDetails("Aditi Pawar", 3), rows.get(0).details);
    }

    @Test
    public void aBatchCodeColumnMustNameThisBatch() {
        final String text =
                "email,roll no,name,class\n"
                        + A
                        + ",262011005,A P,mc26\n"
                        + B
                        + ",262010006,B K,MCA25\n";
        assertEquals(Arrays.asList(Check.READY, Check.OTHER_BATCH), checksOf(text));
    }

    @Test
    public void aBomBlankLinesAndWindowsLineEndingsAreHandled() {
        final String text =
                BOM
                        + "email,roll no,name\r\n"
                        + A
                        + ",262011005,Aditi Pawar\r\n\r\n,,\r\n"
                        + B
                        + ",262010006,Bhavesh Kale\r\n";
        assertEquals(Arrays.asList(Check.READY, Check.READY), checksOf(text));
    }

    @Test
    public void everyKindOfBadRowIsIdentified() {
        assertEquals(Collections.singletonList(Check.MALFORMED), checks(A + ",262011005"));
        assertEquals(
                Collections.singletonList(Check.INVALID_EMAIL),
                checks("apawar@gmail.com,262011005,A P"));
        assertEquals(Collections.singletonList(Check.INVALID_ROLL_NO), checks(A + ",26201100,A P"));
        assertEquals(Collections.singletonList(Check.INVALID_NAME), checks(A + ",262011005,  "));
        assertEquals(
                Collections.singletonList(Check.INVALID_LAB_GROUP),
                checksOf(WITH_GROUP + A + ",262011005,A P,5"));
    }

    @Test
    public void duplicatesInsideTheFileAreFlaggedOnTheLaterRow() {
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_EMAIL),
                checks(A + ",262011005,A P\n" + A + ",262010006,A P"));
        assertEquals(
                Arrays.asList(Check.READY, Check.DUPLICATE_ROLL_NO),
                checks(A + ",262011005,A P\n" + B + ",262011005,B K"));
    }

    @Test
    public void aFileThatCannotBeUsedIsRefusedOutright() {
        assertEquals(FileProblem.NO_HEADER, problem(""));
        assertEquals(FileProblem.NO_HEADER, problem(A + ",262011005,A P"));
        assertEquals(
                FileProblem.NO_HEADER, problem("email,rollNo,cohort\n" + A + ",262011005,MC26"));
        assertEquals(FileProblem.EMPTY, problem(HEADER));
    }

    @Test
    public void rowsAreCheckedAgainstTheWholeRoster() {
        final RosterEntry inAnotherBatch =
                new RosterEntry(B, "252010009", "MCA25", "teacherA", 0, null, "B K", null);
        final String text =
                WITH_GROUP
                        + Fixtures.ENTRY.getEmail()
                        + ",262011001,Meera Joshi,1\n"
                        + A
                        + ",262010002,Aditi Pawar,\n"
                        + B
                        + ",262011007,B K,\n";
        final List<Check> checks = new ArrayList<>();
        for (Row row :
                RosterCsv.checkAgainst(
                        parse(text),
                        BATCH,
                        Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY, inAnotherBatch))) {
            checks.add(row.check);
        }
        assertEquals(
                Arrays.asList(Check.ALREADY_ON_ROSTER, Check.ROLL_TAKEN, Check.EMAIL_ON_ROSTER),
                checks);
    }

    @Test
    public void aChangedNameIsAnUpdateThatKeepsTheGroup() {
        final List<Row> rows =
                RosterCsv.checkAgainst(
                        parse(
                                WITH_GROUP
                                        + Fixtures.ENTRY.getEmail()
                                        + ",262011001,Meera S. Joshi,"),
                        BATCH,
                        Collections.singletonList(Fixtures.ENTRY));
        assertEquals(Check.DETAILS_CHANGED, rows.get(0).check);
        assertEquals(new StudentDetails("Meera S. Joshi", 1), rows.get(0).details);
        assertNull(rows.get(0).labGroup);
    }
}
