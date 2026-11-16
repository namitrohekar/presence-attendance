package com.presence.attendance.attendance.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.presence.attendance.BuildConfig;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMarkSync;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppContainer;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.session.domain.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Sends saved scans when the device has a connection, whether or not the app is open. WorkManager
 * runs it again with a growing delay for as long as a scan is still pending.
 */
public final class PendingMarkSyncWorker extends Worker {

    private static final String WORK_NAME = "pending-mark-sync";
    private static final long FIRST_RETRY_S = 30;
    private static final long STEP_TIMEOUT_S = 30;

    public PendingMarkSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    /** Asks for a sync as soon as there is a connection. Safe to call at any time. */
    public static void request(@NonNull Context context) {
        final OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PendingMarkSyncWorker.class)
                        .setConstraints(
                                new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build())
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL, FIRST_RETRY_S, TimeUnit.SECONDS)
                        .build();
        // Appended, not dropped: a scan saved while a sync is running is picked up by the next.
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            return Result.success();
        }
        final AppContainer container = ((PresenceApp) getApplicationContext()).getContainer();
        final Consumer<Runnable> main = container.executors().main()::execute;

        final Outcome<User> user = await(main, done -> container.auth().loadUser(done));
        if (!user.isSuccess()) {
            // Signed out: the scans wait for their owner. Unreachable: try again later.
            return isUnreachable(user.getError()) ? Result.retry() : Result.success();
        }
        final PendingMarkSync sync =
                new PendingMarkSync(
                        container.pendingMarks(),
                        new PendingMarkSync.Remote() {
                            @NonNull
                            @Override
                            public Outcome<Session> loadSession(@NonNull String sessionId) {
                                return await(
                                        main,
                                        done -> container.sessions().loadSession(sessionId, done));
                            }

                            @NonNull
                            @Override
                            public Outcome<MarkResult> markQueued(
                                    @NonNull Session session, @NonNull PendingMark mark) {
                                return await(
                                        main,
                                        done ->
                                                container
                                                        .attendance()
                                                        .markQueued(session, mark, done));
                            }
                        });
        final boolean retry = sync.run(user.getValue().getUid(), System.currentTimeMillis());
        return retry ? Result.retry() : Result.success();
    }

    private static boolean isUnreachable(@NonNull AppError error) {
        return error == AppError.OFFLINE || error == AppError.TIMEOUT;
    }

    /**
     * Runs a main-thread repository call from this worker thread and waits for its answer. No
     * answer in time counts as a timeout, which leaves the scan pending.
     */
    @NonNull
    private static <T> Outcome<T> await(
            @NonNull Consumer<Runnable> main, @NonNull Consumer<Callback<T>> call) {
        final CountDownLatch answered = new CountDownLatch(1);
        final AtomicReference<Outcome<T>> answer =
                new AtomicReference<>(Outcome.failure(AppError.TIMEOUT));
        main.accept(() ->
                call.accept(result -> {
                    answer.set(result);
                    answered.countDown();
                }));
        try {
            if (!answered.await(STEP_TIMEOUT_S, TimeUnit.SECONDS)) {
                return Outcome.failure(AppError.TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failure(AppError.TIMEOUT);
        }
        return answer.get();
    }
}
