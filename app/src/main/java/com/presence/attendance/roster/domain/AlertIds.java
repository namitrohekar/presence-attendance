package com.presence.attendance.roster.domain;

import androidx.annotation.NonNull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Builds identity alert document ids exactly as the security rules rebuild them. */
public final class AlertIds {

    private AlertIds() {}

    /**
     * {@code {uid}_{type}_{year}-{month}-{day}} with the date in UTC and without zero padding. The
     * rules compute the same id from the server clock and refuse any other, which is what limits an
     * account to one alert per type per day.
     */
    @NonNull
    public static String of(
            @NonNull String uid, @NonNull IdentityAlert.Type type, @NonNull Instant now) {
        final ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        return uid
                + "_"
                + type.name()
                + "_"
                + utc.getYear()
                + "-"
                + utc.getMonthValue()
                + "-"
                + utc.getDayOfMonth();
    }
}
