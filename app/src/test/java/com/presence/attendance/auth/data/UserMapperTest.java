package com.presence.attendance.auth.data;

import static org.junit.Assert.assertEquals;

import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.testutil.DocumentContract;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

public final class UserMapperTest {

    @Test
    public void aNewProfileHasExactlyTheContractFields() throws IOException {
        final Map<String, Object> document =
                UserMapper.newDocument("Meera Joshi", "meera.joshi@example.edu", Role.STUDENT);
        assertEquals(DocumentContract.fields("users", false), new TreeSet<>(document.keySet()));
        assertEquals("the role is written as its constant name", "STUDENT", document.get("role"));
    }

    @Test
    public void theFieldsAnUpdateTouchesAreInTheContract() throws IOException {
        // FirebaseAuthRepository updates these three by name; createdAt must stay untouched.
        final TreeSet<String> updated = new TreeSet<>();
        updated.add(UserMapper.NAME);
        updated.add(UserMapper.EMAIL);
        updated.add(UserMapper.ROLE);
        final TreeSet<String> expected = new TreeSet<>(DocumentContract.fields("users", false));
        expected.remove("createdAt");
        assertEquals(expected, updated);
    }
}
