package com.presence.attendance.auth.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.HashMap;
import java.util.Map;

/** Explicit mapping between {@code users/{uid}} documents and {@link User}. */
final class UserMapper {

    static final String NAME = "name";
    static final String EMAIL = "email";
    static final String ROLE = "role";
    static final String ROLL_NO = "rollNo";
    static final String COHORT = "cohort";
    private static final String CREATED_AT = "createdAt";

    private UserMapper() {}

    /**
     * The user in the snapshot, or null if the document is missing or malformed. A student profile
     * without a valid roll number and cohort (written before the roster existed) is not a
     * registered student and also maps to null.
     */
    @Nullable
    static User fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        final String name = Snapshots.string(snapshot, NAME);
        final String email = Snapshots.string(snapshot, EMAIL);
        final Role role = Role.fromWire(Snapshots.string(snapshot, ROLE));
        if (name == null || email == null || role == null) {
            return null;
        }
        if (role == Role.FACULTY) {
            return new User(snapshot.getId(), name, email, role);
        }
        final String rollNo = Snapshots.string(snapshot, ROLL_NO);
        final String cohort = Snapshots.string(snapshot, COHORT);
        final boolean registered =
                rollNo != null
                        && cohort != null
                        && StudentIdentity.isRollNo(rollNo)
                        && StudentIdentity.isCohort(cohort);
        return registered ? new User(snapshot.getId(), name, email, role, rollNo, cohort) : null;
    }

    @NonNull
    static Map<String, Object> newFacultyDocument(@NonNull String name, @NonNull String email) {
        final Map<String, Object> document = identity(name, email, Role.FACULTY);
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    @NonNull
    static Map<String, Object> newStudentDocument(
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort) {
        final Map<String, Object> document = studentFields(name, email, rollNo, cohort);
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** Everything a student profile holds except createdAt, for updating an existing one. */
    @NonNull
    static Map<String, Object> studentFields(
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort) {
        final Map<String, Object> fields = identity(name, email, Role.STUDENT);
        fields.put(ROLL_NO, rollNo);
        fields.put(COHORT, cohort);
        return fields;
    }

    /**
     * Turns an existing profile into a faculty one: the student fields, if any, are removed,
     * because the rules accept only the exact faculty shape.
     */
    @NonNull
    static Map<String, Object> facultyFields(@NonNull String name, @NonNull String email) {
        final Map<String, Object> fields = identity(name, email, Role.FACULTY);
        fields.put(ROLL_NO, FieldValue.delete());
        fields.put(COHORT, FieldValue.delete());
        return fields;
    }

    @NonNull
    private static Map<String, Object> identity(
            @NonNull String name, @NonNull String email, @NonNull Role role) {
        final Map<String, Object> fields = new HashMap<>();
        fields.put(NAME, name);
        fields.put(EMAIL, email);
        fields.put(ROLE, role.name());
        return fields;
    }
}
