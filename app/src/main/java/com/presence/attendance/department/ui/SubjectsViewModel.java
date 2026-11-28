package com.presence.attendance.department.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.CourseCount;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.CourseRepository.SaveOutcome;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.FacultyRepository;
import com.presence.attendance.department.domain.Timetable;
import com.presence.attendance.department.domain.TimetableCsv;
import com.presence.attendance.department.domain.TimetablePlan;
import com.presence.attendance.roster.domain.RosterCsv;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HOD: one batch's subjects for its current semester, who teaches each, and its weekly timetable. A
 * timetable file is checked whole and previewed; saving then writes each subject one at a time and
 * the timetable last. Shared by the subjects screen and its preview.
 */
public final class SubjectsViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        ERROR
    }

    public enum Phase {
        IDLE,
        PREVIEW,
        SAVING,
        DONE
    }

    /** What saving a subject of the plan will do, or did. */
    public enum Change {
        NEW,
        /** A new name or teacher. */
        UPDATE,
        SAME,
        FAILED
    }

    /** Immutable screen state. */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final Batch batch;

        /** The current semester's subjects, by name and then lab group. */
        @NonNull public final List<Course> subjects;

        @NonNull public final List<FacultyMember> faculty;

        /** The current semester's timetable, or null if none has been imported. */
        @Nullable public final Timetable timetable;

        @Nullable public final AppError error;
        public final boolean online;

        @NonNull public final Phase phase;

        /** Every row of the chosen file with its check. */
        @NonNull public final List<TimetableCsv.Row> rows;

        /** What the file's usable rows make; null outside an import. */
        @Nullable public final TimetablePlan plan;

        /** For each subject of the plan, in order: what saving it will do, or did. */
        @NonNull public final List<Change> changes;

        /** How many subjects of the plan have been written so far. */
        public final int saved;

        @Nullable public final RosterCsv.FileProblem fileProblem;
        @Nullable public final AppError fileError;
        public final boolean openPreview;

        /** Each claimed subject's counted attendance, by course id, once it has been counted. */
        @NonNull public final Map<String, CourseCount> counts;

        State(Builder b) {
            status = b.status;
            batch = b.batch;
            subjects = Collections.unmodifiableList(b.subjects);
            faculty = Collections.unmodifiableList(b.faculty);
            timetable = b.timetable;
            error = b.error;
            online = b.online;
            phase = b.phase;
            rows = Collections.unmodifiableList(b.rows);
            plan = b.plan;
            changes = Collections.unmodifiableList(b.changes);
            saved = b.saved;
            fileProblem = b.fileProblem;
            fileError = b.fileError;
            openPreview = b.openPreview;
            counts = Collections.unmodifiableMap(b.counts);
        }

        /** How many of the plan's subjects will be, or were, saved this way. */
        public int count(@NonNull Change change) {
            int n = 0;
            for (Change c : changes) {
                n += c == change ? 1 : 0;
            }
            return n;
        }

        /** Rows of the file that will not be used. */
        public int rejected() {
            int n = 0;
            for (TimetableCsv.Row row : rows) {
                n += row.check == TimetableCsv.Check.READY ? 0 : 1;
            }
            return n;
        }

        /** The faculty member with this address, or null. */
        @Nullable
        public FacultyMember teacher(@NonNull String email) {
            for (FacultyMember member : faculty) {
                if (member.getEmail().equals(email)) {
                    return member;
                }
            }
            return null;
        }

        @NonNull
        Builder edit() {
            final Builder b = new Builder();
            b.status = status;
            b.batch = batch;
            b.subjects = subjects;
            b.faculty = faculty;
            b.timetable = timetable;
            b.error = error;
            b.online = online;
            b.phase = phase;
            b.rows = rows;
            b.plan = plan;
            b.changes = changes;
            b.saved = saved;
            b.fileProblem = fileProblem;
            b.fileError = fileError;
            b.openPreview = openPreview;
            b.counts = counts;
            return b;
        }
    }

    static final class Builder {
        Status status = Status.LOADING;
        Batch batch;
        List<Course> subjects = new ArrayList<>();
        List<FacultyMember> faculty = new ArrayList<>();
        Timetable timetable;
        AppError error;
        boolean online;
        Phase phase = Phase.IDLE;
        List<TimetableCsv.Row> rows = new ArrayList<>();
        TimetablePlan plan;
        List<Change> changes = new ArrayList<>();
        int saved;
        RosterCsv.FileProblem fileProblem;
        AppError fileError;
        boolean openPreview;
        Map<String, CourseCount> counts = new HashMap<>();
    }

    private final AuthRepository auth;
    private final CourseRepository courses;
    private final BatchRepository batches;
    private final FacultyRepository facultyRepository;
    private final AttendanceRepository attendance;
    private final String batchId;
    private final MediatorLiveData<State> state = new MediatorLiveData<>(new State(new Builder()));

    public SubjectsViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courses,
            @NonNull BatchRepository batches,
            @NonNull FacultyRepository faculty,
            @NonNull AttendanceRepository attendance,
            @NonNull LiveData<Boolean> online,
            @NonNull String batchId) {
        this.attendance = attendance;
        this.auth = auth;
        this.courses = courses;
        this.batches = batches;
        this.facultyRepository = faculty;
        this.batchId = batchId;
        state.addSource(
                online,
                isOnline -> {
                    final Builder b = current().edit();
                    b.online = Boolean.TRUE.equals(isOnline);
                    publish(b);
                });
        refresh();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** The batch, then its faculty list, subjects and timetable for the current semester. */
    public void refresh() {
        final Builder loading = current().edit();
        if (loading.batch == null) {
            loading.status = Status.LOADING;
        }
        publish(loading);
        batches.loadBatch(
                batchId,
                batch -> {
                    if (!batch.isSuccess()) {
                        fail(batch.getError());
                        return;
                    }
                    facultyRepository.loadFaculty(faculty -> {
                        if (!faculty.isSuccess()) {
                            fail(faculty.getError());
                            return;
                        }
                        auth.loadUser(user ->
                                loadSemester(
                                        batch.getValue(),
                                        withSelf(
                                                faculty.getValue(),
                                                user.isSuccess() ? user.getValue() : null)));
                    });
                });
    }

    /**
     * The faculty list with the HOD in it: the HOD teaches too, and need not be on the list. The
     * HOD's initials come from their name, so the timetable can name them either way.
     */
    @NonNull
    static List<FacultyMember> withSelf(@NonNull List<FacultyMember> faculty, @Nullable User hod) {
        if (hod == null) {
            return faculty;
        }
        final String email = FacultyMember.normaliseEmail(hod.getEmail());
        for (FacultyMember member : faculty) {
            if (member.getEmail().equals(email)) {
                return faculty;
            }
        }
        final List<FacultyMember> all = new ArrayList<>();
        all.add(new FacultyMember(email, hod.getName(), Monogram.of(hod.getName())));
        all.addAll(faculty);
        return all;
    }

    private void loadSemester(@NonNull Batch batch, @NonNull List<FacultyMember> faculty) {
        courses.loadSubjects(
                batch.getId(),
                batch.getSemester(),
                subjects -> {
                    if (!subjects.isSuccess()) {
                        fail(subjects.getError());
                        return;
                    }
                    batches.loadTimetables(
                            Collections.singletonList(
                                    Timetable.idOf(batch.getId(), batch.getSemester())),
                            timetables -> {
                                final Builder b = current().edit();
                                b.status = Status.CONTENT;
                                b.error = null;
                                b.batch = batch;
                                b.faculty = faculty;
                                b.subjects = subjects.getValue();
                                // A timetable that cannot be read shows as not imported.
                                b.timetable =
                                        timetables.isSuccess() && !timetables.getValue().isEmpty()
                                                ? timetables.getValue().get(0)
                                                : null;
                                publish(b);
                                countSubjects();
                            });
                });
    }

    /** A chosen file: checked whole, then previewed. Null if it could not be read. */
    public void onFile(@Nullable String text) {
        final State s = current();
        final Batch batch = s.batch;
        if (batch == null || s.phase == Phase.SAVING) {
            return;
        }
        final Builder b = s.edit();
        b.fileProblem = null;
        b.fileError = null;
        if (text == null) {
            b.fileError = AppError.FILE_UNREADABLE;
            publish(b);
            return;
        }
        final List<TimetableCsv.Row> rows;
        try {
            rows = TimetableCsv.parse(text, batch.getLabGroups());
        } catch (RosterCsv.FileException unusable) {
            b.fileProblem = unusable.problem;
            publish(b);
            return;
        }
        final TimetablePlan plan =
                TimetablePlan.of(rows, batch.getId(), batch.getSemester(), s.faculty);
        final List<Change> changes = new ArrayList<>();
        for (TimetablePlan.Subject subject : plan.subjects) {
            changes.add(changeOf(subject, s.subjects, batch));
        }
        b.phase = Phase.PREVIEW;
        b.rows = rows;
        b.plan = plan;
        b.changes = changes;
        b.saved = 0;
        b.openPreview = true;
        publish(b);
    }

    public void onPreviewOpened() {
        final Builder b = current().edit();
        b.openPreview = false;
        publish(b);
    }

    /** Writes the plan's subjects one at a time, then the week's slots. */
    public void save() {
        final State s = current();
        if (s.phase != Phase.PREVIEW || s.plan == null || !s.online) {
            return;
        }
        final Builder b = s.edit();
        b.phase = Phase.SAVING;
        publish(b);
        final TimetablePlan plan = s.plan;
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                final Builder failed = current().edit();
                failed.phase = Phase.DONE;
                failed.changes = Collections.nCopies(plan.subjects.size(), Change.FAILED);
                publish(failed);
                return;
            }
            saveNext(user.getValue(), plan, 0);
        });
    }

    private void saveNext(@NonNull User hod, @NonNull TimetablePlan plan, int index) {
        if (index == plan.subjects.size()) {
            batches.saveTimetable(
                    hod,
                    plan.timetable,
                    saved -> {
                        final Builder b = current().edit();
                        b.phase = Phase.DONE;
                        if (saved.isSuccess()) {
                            b.timetable = saved.getValue();
                        } else {
                            b.fileError = saved.getError();
                        }
                        publish(b);
                        refreshSubjects();
                    });
            return;
        }
        final State s = current();
        final Change planned = s.changes.get(index);
        if (planned == Change.SAME || s.batch == null) {
            advance(index, Change.SAME, hod, plan);
            return;
        }
        courses.saveSubject(
                hod,
                subjectOf(plan.subjects.get(index), s.subjects, s.batch),
                outcome ->
                        advance(
                                index,
                                outcome.isSuccess() && outcome.getValue() != SaveOutcome.UNCHANGED
                                        ? planned
                                        : outcome.isSuccess() ? Change.SAME : Change.FAILED,
                                hod,
                                plan));
    }

    private void advance(int index, @NonNull Change result, @NonNull User hod, TimetablePlan plan) {
        final Builder b = current().edit();
        final List<Change> changes = new ArrayList<>(b.changes);
        changes.set(index, result);
        b.changes = changes;
        b.saved = index + 1;
        publish(b);
        saveNext(hod, plan, index + 1);
    }

    private void refreshSubjects() {
        final Batch batch = current().batch;
        if (batch == null) {
            return;
        }
        courses.loadSubjects(
                batch.getId(),
                batch.getSemester(),
                subjects -> {
                    if (subjects.isSuccess()) {
                        final Builder b = current().edit();
                        b.subjects = subjects.getValue();
                        publish(b);
                        countSubjects();
                    }
                });
    }

    /**
     * Counts each claimed subject's sessions, students and present marks. A subject that cannot be
     * counted simply shows no figure.
     */
    private void countSubjects() {
        for (Course subject : current().subjects) {
            if (subject.getTeacherId().isEmpty()) {
                continue;
            }
            attendance.countCourse(
                    subject.getId(),
                    count -> {
                        if (!count.isSuccess()) {
                            return;
                        }
                        final Builder b = current().edit();
                        final Map<String, CourseCount> counts = new HashMap<>(b.counts);
                        counts.put(subject.getId(), count.getValue());
                        b.counts = counts;
                        publish(b);
                    });
        }
    }

    /** Forgets the file; the preview screen closes. */
    public void reset() {
        final Builder b = current().edit();
        b.phase = Phase.IDLE;
        b.rows = new ArrayList<>();
        b.plan = null;
        b.changes = new ArrayList<>();
        b.saved = 0;
        b.openPreview = false;
        publish(b);
    }

    /**
     * Gives a subject to another teacher, or to nobody with "". Delivers the outcome; the list is
     * reloaded after a success.
     */
    public void assign(
            @NonNull Course subject,
            @NonNull String teacherEmail,
            @NonNull Callback<SaveOutcome> done) {
        if (!current().online) {
            done.onResult(Outcome.failure(AppError.OFFLINE));
            return;
        }
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                done.onResult(Outcome.failure(user.getError()));
                return;
            }
            courses.saveSubject(
                    user.getValue(),
                    subject.withTeacher(teacherEmail),
                    outcome -> {
                        done.onResult(outcome);
                        if (outcome.isSuccess()) {
                            refreshSubjects();
                        }
                    });
        });
    }

    /**
     * The course a planned subject is saved as. A teacher the file names replaces the one it has; a
     * name that matches nobody keeps the teacher the HOD chose by hand.
     */
    @NonNull
    static Course subjectOf(
            @NonNull TimetablePlan.Subject planned,
            @NonNull List<Course> existing,
            @NonNull Batch batch) {
        final Course was = find(planned.courseId, existing);
        final String teacher =
                planned.teacher != null
                        ? planned.teacher.getEmail()
                        : was != null ? was.getTeacherEmail() : "";
        return Course.subject(
                planned.courseId,
                "",
                teacher,
                planned.name,
                planned.code,
                was != null ? was.getThreshold() : Constants.DEFAULT_THRESHOLD,
                batch.getId(),
                batch.getSemester(),
                planned.labGroup);
    }

    @NonNull
    static Change changeOf(
            @NonNull TimetablePlan.Subject planned,
            @NonNull List<Course> existing,
            @NonNull Batch batch) {
        final Course was = find(planned.courseId, existing);
        if (was == null) {
            return Change.NEW;
        }
        final Course next = subjectOf(planned, existing, batch);
        return was.getName().equals(next.getName())
                        && was.getTeacherEmail().equals(next.getTeacherEmail())
                ? Change.SAME
                : Change.UPDATE;
    }

    @Nullable
    private static Course find(@NonNull String courseId, @NonNull List<Course> courses) {
        for (Course course : courses) {
            if (course.getId().equals(courseId)) {
                return course;
            }
        }
        return null;
    }

    private void fail(@Nullable AppError error) {
        final Builder b = current().edit();
        b.status = Status.ERROR;
        b.error = error != null ? error : AppError.UNKNOWN;
        publish(b);
    }

    private void publish(@NonNull Builder b) {
        state.setValue(new State(b));
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
