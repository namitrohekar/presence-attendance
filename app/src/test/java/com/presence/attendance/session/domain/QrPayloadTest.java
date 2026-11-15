package com.presence.attendance.session.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class QrPayloadTest {

    private static final String SESSION = "kYp3Qv9sT2mLx7Zb1cDe";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrSt_-";

    @Test
    public void buildThenParseRoundTrips() {
        final String text = QrPayload.build(SESSION, TOKEN);
        assertEquals("prs1:" + SESSION + ":" + TOKEN, text);
        final QrPayload parsed = QrPayload.parse(text);
        assertNotNull(parsed);
        assertEquals(SESSION, parsed.getSessionId());
        assertEquals(TOKEN, parsed.getToken());
    }

    @Test
    public void anythingElseIsRejected() {
        final String good = "prs1:" + SESSION + ":" + TOKEN;
        for (String text :
                new String[] {
                    null,
                    "",
                    "hello",
                    "https://example.com/?q=" + good,
                    " " + good,
                    good + " ",
                    good + "\n",
                    good + ":extra",
                    "prs2:" + SESSION + ":" + TOKEN,
                    "PRS1:" + SESSION + ":" + TOKEN,
                    "prs1::" + TOKEN,
                    "prs1:" + SESSION + ":",
                    "prs1:" + SESSION,
                    "prs1:" + SESSION + ":" + TOKEN.substring(1),
                    "prs1:" + SESSION + ":" + TOKEN + "x",
                    "prs1:" + SESSION + ":" + TOKEN.replace('_', '+'),
                    "prs1:sess/../other:" + TOKEN,
                    "prs1:sess id:" + TOKEN,
                    "prs1:" + "a".repeat(129) + ":" + TOKEN,
                }) {
            assertNull(String.valueOf(text), QrPayload.parse(text));
        }
    }

    @Test
    public void buildRefusesInvalidParts() {
        assertThrows(IllegalArgumentException.class, () -> QrPayload.build("bad/id", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> QrPayload.build(SESSION, "short"));
    }
}
