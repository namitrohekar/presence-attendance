package com.presence.attendance.core.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One item of an inset-grouped list: a section header, or a row with a title, an optional detail
 * line and an optional badge. Screens map their own data to lines and share one adapter. Immutable.
 */
public final class Line {

    /** What a badge means. Always shown with text, never by colour alone. */
    public enum Tone {
        NEUTRAL,
        SUCCESS,
        WARNING,
        DANGER
    }

    /** Where a row sits in its section, which decides its rounded corners and separator. */
    enum Place {
        SINGLE,
        FIRST,
        MIDDLE,
        LAST
    }

    @NonNull final String key;
    final boolean header;
    @NonNull final String title;
    @Nullable final String detail;
    @Nullable final String badge;
    @NonNull final Tone tone;
    @NonNull final Place place;

    private Line(
            @NonNull String key,
            boolean header,
            @NonNull String title,
            @Nullable String detail,
            @Nullable String badge,
            @NonNull Tone tone,
            @NonNull Place place) {
        this.key = key;
        this.header = header;
        this.title = title;
        this.detail = detail;
        this.badge = badge;
        this.tone = tone;
        this.place = place;
    }

    @NonNull
    public static Line header(@NonNull String key, @NonNull String title) {
        return new Line(key, true, title, null, null, Tone.NEUTRAL, Place.SINGLE);
    }

    @NonNull
    public static Line row(@NonNull String key, @NonNull String title, @Nullable String detail) {
        return row(key, title, detail, null, Tone.NEUTRAL);
    }

    @NonNull
    public static Line row(
            @NonNull String key,
            @NonNull String title,
            @Nullable String detail,
            @Nullable String badge,
            @NonNull Tone tone) {
        return new Line(key, false, title, detail, badge, tone, Place.SINGLE);
    }

    /**
     * The same lines with each row told its place in its section. A section is the run of rows
     * between two headers.
     */
    @NonNull
    public static List<Line> grouped(@NonNull List<Line> lines) {
        final List<Line> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            final Line line = lines.get(i);
            if (line.header) {
                out.add(line);
                continue;
            }
            final boolean first = i == 0 || lines.get(i - 1).header;
            final boolean last = i == lines.size() - 1 || lines.get(i + 1).header;
            final Place place =
                    first && last
                            ? Place.SINGLE
                            : first ? Place.FIRST : last ? Place.LAST : Place.MIDDLE;
            out.add(
                    new Line(
                            line.key,
                            false,
                            line.title,
                            line.detail,
                            line.badge,
                            line.tone,
                            place));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Line)) {
            return false;
        }
        final Line line = (Line) other;
        return header == line.header
                && key.equals(line.key)
                && title.equals(line.title)
                && Objects.equals(detail, line.detail)
                && Objects.equals(badge, line.badge)
                && tone == line.tone
                && place == line.place;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, header, title, detail, badge, tone, place);
    }
}
