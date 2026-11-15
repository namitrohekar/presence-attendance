package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates a class list of {@code email,rollNo,cohort} rows. The whole file is checked
 * before anything is written, so the teacher sees every problem in one preview.
 */
public final class RosterCsv {

    /** Larger files are refused outright rather than partly imported. */
    public static final int MAX_ROWS = 500;

    private static final int COLUMNS = 3;
    private static final char BOM = '\uFEFF';
    private static final String HEADER_FIRST_CELL = "email";

    /** Why a row will not be imported, or READY if it will. */
    public enum Check {
        READY,
        MALFORMED,
        INVALID_EMAIL,
        INVALID_ROLL_NO,
        INVALID_COHORT,
        DUPLICATE_EMAIL,
        DUPLICATE_ROLL_NO,
        /** The identical entry already exists. Skipped, not an error. */
        ALREADY_ON_ROSTER,
        /** The address is on the roster with a different roll number or cohort. */
        EMAIL_ON_ROSTER,
        /** The roll number belongs to another address in that cohort. */
        ROLL_TAKEN
    }

    /** One data row of the file after normalisation. Immutable. */
    public static final class Row {
        public final int line;
        @NonNull public final String email;
        @NonNull public final String rollNo;
        @NonNull public final String cohort;
        @NonNull public final Check check;

        Row(
                int line,
                @NonNull String email,
                @NonNull String rollNo,
                @NonNull String cohort,
                @NonNull Check check) {
            this.line = line;
            this.email = email;
            this.rollNo = rollNo;
            this.cohort = cohort;
            this.check = check;
        }

        @NonNull
        Row with(@NonNull Check newCheck) {
            return new Row(line, email, rollNo, cohort, newCheck);
        }
    }

    private RosterCsv() {}

    /**
     * Parses the text and validates every row on its own and against the rest of the file.
     *
     * @return the data rows in file order, each with its check
     * @throws IllegalArgumentException if the file has no data rows or more than {@link #MAX_ROWS}
     */
    @NonNull
    public static List<Row> parse(@NonNull String text) {
        final List<List<String>> records = records(text);
        if (!records.isEmpty() && isHeader(records.get(0))) {
            records.remove(0);
        }
        if (records.isEmpty() || records.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Expected 1 to " + MAX_ROWS + " rows");
        }
        final List<Row> rows = new ArrayList<>();
        final Set<String> emails = new HashSet<>();
        final Set<String> rollKeys = new HashSet<>();
        int line = 0;
        for (List<String> cells : records) {
            line++;
            rows.add(row(line, cells, emails, rollKeys));
        }
        return rows;
    }

    /**
     * Re-checks READY rows against the roster as it is now, so the preview shows conflicts with
     * existing entries. The write itself re-checks again inside a transaction.
     */
    @NonNull
    public static List<Row> checkAgainst(
            @NonNull List<Row> rows, @NonNull List<RosterEntry> roster) {
        final Map<String, RosterEntry> byEmail = new HashMap<>();
        final Map<String, String> emailByRollKey = new HashMap<>();
        for (RosterEntry entry : roster) {
            byEmail.put(entry.getEmail(), entry);
            emailByRollKey.put(rollKey(entry.getCohort(), entry.getRollNo()), entry.getEmail());
        }
        final List<Row> checked = new ArrayList<>();
        for (Row row : rows) {
            checked.add(
                    row.check == Check.READY
                            ? row.with(conflict(row, byEmail, emailByRollKey))
                            : row);
        }
        return Collections.unmodifiableList(checked);
    }

    @NonNull
    private static Check conflict(
            @NonNull Row row,
            @NonNull Map<String, RosterEntry> byEmail,
            @NonNull Map<String, String> emailByRollKey) {
        final RosterEntry existing = byEmail.get(row.email);
        if (existing != null) {
            final boolean identical =
                    existing.getRollNo().equals(row.rollNo)
                            && existing.getCohort().equals(row.cohort);
            return identical ? Check.ALREADY_ON_ROSTER : Check.EMAIL_ON_ROSTER;
        }
        return emailByRollKey.containsKey(rollKey(row.cohort, row.rollNo))
                ? Check.ROLL_TAKEN
                : Check.READY;
    }

    @NonNull
    private static Row row(
            int line,
            @NonNull List<String> cells,
            @NonNull Set<String> emails,
            @NonNull Set<String> rollKeys) {
        if (cells.size() != COLUMNS) {
            return new Row(
                    line, cells.isEmpty() ? "" : cells.get(0).trim(), "", "", Check.MALFORMED);
        }
        final String email = StudentIdentity.normaliseEmail(cells.get(0));
        final String rollNo = cells.get(1).trim();
        final String cohort = StudentIdentity.normaliseCohort(cells.get(2));
        final Check check;
        if (!StudentIdentity.isStudentEmail(email)) {
            check = Check.INVALID_EMAIL;
        } else if (!StudentIdentity.isRollNo(rollNo)) {
            check = Check.INVALID_ROLL_NO;
        } else if (!StudentIdentity.isCohort(cohort)) {
            check = Check.INVALID_COHORT;
        } else if (!emails.add(email)) {
            check = Check.DUPLICATE_EMAIL;
        } else if (!rollKeys.add(rollKey(cohort, rollNo))) {
            check = Check.DUPLICATE_ROLL_NO;
        } else {
            check = Check.READY;
        }
        return new Row(line, email, rollNo, cohort, check);
    }

    @NonNull
    private static String rollKey(@NonNull String cohort, @NonNull String rollNo) {
        return cohort + "_" + rollNo;
    }

    private static boolean isHeader(@NonNull List<String> cells) {
        return !cells.isEmpty() && cells.get(0).trim().equalsIgnoreCase(HEADER_FIRST_CELL);
    }

    /** RFC 4180 records: quoted cells may hold commas, quotes ("") and line breaks. */
    @NonNull
    private static List<List<String>> records(@NonNull String text) {
        final List<List<String>> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        final int start = !text.isEmpty() && text.charAt(0) == BOM ? 1 : 0;
        for (int i = start; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString());
                cell.setLength(0);
                addUnlessBlank(records, cells);
                cells = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        addUnlessBlank(records, cells);
        return records;
    }

    private static void addUnlessBlank(
            @NonNull List<List<String>> records, @NonNull List<String> cells) {
        final boolean blank = cells.size() == 1 && cells.get(0).trim().isEmpty();
        if (!blank) {
            records.add(cells);
        }
    }
}
