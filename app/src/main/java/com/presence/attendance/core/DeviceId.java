package com.presence.attendance.core;

import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * The id a phone marks attendance with, so the rules can refuse a second student on the same phone
 * in one session.
 *
 * <p>Derived from the id Android gives this app on this phone, which survives reinstalling and
 * clearing data. Only a hash leaves the phone, in the shape of a token: {@link
 * Constants#TOKEN_LENGTH} url-safe characters. It is sent by the app, so a modified app can send
 * any value; this stops the ordinary case, not a determined one.
 */
public final class DeviceId {

    private DeviceId() {}

    @NonNull
    public static String of(@NonNull String androidId) {
        try {
            final byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(androidId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, Constants.TOKEN_BYTES));
        } catch (NoSuchAlgorithmException e) {
            // Every Android and Java runtime is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
