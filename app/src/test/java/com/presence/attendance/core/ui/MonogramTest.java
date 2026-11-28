package com.presence.attendance.core.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MonogramTest {

    @Test
    public void firstAndLastNamesGiveTwoInitials() {
        assertEquals("AP", Monogram.of("Aditi Pawar"));
        assertEquals("MJ", Monogram.of("Meera S. Joshi"));
        assertEquals("A", Monogram.of("Aditi"));
        assertEquals("AP", Monogram.of("  aditi   pawar  "));
    }

    @Test
    public void titlesAreSkipped() {
        assertEquals("AP", Monogram.of("Dr. Archana G. Pai"));
        assertEquals("GS", Monogram.of("Prof. Gajanan Sidam"));
        assertEquals("RM", Monogram.of("Mrs Rutika Meshram"));
    }

    @Test
    public void lettersOfAnyScriptAndNoLettersAtAll() {
        assertEquals("आप", Monogram.of("आदिती पवार"));
        assertEquals("", Monogram.of("   "));
        assertEquals("", Monogram.of("42"));
    }
}
