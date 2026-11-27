package com.presence.attendance.roster.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.roster.domain.IdentityAlert;
import com.presence.attendance.roster.domain.RosterRepository.CorrectionAction;
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.testutil.DocumentContract;
import com.presence.attendance.testutil.Fixtures;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/** The roster documents the app writes must have exactly the fields the rules whitelist. */
public final class RosterMappersTest {

    private static final Timestamp ADDED_AT = new Timestamp(1_700_000_000L, 123_456_789);

    private static Set<String> keys(Map<String, Object> document) {
        return new TreeSet<>(document.keySet());
    }

    @Test
    public void aNewEntryHasExactlyTheContractFieldsAndRevisionZero() throws IOException {
        final Map<String, Object> entry =
                RosterMappers.newEntry(
                        "a@mc.vjti.ac.in",
                        "262011005",
                        "MC26",
                        new StudentDetails("Aditi Pawar", null, StudentEmoji.FOX),
                        "teacherA");
        assertEquals(DocumentContract.fields("studentRegistry", false), keys(entry));
        assertEquals(0, entry.get("revision"));
        assertEquals("teacherA", entry.get("addedBy"));
        assertEquals("Aditi Pawar", entry.get("name"));
        assertEquals("the emoji is stored by name", "FOX", entry.get("emoji"));
    }

    @Test
    public void aNewEntryInABatchAlsoHasTheBatch() throws IOException {
        final Map<String, Object> entry =
                RosterMappers.newEntry(
                        "a@mc.vjti.ac.in",
                        "262011005",
                        "MC26",
                        new StudentDetails("Aditi Pawar", 3, StudentEmoji.FOX),
                        "teacherA");
        assertEquals(DocumentContract.fields("studentRegistry", true), keys(entry));
        assertEquals(3, entry.get("batch"));
    }

    @Test
    public void aDetailsUpdateSetsNameAndEmojiAndSetsOrRemovesTheBatch() {
        final Map<String, Object> inBatch =
                RosterMappers.detailsUpdate(new StudentDetails("M J", 2, StudentEmoji.PEACOCK));
        assertEquals(new TreeSet<>(Arrays.asList("batch", "emoji", "name")), keys(inBatch));
        assertEquals(2, inBatch.get("batch"));
        assertEquals("PEACOCK", inBatch.get("emoji"));
        final Map<String, Object> noBatch =
                RosterMappers.detailsUpdate(new StudentDetails("M J", null, StudentEmoji.OWL));
        assertTrue("a cleared batch is deleted", noBatch.get("batch") instanceof FieldValue);
    }

    @Test
    public void aCorrectionOfAnEntryAddedBeforeNamesKeepsItWithoutThem() throws IOException {
        final Map<String, Object> after =
                RosterMappers.correctedEntry(Fixtures.LEGACY_ENTRY, ADDED_AT, "262010044", "MC26");
        final Set<String> base = DocumentContract.fields("studentRegistry", false);
        base.removeAll(Arrays.asList("name", "emoji"));
        assertEquals(base, keys(after));
    }

    @Test
    public void aClaimSetsOnlyTheTwoRegistrationFields() throws IOException {
        assertEquals(
                DocumentContract.fields("studentRegistry.claim", false),
                keys(RosterMappers.claim("studentA")));
    }

    @Test
    public void aCorrectedEntryDropsTheClaimAndAdvancesTheRevision() throws IOException {
        final Map<String, Object> after =
                RosterMappers.correctedEntry(Fixtures.ENTRY, ADDED_AT, "262011099", "MC25");
        // ENTRY is in a batch, so the optional batch field is there too.
        assertEquals(DocumentContract.fields("studentRegistry", true), keys(after));
        assertEquals("the details are kept", "Meera Joshi", after.get("name"));
        assertEquals("OWL", after.get("emoji"));
        assertEquals(1, after.get("batch"));
        assertFalse(after.containsKey("registeredUid"));
        assertEquals(1, after.get("revision"));
        assertEquals("the original add time is kept", ADDED_AT, after.get("addedAt"));
        assertEquals(Fixtures.ENTRY.getAddedBy(), after.get("addedBy"));
    }

    @Test
    public void theIndexAndItsKey() throws IOException {
        assertEquals(
                DocumentContract.fields("rollIndex", false),
                keys(RosterMappers.index("a@mc.vjti.ac.in")));
        assertEquals("MC26_262011005", RosterMappers.indexKey("MC26", "262011005"));
    }

    @Test
    public void theCorrectionIdIsAddressAddTimeInMillisAndNextRevision() {
        // 1_700_000_000 s and 123_456_789 ns truncate to 1_700_000_000_123 ms, as in the rules.
        assertEquals(
                Fixtures.ENTRY.getEmail() + "_1700000000123_1",
                RosterMappers.correctionId(Fixtures.ENTRY, ADDED_AT));
    }

    @Test
    public void correctionRecordsCarryTheRightOptionalFields() throws IOException {
        final Set<String> required = DocumentContract.fields("registryCorrections", false);
        final Set<String> all = DocumentContract.fields("registryCorrections", true);

        // A claimed entry being updated: every optional field.
        final Map<String, Object> update =
                RosterMappers.correction(
                        Fixtures.ENTRY,
                        CorrectionAction.UPDATE,
                        "Mistyped",
                        "teacherA",
                        "262011099",
                        "MC26");
        assertEquals(all, keys(update));
        assertEquals(Fixtures.STUDENT.getUid(), update.get("releasedUid"));
        assertEquals("UPDATE", update.get("action"));

        // An unclaimed entry being removed: only the required fields.
        final Map<String, Object> remove =
                RosterMappers.correction(
                        Fixtures.OTHER_ENTRY,
                        CorrectionAction.REMOVE,
                        "Left",
                        "teacherA",
                        null,
                        null);
        assertEquals(required, keys(remove));
    }

    @Test
    public void alertsHaveTheContractFieldsWithAndWithoutAnEnteredRollNumber() throws IOException {
        final Map<String, Object> plain =
                RosterMappers.newAlert(
                        "u",
                        "x@gmail.com",
                        IdentityAlert.Type.NON_VJTI_EMAIL,
                        null,
                        IdentityAlert.AUDIENCE_ALL);
        assertEquals(DocumentContract.fields("identityAlerts", false), keys(plain));
        assertEquals("NON_VJTI_EMAIL", plain.get("type"));

        final Map<String, Object> attempt =
                RosterMappers.newAlert(
                        "u",
                        "a@mc.vjti.ac.in",
                        IdentityAlert.Type.ROLL_MISMATCH,
                        "262019999",
                        "teacherA");
        assertEquals(DocumentContract.fields("identityAlerts", true), keys(attempt));
    }
}
