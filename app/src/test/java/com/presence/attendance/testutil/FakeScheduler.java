package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.session.domain.TokenRotation;
import java.util.ArrayList;
import java.util.List;

/** A clock and timer queue that a test advances by hand. */
public final class FakeScheduler implements TokenRotation.Scheduler {

    public long now;
    private final List<Long> due = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();

    @NonNull
    @Override
    public Runnable schedule(@NonNull Runnable task, long delayMs) {
        due.add(now + delayMs);
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
    public void advance(long ms) {
        final long end = now + ms;
        while (true) {
            int next = -1;
            for (int i = 0; i < due.size(); i++) {
                if (due.get(i) <= end && (next < 0 || due.get(i) < due.get(next))) {
                    next = i;
                }
            }
            if (next < 0) {
                break;
            }
            now = due.remove(next);
            tasks.remove(next).run();
        }
        now = end;
    }
}
