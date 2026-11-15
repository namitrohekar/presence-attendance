package com.presence.attendance.roster.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.AlertRepository;
import com.presence.attendance.roster.domain.IdentityAlert;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faculty alerts, live while the screen is open. A mismatch alert only records the number that was
 * typed; whose number it is comes from the roster, looked up here.
 */
public final class AlertsViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** Immutable screen state. {@code emailByRollNo} is empty until the roster has loaded. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<IdentityAlert> alerts;
        @NonNull public final Map<String, String> emailByRollNo;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<IdentityAlert> alerts,
                @NonNull Map<String, String> emailByRollNo,
                @Nullable AppError error) {
            this.status = status;
            this.alerts = Collections.unmodifiableList(alerts);
            this.emailByRollNo = Collections.unmodifiableMap(emailByRollNo);
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final AlertRepository alertRepository;
    private final RosterRepository roster;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(
                    new State(Status.LOADING, Collections.emptyList(), new HashMap<>(), null));
    @Nullable private LiveData<Outcome<List<IdentityAlert>>> source;
    private boolean rosterRequested;

    public AlertsViewModel(
            @NonNull AuthRepository auth,
            @NonNull AlertRepository alertRepository,
            @NonNull RosterRepository roster) {
        this.auth = auth;
        this.alertRepository = alertRepository;
        this.roster = roster;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Starts listening again after a failure. */
    public void retry() {
        load();
    }

    private void load() {
        if (source != null) {
            state.removeSource(source);
            source = null;
        }
        set(Status.LOADING, Collections.emptyList(), null);
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                set(Status.ERROR, Collections.emptyList(), user.getError());
                return;
            }
            final String uid = user.getValue().getUid();
            alertRepository.purgeExpired(uid);
            source = alertRepository.observe(uid);
            state.addSource(source, this::onAlerts);
        });
    }

    private void onAlerts(@NonNull Outcome<List<IdentityAlert>> outcome) {
        if (!outcome.isSuccess()) {
            set(Status.ERROR, Collections.emptyList(), outcome.getError());
            return;
        }
        final List<IdentityAlert> alerts = outcome.getValue();
        set(alerts.isEmpty() ? Status.EMPTY : Status.CONTENT, alerts, null);
        loadRosterOnceIfNeeded(alerts);
    }

    /** One roster read per screen visit, and only if an alert needs a number looked up. */
    private void loadRosterOnceIfNeeded(@NonNull List<IdentityAlert> alerts) {
        boolean needed = false;
        for (IdentityAlert alert : alerts) {
            needed |= alert.getType() == IdentityAlert.Type.ROLL_MISMATCH;
        }
        if (!needed || rosterRequested) {
            return;
        }
        rosterRequested = true;
        roster.loadRoster(outcome -> {
            if (!outcome.isSuccess()) {
                // The alerts still show; only the "belongs to" detail is missing.
                return;
            }
            final Map<String, String> emailByRollNo = new HashMap<>();
            for (RosterEntry entry : outcome.getValue()) {
                emailByRollNo.put(entry.getRollNo(), entry.getEmail());
            }
            final State s = current();
            state.setValue(new State(s.status, s.alerts, emailByRollNo, s.error));
        });
    }

    private void set(
            @NonNull Status status, @NonNull List<IdentityAlert> alerts, @Nullable AppError error) {
        state.setValue(new State(status, alerts, current().emailByRollNo, error));
    }

    @NonNull
    private State current() {
        final State value = state.getValue();
        if (value == null) {
            throw new IllegalStateException("State is always set");
        }
        return value;
    }
}
