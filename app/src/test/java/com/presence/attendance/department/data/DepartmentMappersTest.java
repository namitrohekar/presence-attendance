package com.presence.attendance.department.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.presence.attendance.department.domain.Timetable;
import com.presence.attendance.department.domain.TimetableSlot;
import com.presence.attendance.testutil.DocumentContract;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

public final class DepartmentMappersTest {

    private static final TimetableSlot SLOT =
            new TimetableSlot(
                    1, "10:30", "12:25", "MC26S1OSG1", TimetableSlot.Type.LAB, "AL-301", "GS");

    @Test
    public void aTimetableAndItsSlotsHaveExactlyTheContractFields() throws IOException {
        final Map<String, Object> document =
                DepartmentMappers.newTimetable(
                        new Timetable("MC26", 1, Collections.singletonList(SLOT)), "teacherA");
        assertEquals(
                DocumentContract.fields("timetables", false), new TreeSet<>(document.keySet()));
        final List<?> slots = (List<?>) document.get("slots");
        assertEquals(1, slots.size());
        assertEquals(
                DocumentContract.fields("timetables.slot", false),
                new TreeSet<>(((Map<?, ?>) slots.get(0)).keySet()));
    }

    @Test
    public void aStoredSlotIsReadBackAsWritten() {
        final Map<String, Object> stored = new HashMap<>(DepartmentMappers.slot(SLOT));
        // Firestore gives whole numbers back as longs.
        stored.put("day", 1L);
        assertEquals(SLOT, DepartmentMappers.slot(stored));
    }

    @Test
    public void aSlotOfAnUnknownTypeOrShapeIsSkipped() {
        final Map<String, Object> stored = new HashMap<>(DepartmentMappers.slot(SLOT));
        stored.put("type", "SEMINAR");
        assertNull(DepartmentMappers.slot(stored));
        stored.put("type", "LAB");
        stored.remove("start");
        assertNull(DepartmentMappers.slot(stored));
    }
}
