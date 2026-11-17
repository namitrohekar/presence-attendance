package com.presence.attendance.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.session.domain.Tokens;
import org.junit.Test;

public final class DeviceIdTest {

    @Test
    public void itHasTheShapeTheRulesRequireOfAPhoneId() {
        for (String androidId :
                new String[] {"9774d56d682e549c", "a", "", "üñí/ç0de with spaces"}) {
            assertTrue(androidId, Tokens.isValid(DeviceId.of(androidId)));
        }
    }

    @Test
    public void theSamePhoneAlwaysGetsTheSameIdAndDifferentPhonesDiffer() {
        assertEquals(DeviceId.of("9774d56d682e549c"), DeviceId.of("9774d56d682e549c"));
        assertNotEquals(DeviceId.of("9774d56d682e549c"), DeviceId.of("9774d56d682e549d"));
    }

    @Test
    public void theAndroidIdItselfNeverLeavesThePhone() {
        assertFalse(DeviceId.of("9774d56d682e549c").contains("9774d56d"));
    }
}
