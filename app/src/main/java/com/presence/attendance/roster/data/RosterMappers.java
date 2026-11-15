package com.presence.attendance.roster.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.roster.domain.IdentityAlert;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository.CorrectionAction;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.HashMap;
import java.util.Map;

/**
 * Explicit mapping for {@code studentRegistry}, {@code rollIndex}, {@code registryCorrections} and
 * {@code identityAlerts} documents. Field lists are held to {@code contracts/documents.json}.
 */
public final class RosterMappers {

    public static final String EMAIL = "email";
    public static final String ROLL_NO = "rollNo";
    public static final String COHORT = "cohort";
    public static final String ADDED_BY = "addedBy";
    public static final String ADDED_AT = "addedAt";
    public static final String REVISION = "revision";
    public static final String REGISTERED_UID = "registeredUid";
    public static final String REGISTERED_AT = "registeredAt";
    public static final String AUDIENCE = "audience";
    public static final String CREATED_AT = "createdAt";
    private static final String TYPE = "type";
    private static final String UID = "uid";
    private static final String ENTERED_ROLL_NO = "enteredRollNo";
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long NANOS_PER_MILLI = 1_000_000;

    private RosterMappers() {}

    /** The roster entry in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    public static RosterEntry entry(@NonNull DocumentSnapshot snapshot) {
        final String email = Snapshots.string(snapshot, EMAIL);
        final String rollNo = Snapshots.string(snapshot, ROLL_NO);
        final String cohort = Snapshots.string(snapshot, COHORT);
        final String addedBy = Snapshots.string(snapshot, ADDED_BY);
        final Long revision = Snapshots.integer(snapshot, REVISION);
        final boolean valid =
                email != null
                        && rollNo != null
                        && cohort != null
                        && addedBy != null
                        && revision != null
                        && Snapshots.timestamp(snapshot, ADDED_AT) != null
                        && StudentIdentity.isRollNo(rollNo)
                        && StudentIdentity.isCohort(cohort);
        if (!valid) {
            return null;
        }
        return new RosterEntry(
                email,
                rollNo,
                cohort,
                addedBy,
                revision.intValue(),
                Snapshots.string(snapshot, REGISTERED_UID));
    }

    @NonNull
    public static Map<String, Object> newEntry(
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @NonNull String teacherUid) {
        final Map<String, Object> document = new HashMap<>();
        document.put(EMAIL, email);
        document.put(ROLL_NO, rollNo);
        document.put(COHORT, cohort);
        document.put(ADDED_BY, teacherUid);
        document.put(ADDED_AT, FieldValue.serverTimestamp());
        document.put(REVISION, 0);
        return document;
    }

    /**
     * The whole entry after a correction. Written as a full replace, so the registration fields are
     * dropped and the student must register again.
     */
    @NonNull
    static Map<String, Object> correctedEntry(
            @NonNull RosterEntry before,
            @NonNull Timestamp addedAt,
            @NonNull String rollNo,
            @NonNull String cohort) {
        final Map<String, Object> document = new HashMap<>();
        document.put(EMAIL, before.getEmail());
        document.put(ROLL_NO, rollNo);
        document.put(COHORT, cohort);
        document.put(ADDED_BY, before.getAddedBy());
        document.put(ADDED_AT, addedAt);
        document.put(REVISION, before.getRevision() + 1);
        return document;
    }

    /** The two fields a student sets, once, to bind their entry to their sign-in. */
    @NonNull
    public static Map<String, Object> claim(@NonNull String uid) {
        final Map<String, Object> fields = new HashMap<>();
        fields.put(REGISTERED_UID, uid);
        fields.put(REGISTERED_AT, FieldValue.serverTimestamp());
        return fields;
    }

    @NonNull
    static Map<String, Object> index(@NonNull String email) {
        final Map<String, Object> document = new HashMap<>();
        document.put(EMAIL, email);
        return document;
    }

    @NonNull
    public static String indexKey(@NonNull String cohort, @NonNull String rollNo) {
        return cohort + "_" + rollNo;
    }

    /** The id the rules require for the audit record of the next change to an entry. */
    @NonNull
    static String correctionId(@NonNull RosterEntry before, @NonNull Timestamp addedAt) {
        final long addedAtMillis =
                addedAt.getSeconds() * MILLIS_PER_SECOND
                        + addedAt.getNanoseconds() / NANOS_PER_MILLI;
        return before.getEmail() + "_" + addedAtMillis + "_" + (before.getRevision() + 1);
    }

    /**
     * @param afterRollNo the roll number after the change; null for a removal
     * @param afterCohort the cohort after the change; null for a removal
     */
    @NonNull
    static Map<String, Object> correction(
            @NonNull RosterEntry before,
            @NonNull CorrectionAction action,
            @NonNull String reason,
            @NonNull String teacherUid,
            @Nullable String afterRollNo,
            @Nullable String afterCohort) {
        final Map<String, Object> document = new HashMap<>();
        document.put(EMAIL, before.getEmail());
        document.put("action", action.name());
        document.put("reason", reason);
        document.put("correctedBy", teacherUid);
        document.put("correctedAt", FieldValue.serverTimestamp());
        document.put("beforeRollNo", before.getRollNo());
        document.put("beforeCohort", before.getCohort());
        if (before.getRegisteredUid() != null) {
            document.put("releasedUid", before.getRegisteredUid());
        }
        if (afterRollNo != null && afterCohort != null) {
            document.put("afterRollNo", afterRollNo);
            document.put("afterCohort", afterCohort);
        }
        return document;
    }

    @NonNull
    static Map<String, Object> newAlert(
            @NonNull String uid,
            @NonNull String email,
            @NonNull IdentityAlert.Type type,
            @Nullable String enteredRollNo,
            @NonNull String audience) {
        final Map<String, Object> document = new HashMap<>();
        document.put(TYPE, type.name());
        document.put(EMAIL, email);
        document.put(UID, uid);
        document.put(AUDIENCE, audience);
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        if (enteredRollNo != null) {
            document.put(ENTERED_ROLL_NO, enteredRollNo);
        }
        return document;
    }

    /** The alert in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    static IdentityAlert alert(@NonNull DocumentSnapshot snapshot) {
        final IdentityAlert.Type type =
                IdentityAlert.Type.fromWire(Snapshots.string(snapshot, TYPE));
        final String email = Snapshots.string(snapshot, EMAIL);
        final Timestamp createdAt = Snapshots.timestamp(snapshot, CREATED_AT);
        if (type == null || email == null || createdAt == null) {
            return null;
        }
        return new IdentityAlert(
                snapshot.getId(),
                type,
                email,
                Snapshots.string(snapshot, ENTERED_ROLL_NO),
                createdAt.toDate().getTime());
    }
}
