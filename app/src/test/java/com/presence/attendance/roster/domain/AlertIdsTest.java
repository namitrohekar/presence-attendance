package com.presence.attendance.roster.domain;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import org.junit.Test;

/** The id must equal what firestore.rules rebuilds: {uid}_{type}_{y}-{m}-{d}, UTC, unpadded. */
public final class AlertIdsTest {

    @Test
    public void theDateIsUtcAndNotZeroPadded() {
        assertEquals(
                "studentA_ROLL_MISMATCH_2026-3-5",
                AlertIds.of(
                        "studentA",
                        IdentityAlert.Type.ROLL_MISMATCH,
                        Instant.parse("2026-03-05T10:15:30Z")));
        assertEquals(
                "u_NON_VJTI_EMAIL_2026-11-15",
                AlertIds.of(
                        "u",
                        IdentityAlert.Type.NON_VJTI_EMAIL,
                        Instant.parse("2026-11-15T00:00:00Z")));
    }

    @Test
    public void lateEveningInIndiaIsStillThePreviousUtcDayUntilHalfPastFive() {
        // 05:29 IST on the 16th is 23:59 UTC on the 15th.
        assertEquals(
                "u_NOT_ON_REGISTER_2026-11-15",
                AlertIds.of(
                        "u",
                        IdentityAlert.Type.NOT_ON_REGISTER,
                        Instant.parse("2026-11-15T23:59:59Z")));
        assertEquals(
                "u_NOT_ON_REGISTER_2026-11-16",
                AlertIds.of(
                        "u",
                        IdentityAlert.Type.NOT_ON_REGISTER,
                        Instant.parse("2026-11-16T00:00:00Z")));
    }
}
