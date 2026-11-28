package com.presence.attendance.session.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Explicit mapping for {@code sessions}, their {@code tokens}, and {@code appConfig/campus}. */
final class SessionMappers {

    static final String COURSE_ID = "courseId";
    static final String TEACHER_ID = "teacherId";
    static final String STATUS = "status";
    static final String STARTED_AT = "startedAt";
    static final String ENDS_AT = "endsAt";
    private static final String GEOFENCE = "geofence";
    private static final String NAME = "name";
    private static final String SAVED_BY = "savedBy";
    private static final String SAVED_AT = "savedAt";
    static final String HEADCOUNT = "headcount";
    private static final String LAT = "lat";
    private static final String LNG = "lng";
    private static final String RADIUS_M = "radiusM";
    private static final String COS_LAT = "cosLat";
    private static final String VALID_FROM = "validFrom";

    private SessionMappers() {}

    /** The session in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    static Session session(@NonNull DocumentSnapshot snapshot) {
        final String courseId = Snapshots.string(snapshot, COURSE_ID);
        final String teacherId = Snapshots.string(snapshot, TEACHER_ID);
        final Session.Status status = status(Snapshots.string(snapshot, STATUS));
        // A session this client just created has no server time yet; estimate until it does.
        final Timestamp startedAt =
                snapshot.getTimestamp(
                        STARTED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        final Timestamp endsAt =
                snapshot.getTimestamp(ENDS_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        final Geofence geofence = geofence(snapshot.get(GEOFENCE));
        if (courseId == null
                || teacherId == null
                || status == null
                || startedAt == null
                || endsAt == null
                || geofence == null) {
            return null;
        }
        return new Session(
                snapshot.getId(),
                courseId,
                teacherId,
                status,
                startedAt.toDate().getTime(),
                endsAt.toDate().getTime(),
                geofence,
                headcount(Snapshots.integer(snapshot, HEADCOUNT)));
    }

    /** A stored headcount, or null if there is none or it is out of range. */
    @Nullable
    private static Integer headcount(@Nullable Long value) {
        return value != null && value >= 0 && value <= Constants.HEADCOUNT_MAX
                ? value.intValue()
                : null;
    }

    /** The campus geofence, or null if {@code appConfig/campus} is missing or malformed. */
    @Nullable
    static Geofence campus(@NonNull DocumentSnapshot snapshot) {
        return geofence(snapshot.getData());
    }

    @NonNull
    private static Map<String, Object> area(@NonNull Geofence geofence) {
        final Map<String, Object> area = new HashMap<>();
        area.put(LAT, geofence.getLat());
        area.put(LNG, geofence.getLng());
        area.put(RADIUS_M, geofence.getRadiusM());
        area.put(COS_LAT, geofence.getCosLat());
        return area;
    }

    /** A room's document id: its name in capitals without punctuation, "AL-301" is "AL301". */
    @NonNull
    static String roomId(@NonNull String name) {
        return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    @NonNull
    static Map<String, Object> newRoom(
            @NonNull String name, @NonNull Geofence geofence, @NonNull String teacherId) {
        final Map<String, Object> document = new HashMap<>();
        document.put(NAME, name);
        document.put(GEOFENCE, area(geofence));
        document.put(SAVED_BY, teacherId);
        document.put(SAVED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** A room's saved geofence, or null if the room is missing or malformed. */
    @Nullable
    static Geofence room(@NonNull DocumentSnapshot snapshot) {
        return geofence(snapshot.get(GEOFENCE));
    }

    @NonNull
    static Map<String, Object> newSession(
            @NonNull String courseId,
            @NonNull String teacherId,
            @NonNull Date endsAt,
            @NonNull Geofence geofence) {
        final Map<String, Object> area = area(geofence);
        final Map<String, Object> document = new HashMap<>();
        document.put(COURSE_ID, courseId);
        document.put(TEACHER_ID, teacherId);
        document.put(STATUS, Session.Status.ACTIVE.name());
        document.put(STARTED_AT, FieldValue.serverTimestamp());
        document.put(ENDS_AT, new Timestamp(endsAt));
        document.put(GEOFENCE, area);
        return document;
    }

    @NonNull
    static Map<String, Object> newToken() {
        final Map<String, Object> document = new HashMap<>();
        document.put(VALID_FROM, FieldValue.serverTimestamp());
        return document;
    }

    @Nullable
    private static Session.Status status(@Nullable String value) {
        for (Session.Status status : Session.Status.values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }

    @Nullable
    private static Geofence geofence(@Nullable Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final Double lat = number(map.get(LAT));
        final Double lng = number(map.get(LNG));
        final Double radiusM = number(map.get(RADIUS_M));
        final Double cosLat = number(map.get(COS_LAT));
        if (lat == null || lng == null || radiusM == null || cosLat == null) {
            return null;
        }
        return new Geofence(lat, lng, radiusM, cosLat);
    }

    /** Firestore stores whole numbers as integers, so both kinds are accepted. */
    @Nullable
    private static Double number(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }
}
