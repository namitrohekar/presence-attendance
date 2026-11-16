package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import java.util.List;

/**
 * Builds the attendance report as CSV text that opens correctly in Excel and Sheets.
 *
 * <p>Names and emails come from other people's accounts, so every text field is treated as hostile:
 * it is quoted when it contains a separator, and neutralised when a spreadsheet would otherwise run
 * it as a formula.
 */
public final class AttendanceCsv {

    /** One student's line in the report. */
    public static final class Row {
        @NonNull final String rollNo;
        @NonNull final String name;
        @NonNull final String email;
        final int attended;
        final int held;
        final int percent;
        final boolean belowThreshold;

        public Row(
                @NonNull String rollNo,
                @NonNull String name,
                @NonNull String email,
                int attended,
                int held,
                int percent,
                boolean belowThreshold) {
            this.rollNo = rollNo;
            this.name = name;
            this.email = email;
            this.attended = attended;
            this.held = held;
            this.percent = percent;
            this.belowThreshold = belowThreshold;
        }
    }

    /** Tells Excel the file is UTF-8; without it, names outside ASCII are garbled. */
    static final String BOM = String.valueOf((char) 0xFEFF);

    private static final String NEW_LINE = "\r\n";
    private static final String HEADER =
            "Roll number,Name,Email,Sessions attended,Sessions held,Percent,Below requirement";

    private AttendanceCsv() {}

    @NonNull
    public static String build(@NonNull List<Row> rows) {
        final StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(NEW_LINE);
        for (Row row : rows) {
            csv.append(text(row.rollNo))
                    .append(',')
                    .append(text(row.name))
                    .append(',')
                    .append(text(row.email))
                    .append(',')
                    .append(row.attended)
                    .append(',')
                    .append(row.held)
                    .append(',')
                    .append(row.percent)
                    .append(',')
                    .append(row.belowThreshold ? "Yes" : "No")
                    .append(NEW_LINE);
        }
        return csv.toString();
    }

    /** One text field: made inert, then quoted if it needs to be. */
    @NonNull
    static String text(@NonNull String value) {
        String field = value;
        // A leading =, +, -, @, tab or carriage return makes a spreadsheet evaluate the cell.
        // An apostrophe in front makes it plain text. A roll number that is all digits is safe.
        if (!field.isEmpty() && "=+-@\t\r".indexOf(field.charAt(0)) >= 0) {
            field = "'" + field;
        }
        final boolean needsQuotes =
                field.indexOf(',') >= 0
                        || field.indexOf('"') >= 0
                        || field.indexOf('\n') >= 0
                        || field.indexOf('\r') >= 0;
        return needsQuotes ? '"' + field.replace("\"", "\"\"") + '"' : field;
    }
}
