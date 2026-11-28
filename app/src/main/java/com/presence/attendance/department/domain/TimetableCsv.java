package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.Constants;
import com.presence.attendance.department.domain.TimetableSlot.Type;
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.roster.domain.RosterCsv.FileException;
import com.presence.attendance.roster.domain.RosterCsv.FileProblem;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a batch's weekly timetable, one class per line. The first line names the columns, in any
 * order: day, start, end and subject are required; code, type, batch (the lab group), faculty and
 * room are optional. The whole file is checked before anything is written.
 */
public final class TimetableCsv {

    /** Why a row cannot be used. */
    public enum Check {
        READY,
        MALFORMED,
        INVALID_DAY,
        /** A start or end that is not a time, or an end that is not after the start. */
        INVALID_TIME,
        INVALID_TYPE,
        INVALID_SUBJECT,
        /** Not a lab group of this batch. */
        INVALID_LAB_GROUP
    }

    private enum Column {
        DAY(true, "day", "weekday"),
        START(true, "start", "from", "starttime", "begins"),
        END(true, "end", "to", "endtime", "ends"),
        SUBJECT(true, "subject", "subjectname", "course", "coursename"),
        CODE(false, "code", "subjectcode", "coursecode"),
        TYPE(false, "type", "kind"),
        LAB_GROUP(false, "batch", "labgroup", "group", "labbatch", "practicalbatch"),
        FACULTY(false, "faculty", "teacher", "staff", "facultyname"),
        ROOM(false, "room", "venue", "location", "classroom");

        final boolean required;
        final String[] headings;

        Column(boolean required, String... headings) {
            this.required = required;
            this.headings = headings;
        }
    }

    private static final List<String> DAYS =
            Arrays.asList("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    private static final Pattern TIME = Pattern.compile("^([0-9]{1,2})[:.]([0-9]{2})$");

    /** Before this hour a time is read as afternoon: "1:30" in a college day is 13:30. */
    private static final int FIRST_MORNING_HOUR = 8;

    private static final int CODE_MAX = 20;
    private static final int KEY_MAX = 60;
    private static final int ROOM_MAX = 40;
    private static final int FACULTY_MAX = 80;

    /** Words left out of a code made from a subject's name. */
    private static final List<String> SMALL_WORDS = Arrays.asList("and", "of", "the", "for", "in");

    /** One data row after normalisation. Immutable. */
    public static final class Row {
        /** Position among the data rows, from 1. */
        public final int line;

        /** 1 is Monday; 0 if the cell is not a day. */
        public final int day;

        /** "HH:MM", or the cell as written if it is not a time. */
        @NonNull public final String start;

        @NonNull public final String end;
        @NonNull public final String subject;

        /** The code given, or one made from the subject's name if the cell is empty. */
        @NonNull public final String code;

        /**
         * What tells this subject apart from others: the code given, or the whole name when there
         * is none, since two names can share their initials.
         */
        @NonNull public final String key;

        @Nullable public final Type type;

        /** 0 for the whole batch. */
        public final int labGroup;

        @NonNull public final String faculty;
        @NonNull public final String room;
        @NonNull public final Check check;

        Row(
                int line,
                int day,
                @NonNull String start,
                @NonNull String end,
                @NonNull String subject,
                @NonNull String code,
                @NonNull String key,
                @Nullable Type type,
                int labGroup,
                @NonNull String faculty,
                @NonNull String room,
                @NonNull Check check) {
            this.line = line;
            this.day = day;
            this.start = start;
            this.end = end;
            this.subject = subject;
            this.code = code;
            this.key = key;
            this.type = type;
            this.labGroup = labGroup;
            this.faculty = faculty;
            this.room = room;
            this.check = check;
        }
    }

    private TimetableCsv() {}

    /**
     * Every data row, each with its check.
     *
     * @throws FileException if the file has no header naming the required columns, no rows, or more
     *     than {@link Constants#TIMETABLE_SLOTS_MAX}
     */
    @NonNull
    public static List<Row> parse(@NonNull String text, int labGroups) {
        final List<List<String>> records = RosterCsv.records(text);
        final Map<Column, Integer> columns = records.isEmpty() ? null : columns(records.get(0));
        if (columns == null) {
            throw new FileException(FileProblem.NO_HEADER);
        }
        if (records.size() == 1) {
            throw new FileException(FileProblem.EMPTY);
        }
        if (records.size() - 1 > Constants.TIMETABLE_SLOTS_MAX) {
            throw new FileException(FileProblem.TOO_LARGE);
        }
        int lastRequired = 0;
        for (Map.Entry<Column, Integer> entry : columns.entrySet()) {
            if (entry.getKey().required) {
                lastRequired = Math.max(lastRequired, entry.getValue());
            }
        }
        final List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            final List<String> cells = records.get(i);
            if (cells.size() <= lastRequired) {
                rows.add(new Row(i, 0, "", "", "", "", "", null, 0, "", "", Check.MALFORMED));
            } else {
                rows.add(row(i, cells, columns, labGroups));
            }
        }
        return rows;
    }

    @NonNull
    private static Row row(
            int line,
            @NonNull List<String> cells,
            @NonNull Map<Column, Integer> columns,
            int labGroups) {
        final int day = day(cell(cells, columns, Column.DAY));
        final String startCell = cell(cells, columns, Column.START);
        final String endCell = cell(cells, columns, Column.END);
        final String start = time(startCell);
        final String end = time(endCell);
        final String subject = collapse(cell(cells, columns, Column.SUBJECT));
        final String codeCell = cell(cells, columns, Column.CODE).toUpperCase(Locale.ROOT);
        final String code = codeCell.isEmpty() ? codeFrom(subject) : codeCell;
        final String typeCell = cell(cells, columns, Column.TYPE);
        final int group =
                StudentIdentity.parseLabGroup(cell(cells, columns, Column.LAB_GROUP), labGroups);
        Type type = type(typeCell);
        if (typeCell.isEmpty()) {
            type = group > 0 ? Type.LAB : Type.LECTURE;
        }
        final Check check;
        if (day == 0) {
            check = Check.INVALID_DAY;
        } else if (start == null
                || end == null
                || TimetableSlot.minutesOf(end) <= TimetableSlot.minutesOf(start)) {
            check = Check.INVALID_TIME;
        } else if (subject.isEmpty() || subject.length() > Constants.NAME_MAX_LEN) {
            check = Check.INVALID_SUBJECT;
        } else if (type == null) {
            check = Check.INVALID_TYPE;
        } else if (group == StudentIdentity.INVALID_GROUP) {
            check = Check.INVALID_LAB_GROUP;
        } else {
            check = Check.READY;
        }
        return new Row(
                line,
                day,
                start != null ? start : startCell,
                end != null ? end : endCell,
                subject,
                code.length() > CODE_MAX ? code.substring(0, CODE_MAX) : code,
                codeCell.isEmpty()
                        ? clip(subject.replaceAll("[^A-Za-z0-9]", ""), KEY_MAX)
                        : codeCell,
                type,
                Math.max(group, 0),
                clip(collapse(cell(cells, columns, Column.FACULTY)), FACULTY_MAX),
                clip(collapse(cell(cells, columns, Column.ROOM)), ROOM_MAX),
                check);
    }

    /** "MON", "Monday" or "mon." is 1; anything else is 0. */
    static int day(@NonNull String cell) {
        final String text = cell.trim().toLowerCase(Locale.ROOT);
        return text.length() < 3 ? 0 : DAYS.indexOf(text.substring(0, 3)) + 1;
    }

    /** "9:30", "09.30" or "13:30" as "HH:MM"; null if it is not a time of day. */
    @Nullable
    static String time(@NonNull String cell) {
        final Matcher matcher = TIME.matcher(cell.trim());
        if (!matcher.matches()) {
            return null;
        }
        int hours = Integer.parseInt(matcher.group(1));
        final int minutes = Integer.parseInt(matcher.group(2));
        // shortcut: assumes no class starts before 08:00; a 24-hour file is read as written.
        if (hours > 0 && hours < FIRST_MORNING_HOUR) {
            hours += 12;
        }
        if (hours > 23 || minutes > 59) {
            return null;
        }
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    @Nullable
    static Type type(@NonNull String cell) {
        final String text = cell.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("lec") || text.equals("theory") || text.equals("l")) {
            return Type.LECTURE;
        }
        if (text.startsWith("lab") || text.startsWith("prac") || text.equals("p")) {
            return Type.LAB;
        }
        if (text.startsWith("tut") || text.equals("t")) {
            return Type.TUTORIAL;
        }
        return null;
    }

    /** The first letters of the subject's main words: "Accounting and Finance" is "AF". */
    @NonNull
    static String codeFrom(@NonNull String subject) {
        final StringBuilder code = new StringBuilder();
        for (String word : subject.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty() && !SMALL_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                code.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return code.toString();
    }

    @NonNull
    private static String collapse(@NonNull String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    @NonNull
    private static String clip(@NonNull String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    @NonNull
    private static String cell(
            @NonNull List<String> cells,
            @NonNull Map<Column, Integer> columns,
            @NonNull Column column) {
        final Integer index = columns.get(column);
        return index == null || index >= cells.size() ? "" : cells.get(index).trim();
    }

    @Nullable
    private static Map<Column, Integer> columns(@NonNull List<String> header) {
        final Map<Column, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            final String heading =
                    header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            for (Column column : Column.values()) {
                if (Arrays.asList(column.headings).contains(heading)) {
                    columns.putIfAbsent(column, i);
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
}
