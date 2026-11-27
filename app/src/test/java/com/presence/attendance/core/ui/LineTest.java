package com.presence.attendance.core.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class LineTest {

    @Test
    public void rowsLearnTheirPlaceWithinEachSection() {
        final List<Line> grouped =
                Line.grouped(
                        Arrays.asList(
                                Line.header("h1", "Cohort MC26"),
                                Line.row("a", "A", null),
                                Line.row("b", "B", null),
                                Line.row("c", "C", null),
                                Line.header("h2", "Cohort MC25"),
                                Line.row("d", "D", null),
                                Line.row("e", "E", null),
                                Line.header("h3", "Cohort MC24"),
                                Line.row("f", "F", null)));
        assertEquals(Line.Place.FIRST, grouped.get(1).place);
        assertEquals(Line.Place.MIDDLE, grouped.get(2).place);
        assertEquals(Line.Place.LAST, grouped.get(3).place);
        assertEquals(Line.Place.FIRST, grouped.get(5).place);
        assertEquals(Line.Place.LAST, grouped.get(6).place);
        assertEquals(Line.Place.SINGLE, grouped.get(8).place);
    }

    @Test
    public void aListWithoutHeadersIsOneSection() {
        final List<Line> grouped =
                Line.grouped(Arrays.asList(Line.row("a", "A", null), Line.row("b", "B", null)));
        assertEquals(Line.Place.FIRST, grouped.get(0).place);
        assertEquals(Line.Place.LAST, grouped.get(1).place);
    }

    @Test
    public void anAvatarSurvivesGroupingAndTheChevronAndCountsAsContent() {
        final Line row = Line.row("a", "Aditi Pawar", null).withAvatar("x").navigates();
        final Line grouped = Line.grouped(Arrays.asList(row)).get(0);
        assertEquals("x", grouped.avatar);
        assertTrue(grouped.chevron);
        assertNotEquals(
                "a changed emoji redraws the row",
                Line.row("a", "Aditi Pawar", null).withAvatar("x"),
                Line.row("a", "Aditi Pawar", null).withAvatar("y"));
    }
}
