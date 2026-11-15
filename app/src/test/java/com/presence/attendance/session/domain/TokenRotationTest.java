package com.presence.attendance.session.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

public final class TokenRotationTest {

    /** A clock and timer queue the test advances by hand. */
    private static final class FakeTime implements TokenRotation.Scheduler {
        long now;
        private final List<long[]> due = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        @NonNull
        @Override
        public Runnable schedule(@NonNull Runnable task, long delayMs) {
            final long[] at = {now + delayMs};
            due.add(at);
            tasks.add(task);
            return () -> {
                final int i = tasks.indexOf(task);
                if (i >= 0) {
                    tasks.remove(i);
                    due.remove(i);
                }
            };
        }

        /** Moves time forward, running every timer that falls due, in order. */
        void advance(long ms) {
            final long end = now + ms;
            while (true) {
                int next = -1;
                for (int i = 0; i < due.size(); i++) {
                    if (due.get(i)[0] <= end && (next < 0 || due.get(i)[0] < due.get(next)[0])) {
                        next = i;
                    }
                }
                if (next < 0) {
                    break;
                }
                now = due.get(next)[0];
                final Runnable task = tasks.remove(next);
                due.remove(next);
                task.run();
            }
            now = end;
        }
    }

    private final FakeTime time = new FakeTime();
    private final List<String> written = new ArrayList<>();
    private final List<Consumer<Boolean>> pending = new ArrayList<>();
    private final List<TokenRotation.State> states = new ArrayList<>();
    private int counter;
    private TokenRotation rotation;

    @Before
    public void setUp() {
        rotation =
                new TokenRotation(
                        () -> "token" + (++counter),
                        (token, onResult) -> {
                            written.add(token);
                            pending.add(onResult);
                        },
                        time,
                        () -> time.now,
                        states::add);
    }

    private TokenRotation.State state() {
        return states.get(states.size() - 1);
    }

    /** The server answers the oldest outstanding write. */
    private void answer(boolean acknowledged) {
        pending.remove(0).accept(acknowledged);
    }

    @Test
    public void aTokenIsShownOnlyAfterTheServerAcknowledgesIt() {
        rotation.start();
        assertEquals(1, written.size());
        assertTrue("nothing to show while the first write is in flight", states.isEmpty());
        time.advance(300);
        answer(true);
        assertEquals("token1", state().token);
        assertFalse(state().reconnecting);
    }

    @Test
    public void theNextWriteStartsTwelveSecondsAfterTheAcknowledgement() {
        rotation.start();
        time.advance(400);
        answer(true);
        assertEquals(400 + 12_000, state().rotatesAtMs);

        time.advance(11_999);
        assertEquals("not yet", 1, written.size());
        time.advance(1);
        assertEquals(2, written.size());
        assertEquals(
                "the old token stays up until the new one is acknowledged",
                "token1",
                state().token);
        time.advance(200);
        answer(true);
        assertEquals("token2", state().token);
    }

    @Test
    public void thereIsNeverMoreThanOneWriteInFlight() {
        rotation.start();
        rotation.start();
        time.advance(60_000);
        assertEquals("still waiting for the first answer", 1, written.size());
        assertEquals(1, pending.size());
    }

    @Test
    public void aFailedWriteShowsReconnectingAndIsRetried() {
        rotation.start();
        answer(false);
        assertTrue(state().reconnecting);
        assertNull(state().token);
        time.advance(TokenRotation.RETRY_MS);
        assertEquals(2, written.size());
        answer(true);
        assertFalse(state().reconnecting);
        assertEquals("token2", state().token);
    }

    @Test
    public void aShownTokenIsBlankedTwentySecondsAfterItsWriteStarted() {
        rotation.start(); // write starts at t=0
        time.advance(1_000);
        answer(true); // acknowledged at t=1000, shown
        time.advance(12_000); // t=13000: next write starts
        answer(false); // and fails; the old token is still valid
        assertEquals("token1", state().token);
        assertTrue(state().reconnecting);

        // Keep failing. The token must go blank at t=20000: 20 s after its write started,
        // not 20 s after it was acknowledged.
        time.advance(2_000);
        answer(false);
        time.advance(2_000);
        answer(false);
        time.advance(2_000); // t=19000
        answer(false);
        assertEquals("token1", state().token);
        time.advance(999); // t=19999
        assertEquals("token1", state().token);
        time.advance(1); // t=20000
        assertNull(state().token);
        assertTrue(state().reconnecting);
    }

    @Test
    public void anAcknowledgementThatArrivesTooLateIsNotShown() {
        rotation.start();
        time.advance(16_000); // only 4 s of validity would remain
        answer(true);
        assertNull(state().token);
        assertTrue(state().reconnecting);
        assertEquals("a fresh token is written at once", 2, written.size());
        time.advance(100);
        answer(true);
        assertEquals("token2", state().token);
    }

    @Test
    public void stoppingBlanksTheQrAndIgnoresLateResults() {
        rotation.start();
        answer(true);
        time.advance(12_000);
        assertEquals(2, written.size());

        rotation.stop();
        assertNull(state().token);
        final int publishedAtStop = states.size();
        answer(true); // the in-flight write is acknowledged after the stop
        assertEquals("the late result changes nothing", publishedAtStop, states.size());
        time.advance(120_000);
        assertEquals("no more writes after stop", 2, written.size());
    }

    @Test
    public void itCanBeStartedAgainAfterAStop() {
        rotation.start();
        answer(true);
        rotation.stop();
        rotation.start();
        assertEquals(2, written.size());
        pending.remove(pending.size() - 1).accept(true);
        assertEquals("token2", state().token);
    }

    @Test
    public void aSteadySessionWritesFiveTokensAMinute() {
        rotation.start();
        // Just short of a full minute: the sixth write falls exactly on the 60 s mark.
        for (long t = 0; t < 59_900; t += 100) {
            if (!pending.isEmpty()) {
                answer(true);
            }
            time.advance(100);
        }
        assertEquals(5, written.size());
    }
}
