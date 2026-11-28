package com.presence.attendance.course.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.Timetable;
import com.presence.attendance.department.domain.TimetableSlot;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Home screen state: the signed-in user and the courses they teach or have joined, and for the HOD
 * the department's batches.
 */
public final class HomeViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR,
        SIGNED_OUT,
        /** A signed-in student address that has not linked a roll number yet. */
        REGISTRATION_REQUIRED
    }

    /**
     * Immutable screen state. {@code user} is known in every state reached after loading it. {@code
     * liveSessionByCourse} maps a course id to its running session, for faculty.
     */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final User user;
        @NonNull public final List<Course> courses;
        @NonNull public final Map<String, String> liveSessionByCourse;

        /** The department's batches, for the HOD; empty for everyone else. */
        @NonNull public final List<Batch> batches;

        /**
         * Today's classes of the user's subjects, earliest first, from their batches' timetables.
         */
        @NonNull public final List<TimetableSlot> today;

        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @Nullable User user,
                @NonNull List<Course> courses,
                @NonNull Map<String, String> liveSessionByCourse,
                @NonNull List<Batch> batches,
                @NonNull List<TimetableSlot> today,
                @Nullable AppError error) {
            this.status = status;
            this.user = user;
            this.courses = Collections.unmodifiableList(courses);
            this.liveSessionByCourse = Collections.unmodifiableMap(liveSessionByCourse);
            this.batches = Collections.unmodifiableList(batches);
            this.today = Collections.unmodifiableList(today);
            this.error = error;
        }

        /** The course a slot of {@link #today} is for, or null if it is not one of the user's. */
        @Nullable
        public Course courseOf(@NonNull TimetableSlot slot) {
            for (Course course : courses) {
                if (course.getId().equals(slot.getCourseId())) {
                    return course;
                }
            }
            return null;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final SessionRepository sessionRepository;
    private final BatchRepository batchRepository;
    private final LongSupplier wallClockMs;
    private final ZoneId zone;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(
                    new State(
                            Status.LOADING,
                            null,
                            Collections.emptyList(),
                            new HashMap<>(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            null));

    public HomeViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courseRepository,
            @NonNull SessionRepository sessionRepository,
            @NonNull BatchRepository batchRepository,
            @NonNull LongSupplier wallClockMs,
            @NonNull ZoneId zone) {
        this.auth = auth;
        this.zone = zone;
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.batchRepository = batchRepository;
        this.wallClockMs = wallClockMs;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Reloads: after a failure, after a change, and whenever the screen comes back into view. */
    public void refresh() {
        load();
    }

    public void signOut() {
        auth.signOut();
        set(Status.SIGNED_OUT, null, Collections.emptyList(), null);
    }

    private void load() {
        final State current = current();
        // Keep showing the list during a refresh; only an empty screen shows the spinner.
        if (current.status != Status.CONTENT) {
            set(Status.LOADING, current.user, current.courses, null);
        }
        auth.loadUser(this::onUser);
    }

    private void onUser(@NonNull Outcome<User> outcome) {
        if (!outcome.isSuccess()) {
            final AppError error = outcome.getError();
            final Status status;
            if (error == AppError.REGISTRATION_REQUIRED) {
                status = Status.REGISTRATION_REQUIRED;
            } else if (error == AppError.UNAUTHENTICATED || error == AppError.NOT_VJTI_ACCOUNT) {
                status = Status.SIGNED_OUT;
            } else {
                status = Status.ERROR;
            }
            set(status, null, Collections.emptyList(), error);
            return;
        }
        final User user = outcome.getValue();
        // Subjects first: a teacher claims what the HOD assigned, a student joins their batch's.
        // A failed sync only means the list is as it was; it is tried again on every visit.
        courseRepository.syncSubjects(user, synced -> loadCourses(user));
    }

    private void loadCourses(@NonNull User user) {
        final Callback<List<Course>> onCourses = courses -> onCourses(user, courses);
        if (user.getRole() == Role.FACULTY) {
            courseRepository.loadTeacherCourses(user.getUid(), onCourses);
        } else {
            courseRepository.loadStudentCourses(user.getUid(), onCourses);
        }
    }

    private void onCourses(@NonNull User user, @NonNull Outcome<List<Course>> outcome) {
        if (!outcome.isSuccess()) {
            set(Status.ERROR, user, Collections.emptyList(), outcome.getError());
            return;
        }
        final List<Course> courses = outcome.getValue();
        boolean hasSubject = false;
        for (Course course : courses) {
            hasSubject |= course.isSubject();
        }
        // The batches give the HOD's department section, and everyone's current semesters.
        if (user.isHod() || hasSubject) {
            batchRepository.loadBatches(batches -> onBatches(user, courses, batches));
            return;
        }
        showCourses(user, courses, Collections.emptyList());
    }

    private void onBatches(
            @NonNull User user,
            @NonNull List<Course> courses,
            @NonNull Outcome<List<Batch>> outcome) {
        if (!outcome.isSuccess()) {
            if (user.isHod()) {
                set(Status.ERROR, user, courses, outcome.getError());
            } else {
                // The courses still show, without today's classes.
                showCourses(user, courses, Collections.emptyList());
            }
            return;
        }
        final List<Batch> batches = outcome.getValue();
        showCourses(user, courses, user.isHod() ? batches : Collections.emptyList());
        // Only the current semester's timetable of each batch the user has a subject in.
        final List<String> ids = new ArrayList<>();
        for (Batch batch : batches) {
            final String id = Timetable.idOf(batch.getId(), batch.getSemester());
            for (Course course : courses) {
                if (batch.getId().equals(course.getBatchId())
                        && course.getSemester() == batch.getSemester()
                        && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (!ids.isEmpty()) {
            batchRepository.loadTimetables(ids, timetables -> onTimetables(courses, timetables));
        }
    }

    /** The HOD always gets the department section, courses or not. */
    private void showCourses(
            @NonNull User user, @NonNull List<Course> courses, @NonNull List<Batch> batches) {
        final State s = current();
        final boolean content = user.isHod() || !courses.isEmpty();
        final Map<String, String> live =
                s.status == Status.CONTENT ? s.liveSessionByCourse : new HashMap<>();
        final List<TimetableSlot> today =
                s.status == Status.CONTENT ? s.today : Collections.<TimetableSlot>emptyList();
        state.setValue(
                new State(
                        content ? Status.CONTENT : Status.EMPTY,
                        user,
                        courses,
                        live,
                        batches,
                        today,
                        null));
        if (user.getRole() == Role.FACULTY && !courses.isEmpty()) {
            sessionRepository.loadActiveSessions(user.getUid(), this::onActiveSessions);
        }
    }

    /** Today's classes. A timetable that cannot be read just leaves the section out. */
    private void onTimetables(
            @NonNull List<Course> courses, @NonNull Outcome<List<Timetable>> outcome) {
        final State s = current();
        if (!outcome.isSuccess() || s.status != Status.CONTENT) {
            return;
        }
        final List<String> ids = new ArrayList<>();
        for (Course course : courses) {
            ids.add(course.getId());
        }
        final int day =
                Instant.ofEpochMilli(wallClockMs.getAsLong())
                        .atZone(zone)
                        .getDayOfWeek()
                        .getValue();
        state.setValue(
                new State(
                        s.status,
                        s.user,
                        s.courses,
                        s.liveSessionByCourse,
                        s.batches,
                        Timetable.on(outcome.getValue(), day, ids),
                        s.error));
    }

    /** Minutes since midnight now, in the phone's zone, to tell a class that is on now. */
    public int minutesNow() {
        final ZonedDateTime now = Instant.ofEpochMilli(wallClockMs.getAsLong()).atZone(zone);
        return now.getHour() * 60 + now.getMinute();
    }

    /** Marks the courses that have a session running. A failure just leaves them unmarked. */
    private void onActiveSessions(@NonNull Outcome<List<Session>> outcome) {
        final State s = current();
        if (!outcome.isSuccess() || s.status != Status.CONTENT) {
            return;
        }
        final long now = wallClockMs.getAsLong();
        final Map<String, String> live = new HashMap<>();
        for (Session session : outcome.getValue()) {
            if (session.isLive(now)) {
                live.put(session.getCourseId(), session.getId());
            }
        }
        state.setValue(new State(s.status, s.user, s.courses, live, s.batches, s.today, s.error));
    }

    private void set(
            @NonNull Status status,
            @Nullable User user,
            @NonNull List<Course> courses,
            @Nullable AppError error) {
        final State s = current();
        final Map<String, String> live =
                status == Status.CONTENT ? s.liveSessionByCourse : new HashMap<>();
        // A refresh keeps the department section until the new batches arrive.
        final List<Batch> batches =
                user != null && user.isHod() ? s.batches : Collections.<Batch>emptyList();
        final List<TimetableSlot> today =
                status == Status.CONTENT ? s.today : Collections.<TimetableSlot>emptyList();
        state.setValue(new State(status, user, courses, live, batches, today, error));
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
