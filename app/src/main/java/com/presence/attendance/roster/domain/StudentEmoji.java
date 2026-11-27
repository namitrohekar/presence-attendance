package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * The ten emojis a student is shown with in faculty lists. Stored by name, so the stored value
 * never depends on how a phone draws the character. The order is part of the contract ({@code
 * STUDENT_EMOJIS}): a teacher may give an emoji by its number, 1 to 10.
 */
public enum StudentEmoji {
    LION(0x1F981),
    TIGER(0x1F42F),
    ELEPHANT(0x1F418),
    PANDA(0x1F43C),
    FOX(0x1F98A),
    OWL(0x1F989),
    DOLPHIN(0x1F42C),
    TURTLE(0x1F422),
    BUTTERFLY(0x1F98B),
    PEACOCK(0x1F99A);

    /** Variation selector 16. Some keyboards add it after an emoji; it changes nothing here. */
    private static final String EMOJI_PRESENTATION = String.valueOf((char) 0xFE0F);

    @NonNull private final String glyph;

    StudentEmoji(int codePoint) {
        this.glyph = new String(Character.toChars(codePoint));
    }

    /** The character to draw. */
    @NonNull
    public String glyph() {
        return glyph;
    }

    /** The emoji stored under this name, or null if the name is not one of the ten. */
    @Nullable
    public static StudentEmoji fromName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (StudentEmoji emoji : values()) {
            if (emoji.name().equals(name)) {
                return emoji;
            }
        }
        return null;
    }

    /**
     * What a teacher typed in a class list: the emoji itself, its name in any case, or its number
     * from 1 to 10.
     *
     * @return the emoji, or null if the text is none of these
     */
    @Nullable
    public static StudentEmoji parse(@NonNull String input) {
        final String text = input.replace(EMOJI_PRESENTATION, "").trim();
        for (StudentEmoji emoji : values()) {
            if (emoji.glyph.equals(text) || emoji.name().equals(text.toUpperCase(Locale.ROOT))) {
                return emoji;
            }
        }
        if (text.matches("[0-9]{1,2}")) {
            final int number = Integer.parseInt(text);
            return number >= 1 && number <= values().length ? values()[number - 1] : null;
        }
        return null;
    }

    /**
     * The emoji a student gets when none was chosen: one for each last digit of the roll number, so
     * students next to each other on the list look different.
     */
    @NonNull
    public static StudentEmoji forRollNo(@NonNull String rollNo) {
        final char last = rollNo.isEmpty() ? '0' : rollNo.charAt(rollNo.length() - 1);
        return last >= '0' && last <= '9' ? values()[last - '0'] : LION;
    }

    /** The stored emoji, or the roll number's for an entry added before emojis were kept. */
    @NonNull
    public static StudentEmoji orForRollNo(@Nullable StudentEmoji stored, @NonNull String rollNo) {
        return stored != null ? stored : forRollNo(rollNo);
    }
}
