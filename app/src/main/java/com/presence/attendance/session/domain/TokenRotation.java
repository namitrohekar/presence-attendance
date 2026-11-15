package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.core.Constants;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Decides which token the faculty screen shows, and when to write the next one.
 *
 * <p>Rules it keeps:
 *
 * <ul>
 *   <li>A token is shown only after the server has acknowledged its write.
 *   <li>The next write starts {@link Constants#ROTATION_S} seconds after the previous
 *       acknowledgement, and there is never more than one write in flight.
 *   <li>A shown token is blanked {@link Constants#TOKEN_VALIDITY_S} seconds after its write
 *       <em>started</em>. The server stamped it at some point after that, so the QR is never shown
 *       for longer than the server will accept it.
 *   <li>A failed write is retried; the screen says it is reconnecting in the meantime.
 * </ul>
 *
 * <p>Pure Java with every dependency injected, so the timing is tested without Android. The clock
 * is only used for these UI timers; nothing is validated with it.
 */
public final class TokenRotation {

    static final long ROTATION_MS = Constants.ROTATION_S * 1000L;
    static final long VALIDITY_MS = Constants.TOKEN_VALIDITY_S * 1000L;
    static final long RETRY_MS = 2_000L;

    /** An acknowledgement this late leaves too little validity to be worth showing. */
    static final long MIN_USEFUL_MS = 5_000L;

    /** Runs a task later. Returns a handle that cancels it. */
    public interface Scheduler {
        @NonNull
        Runnable schedule(@NonNull Runnable task, long delayMs);
    }

    /** Writes a token to the server and reports whether the server acknowledged it. */
    public interface Writer {
        void write(@NonNull String token, @NonNull Consumer<Boolean> onResult);
    }

    /** What the screen should show. Immutable. */
    public static final class State {
        /** The token to render as a QR, or null to show a blank. */
        @Nullable public final String token;

        public final boolean reconnecting;

        /** When the shown token will be replaced, on the injected clock; 0 if none is shown. */
        public final long rotatesAtMs;

        State(@Nullable String token, boolean reconnecting, long rotatesAtMs) {
            this.token = token;
            this.reconnecting = reconnecting;
            this.rotatesAtMs = rotatesAtMs;
        }
    }

    private final Supplier<String> generator;
    private final Writer writer;
    private final Scheduler scheduler;
    private final LongSupplier clockMs;
    private final Consumer<State> listener;

    private boolean running;
    private boolean inFlight;
    // Bumped on every stop, so a result that arrives for an earlier run is ignored.
    private int run;
    @Nullable private String token;
    private boolean reconnecting;
    private long rotatesAtMs;
    @Nullable private Runnable cancelNext;
    @Nullable private Runnable cancelExpiry;

    public TokenRotation(
            @NonNull Supplier<String> generator,
            @NonNull Writer writer,
            @NonNull Scheduler scheduler,
            @NonNull LongSupplier clockMs,
            @NonNull Consumer<State> listener) {
        this.generator = generator;
        this.writer = writer;
        this.scheduler = scheduler;
        this.clockMs = clockMs;
        this.listener = listener;
    }

    /** Starts writing tokens. Does nothing if already running. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        writeNext();
    }

    /** Stops writing and blanks the QR at once. Safe to call repeatedly. */
    public void stop() {
        running = false;
        run++;
        inFlight = false;
        cancel(cancelNext);
        cancel(cancelExpiry);
        cancelNext = null;
        cancelExpiry = null;
        token = null;
        reconnecting = false;
        rotatesAtMs = 0;
        publish();
    }

    private void writeNext() {
        if (!running || inFlight) {
            return;
        }
        inFlight = true;
        final String candidate = generator.get();
        final long startedAtMs = clockMs.getAsLong();
        final int thisRun = run;
        writer.write(
                candidate, acknowledged -> onResult(thisRun, candidate, startedAtMs, acknowledged));
    }

    private void onResult(
            int thisRun, @NonNull String candidate, long startedAtMs, boolean acknowledged) {
        if (thisRun != run) {
            return;
        }
        inFlight = false;
        final long now = clockMs.getAsLong();
        final long remainingMs = startedAtMs + VALIDITY_MS - now;
        if (!acknowledged) {
            reconnecting = true;
            cancelNext = scheduler.schedule(this::writeNext, RETRY_MS);
        } else if (remainingMs < MIN_USEFUL_MS) {
            // Acknowledged, but so late that it is nearly expired: replace it straight away.
            reconnecting = true;
            writeNext();
        } else {
            token = candidate;
            reconnecting = false;
            rotatesAtMs = now + ROTATION_MS;
            cancel(cancelExpiry);
            cancelExpiry = scheduler.schedule(() -> expire(candidate), remainingMs);
            cancelNext = scheduler.schedule(this::writeNext, ROTATION_MS);
        }
        publish();
    }

    private void expire(@NonNull String expired) {
        if (expired.equals(token)) {
            token = null;
            rotatesAtMs = 0;
            publish();
        }
    }

    private void publish() {
        listener.accept(new State(token, reconnecting, rotatesAtMs));
    }

    private static void cancel(@Nullable Runnable handle) {
        if (handle != null) {
            handle.run();
        }
    }
}
