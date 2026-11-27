package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates a class list. The first line names the columns, in any order: email, roll
 * no, name and cohort are required, batch and emoji are optional, and any other column (a serial
 * number, a phone number) is ignored. The whole file is checked before anything is written, so the
 * teacher sees every problem in one preview.
 */
public final class RosterCsv {

    /** Larger files are refused outright rather than partly imported. */
    public static final int MAX_ROWS = 500;

    private static final char BOM = (char) 0xFEFF;

    /** Why a whole file cannot be used. */
    public enum FileProblem {
        /** No data rows under the header. */
        EMPTY,
        /** More than {@link #MAX_ROWS} data rows. */
        TOO_LARGE,
        /** The first line does not name the email, roll no, name and cohort columns. */
        NO_HEADER
    }

    /** Thrown by {@link #parse} for a file that cannot be used at all. */
    public static final class FileException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        @NonNull public final FileProblem problem;

        FileException(@NonNull FileProblem problem) {
            super(problem.name());
            this.problem = problem;
        }
    }

    /** Why a row will not be written, or what will happen to it. */
    public enum Check {
        READY,
        /** The row has fewer cells than the header. */
        MALFORMED,
        INVALID_EMAIL,
        INVALID_ROLL_NO,
        INVALID_NAME,
        INVALID_COHORT,
        INVALID_BATCH,
        INVALID_EMOJI,
        DUPLICATE_EMAIL,
        DUPLICATE_ROLL_NO,
        /** The identical entry already exists. Skipped, not an error. */
        ALREADY_ON_ROSTER,
        /** On the roster as it is, with a different name, batch or emoji: those are updated. */
        DETAILS_CHANGED,
        /** The address is on the roster with a different roll number or cohort. */
        EMAIL_ON_ROSTER,
        /** The roll number belongs to another address in that cohort. */
        ROLL_TAKEN
    }

    /** The columns the importer understands, each with the headings it accepts. */
    private enum Column {
        EMAIL(true, "email", "emailid", "emailaddress", "officialemail"),
        ROLL_NO(true, "rollno", "rollnumber", "roll", "rollnum"),
        NAME(true, "name", "fullname", "studentname", "nameofstudent", "nameofthestudent"),
        COHORT(true, "cohort", "class"),
        BATCH(false, "batch", "labbatch"),
        EMOJI(false, "emoji");

        final boolean required;
        final String[] headings;

        Column(boolean required, String... headings) {
            this.required = required;
            this.headings = headings;
        }
    }

    /** One data row of the file after normalisation. Immutable. */
    public static final class Row {
        /** Position among the data rows, from 1. */
        public final int line;

        @NonNull public final String email;
        @NonNull public final String rollNo;
        @NonNull public final String cohort;
        @NonNull public final String name;

        /** The batch the file gives, or null if its cell is empty or missing. */
        @Nullable public final Integer batch;

        /** The emoji the file gives, or null if its cell is empty or missing. */
        @Nullable public final StudentEmoji emoji;

        /**
         * What will be written: set for READY and DETAILS_CHANGED rows, null for the rest. For a
         * student already on the roster an empty batch or emoji cell keeps what is there.
         */
        @Nullable public final StudentDetails details;

        @NonNull public final Check check;

        Row(
                int line,
                @NonNull String email,
                @NonNull String rollNo,
                @NonNull String cohort,
                @NonNull String name,
                @Nullable Integer batch,
                @Nullable StudentEmoji emoji,
                @Nullable StudentDetails details,
                @NonNull Check check) {
            this.line = line;
            this.email = email;
            this.rollNo = rollNo;
            this.cohort = cohort;
            this.name = name;
            this.batch = batch;
            this.emoji = emoji;
            this.details = details;
            this.check = check;
        }

        @NonNull
        Row with(@NonNull Check newCheck, @Nullable StudentDetails newDetails) {
            return new Row(line, email, rollNo, cohort, name, batch, emoji, newDetails, newCheck);
        }

        /** Whether this row is written by an import: a new student, or new details. */
        public boolean writes() {
            return check == Check.READY || check == Check.DETAILS_CHANGED;
        }

        /** The emoji to show beside the row, or null for a row with no usable roll number. */
        @Nullable
        public StudentEmoji shownEmoji() {
            if (details != null) {
                return details.getEmoji();
            }
            if (emoji != null) {
                return emoji;
            }
            return StudentIdentity.isRollNo(rollNo) ? StudentEmoji.forRollNo(rollNo) : null;
        }
    }

    private RosterCsv() {}

    /**
     * Parses the text and validates every row on its own and against the rest of the file.
     *
     * @return the data rows in file order, each with its check
     * @throws FileException if the header is missing, or there are no data rows or too many
     */
    @NonNull
    public static List<Row> parse(@NonNull String text) {
        final List<List<String>> records = records(text);
        final Map<Column, Integer> columns = records.isEmpty() ? null : columns(records.get(0));
        if (columns == null) {
            throw new FileException(FileProblem.NO_HEADER);
        }
        records.remove(0);
        if (records.isEmpty()) {
            throw new FileException(FileProblem.EMPTY);
        }
        if (records.size() > MAX_ROWS) {
            throw new FileException(FileProblem.TOO_LARGE);
        }
        final List<Row> rows = new ArrayList<>();
        final Set<String> emails = new HashSet<>();
        final Set<String> rollKeys = new HashSet<>();
        int line = 0;
        for (List<String> cells : records) {
            line++;
            rows.add(row(line, cells, columns, emails, rollKeys));
        }
        return rows;
    }

    /**
     * Re-checks READY rows against the roster as it is now, so the preview shows conflicts with
     * existing entries and which students only get new details. The write itself re-checks again
     * inside a transaction.
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
            checked.add(row.check == Check.READY ? conflict(row, byEmail, emailByRollKey) : row);
        }
        return Collections.unmodifiableList(checked);
    }

    @NonNull
    private static Row conflict(
            @NonNull Row row,
            @NonNull Map<String, RosterEntry> byEmail,
            @NonNull Map<String, String> emailByRollKey) {
        final RosterEntry existing = byEmail.get(row.email);
        if (existing == null) {
            return emailByRollKey.containsKey(rollKey(row.cohort, row.rollNo))
                    ? row.with(Check.ROLL_TAKEN, null)
                    : row;
        }
        final boolean sameIdentity =
                existing.getRollNo().equals(row.rollNo) && existing.getCohort().equals(row.cohort);
        if (!sameIdentity) {
            return row.with(Check.EMAIL_ON_ROSTER, null);
        }
        // An empty batch or emoji cell keeps what the roster has; the name is always the file's.
        final StudentDetails merged =
                new StudentDetails(
                        row.name,
                        row.batch != null ? row.batch : existing.getBatch(),
                        row.emoji != null ? row.emoji : existing.shownEmoji());
        return merged.equals(existing.details())
                ? row.with(Check.ALREADY_ON_ROSTER, null)
                : row.with(Check.DETAILS_CHANGED, merged);
    }

    @NonNull
    private static Row row(
            int line,
            @NonNull List<String> cells,
            @NonNull Map<Column, Integer> columns,
            @NonNull Set<String> emails,
            @NonNull Set<String> rollKeys) {
        final String email = StudentIdentity.normaliseEmail(cell(cells, columns, Column.EMAIL));
        final String rollNo = cell(cells, columns, Column.ROLL_NO).trim();
        final String cohort = StudentIdentity.normaliseCohort(cell(cells, columns, Column.COHORT));
        final String name = StudentIdentity.normaliseName(cell(cells, columns, Column.NAME));
        final String batchText = cell(cells, columns, Column.BATCH);
        final String emojiText = cell(cells, columns, Column.EMOJI).trim();
        final int batchValue = StudentIdentity.parseBatch(batchText);
        final Integer batch = batchValue > 0 ? batchValue : null;
        final StudentEmoji emoji = emojiText.isEmpty() ? null : StudentEmoji.parse(emojiText);

        final Check check;
        if (cells.size() <= lastRequired(columns)) {
            check = Check.MALFORMED;
        } else if (!StudentIdentity.isStudentEmail(email)) {
            check = Check.INVALID_EMAIL;
        } else if (!StudentIdentity.isRollNo(rollNo)) {
            check = Check.INVALID_ROLL_NO;
        } else if (!StudentIdentity.isName(name)) {
            check = Check.INVALID_NAME;
        } else if (!StudentIdentity.isCohort(cohort)) {
            check = Check.INVALID_COHORT;
        } else if (batchValue == StudentIdentity.INVALID_BATCH) {
            check = Check.INVALID_BATCH;
        } else if (!emojiText.isEmpty() && emoji == null) {
            check = Check.INVALID_EMOJI;
        } else if (!emails.add(email)) {
            check = Check.DUPLICATE_EMAIL;
        } else if (!rollKeys.add(rollKey(cohort, rollNo))) {
            check = Check.DUPLICATE_ROLL_NO;
        } else {
            check = Check.READY;
        }
        final StudentDetails details =
                check == Check.READY
                        ? new StudentDetails(
                                name, batch, emoji != null ? emoji : StudentEmoji.forRollNo(rollNo))
                        : null;
        return new Row(line, email, rollNo, cohort, name, batch, emoji, details, check);
    }

    /** The cell of a column, or "" if the file has no such column or the row is short. */
    @NonNull
    private static String cell(
            @NonNull List<String> cells,
            @NonNull Map<Column, Integer> columns,
            @NonNull Column column) {
        final Integer index = columns.get(column);
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }

    private static int lastRequired(@NonNull Map<Column, Integer> columns) {
        int last = 0;
        for (Map.Entry<Column, Integer> entry : columns.entrySet()) {
            if (entry.getKey().required) {
                last = Math.max(last, entry.getValue());
            }
        }
        return last;
    }

    /**
     * Where each known column is, from the header's headings; null if a required one is missing.
     * Headings are compared without case, spaces or punctuation, so "Roll No." is "rollno".
     */
    @Nullable
    private static Map<Column, Integer> columns(@NonNull List<String> header) {
        final Map<Column, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            final String heading =
                    header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            for (Column column : Column.values()) {
                for (String accepted : column.headings) {
                    if (accepted.equals(heading)) {
                        columns.putIfAbsent(column, i);
                    }
                }
            }
        }
        for (Column column : Column.values()) {
            if (column.required && !columns.containsKey(column)) {
                return null;
            }
        }
        return columns;
    }

    @NonNull
    private static String rollKey(@NonNull String cohort, @NonNull String rollNo) {
        return cohort + "_" + rollNo;
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

    /** A line of only commas is blank too: spreadsheets export empty rows that way. */
    private static void addUnlessBlank(
            @NonNull List<List<String>> records, @NonNull List<String> cells) {
        for (String value : cells) {
            if (!value.trim().isEmpty()) {
                records.add(cells);
                return;
            }
        }
    }
}
