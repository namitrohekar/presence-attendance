package com.presence.attendance.roster.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.presence.attendance.core.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class StudentEmojiTest {

    private static final String LION = new String(Character.toChars(0x1F981));
    private static final String CAT = new String(Character.toChars(0x1F431));
    private static final String VS16 = String.valueOf((char) 0xFE0F);

    @Test
    public void theTenEmojisAreTheContractsInItsOrder() {
        final List<String> names = new ArrayList<>();
        for (StudentEmoji emoji : StudentEmoji.values()) {
            names.add(emoji.name());
        }
        assertEquals(Constants.STUDENT_EMOJIS, String.join(",", names));
    }

    @Test
    public void aTeacherMayGiveThePictureTheNameOrTheNumber() {
        assertEquals(StudentEmoji.LION, StudentEmoji.parse(LION));
        assertEquals(
                "a variation selector after the picture is ignored",
                StudentEmoji.LION,
                StudentEmoji.parse(LION + VS16));
        assertEquals(StudentEmoji.PEACOCK, StudentEmoji.parse(" peacock "));
        assertEquals(StudentEmoji.PEACOCK, StudentEmoji.parse("PEACOCK"));
        assertEquals(StudentEmoji.LION, StudentEmoji.parse("1"));
        assertEquals(StudentEmoji.PEACOCK, StudentEmoji.parse("10"));
        assertEquals(StudentEmoji.LION, StudentEmoji.parse("01"));
    }

    @Test
    public void anythingElseIsNotAnEmoji() {
        assertNull(StudentEmoji.parse("cat"));
        assertNull(StudentEmoji.parse("0"));
        assertNull(StudentEmoji.parse("11"));
        assertNull(StudentEmoji.parse(""));
        assertNull(StudentEmoji.parse(CAT));
        assertNull(StudentEmoji.fromName("lion"));
        assertNull(StudentEmoji.fromName(null));
    }

    @Test
    public void theRollNumbersLastDigitChoosesOneSoNeighboursDiffer() {
        assertEquals(StudentEmoji.LION, StudentEmoji.forRollNo("262011000"));
        assertEquals(StudentEmoji.TIGER, StudentEmoji.forRollNo("262011001"));
        assertEquals(StudentEmoji.PEACOCK, StudentEmoji.forRollNo("262011009"));
        assertEquals("no roll number", StudentEmoji.LION, StudentEmoji.forRollNo(""));
        assertEquals(StudentEmoji.FOX, StudentEmoji.orForRollNo(StudentEmoji.FOX, "262011001"));
        assertEquals(StudentEmoji.TIGER, StudentEmoji.orForRollNo(null, "262011001"));
    }

    @Test
    public void everyGlyphIsOneDistinctCharacter() {
        final List<String> glyphs = new ArrayList<>();
        for (StudentEmoji emoji : StudentEmoji.values()) {
            assertEquals(emoji.name(), 1, emoji.glyph().codePointCount(0, emoji.glyph().length()));
            glyphs.add(emoji.glyph());
        }
        assertEquals(10, glyphs.stream().distinct().count());
    }
}
