package com.presence.attendance.course.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.course.domain.JoinCodes;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.course.domain.SubjectEnrolment;
import com.presence.attendance.roster.data.FirestoreRosterRepository;
import com.presence.attendance.roster.data.RosterMappers;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** {@link CourseRepository} backed by Cloud Firestore. */
public final class FirestoreCourseRepository implements CourseRepository {

    private static final String TAG = "CourseRepository";
    private static final String COURSES = "courses";
    private static final String JOIN_CODES = "joinCodes";
    private static final String MEMBERSHIPS = "memberships";
    private static final String BATCHES = "batches";
    private static final String BATCH_SEMESTER = "semester";

    // 32^6 codes: a collision is rare, five in a row means something else is wrong.
    private static final int MAX_JOIN_CODE_ATTEMPTS = 5;

    /** The most addresses one roster query may name. */
    private static final int ROSTER_CHUNK = 30;

    private final FirebaseFirestore firestore;
    private final Random random;

    public FirestoreCourseRepository(@NonNull FirebaseFirestore firestore, @NonNull Random random) {
        this.firestore = firestore;
        this.random = random;
    }

    @Override
    public void loadTeacherCourses(
            @NonNull String teacherId, @NonNull Callback<List<Course>> callback) {
        // The rules only allow listing courses with this constraint.
        courses()
                .whereEqualTo(CourseMappers.TEACHER_ID, teacherId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        deliverCourses(task.getResult().getDocuments(), callback);
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    @Override
    public void loadStudentCourses(
            @NonNull String studentId, @NonNull Callback<List<Course>> callback) {
        // A student cannot list courses, only read the ones they are a member of.
        final Task<List<DocumentSnapshot>> courseDocuments =
                memberships()
                        .whereEqualTo(CourseMappers.STUDENT_ID, studentId)
                        .get()
                        .onSuccessTask(memberships -> {
                            final List<Task<DocumentSnapshot>> reads = new ArrayList<>();
                            for (DocumentSnapshot membership : memberships.getDocuments()) {
                                final String courseId =
                                        Snapshots.string(membership, CourseMappers.COURSE_ID);
                                if (courseId != null) {
                                    reads.add(courses().document(courseId).get());
                                }
                            }
                            return Tasks.whenAllSuccess(reads);
                        });
        courseDocuments.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                deliverCourses(task.getResult(), callback);
            } else {
                fail(callback, task.getException());
            }
        });
    }

    @Override
    public void createCourse(
            @NonNull User teacher,
            @NonNull String name,
            @NonNull String code,
            @NonNull Callback<Course> callback) {
        createCourse(teacher, name, code, 1, callback);
    }

    private void createCourse(
            @NonNull User teacher,
            @NonNull String name,
            @NonNull String code,
            int attempt,
            @NonNull Callback<Course> callback) {
        final DocumentReference courseRef = courses().document();
        final Course course =
                new Course(
                        courseRef.getId(),
                        teacher.getUid(),
                        name,
                        code,
                        Constants.DEFAULT_THRESHOLD,
                        JoinCodes.generate(random));
        final DocumentReference joinCodeRef = joinCodes().document(course.getJoinCode());
        firestore
                .runTransaction(transaction -> {
                    // The rules also reject a taken code; reading first lets the app
                    // pick another one instead of failing.
                    if (transaction.get(joinCodeRef).exists()) {
                        return false;
                    }
                    transaction.set(courseRef, CourseMappers.newCourse(course));
                    transaction.set(joinCodeRef, CourseMappers.newJoinCode(course, teacher));
                    return true;
                })
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                    } else if (task.getResult()) {
                        callback.onResult(Outcome.success(course));
                    } else if (attempt < MAX_JOIN_CODE_ATTEMPTS) {
                        createCourse(teacher, name, code, attempt + 1, callback);
                    } else {
                        callback.onResult(Outcome.failure(AppError.UNKNOWN));
                    }
                });
    }

    @Override
    public void findJoinCode(@NonNull String joinCode, @NonNull Callback<JoinCodeInfo> callback) {
        // Server only: a cached miss must not hide a code that was created a minute ago.
        joinCodes()
                .document(joinCode)
                .get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final JoinCodeInfo info = CourseMappers.joinCode(task.getResult());
                    callback.onResult(
                            info != null
                                    ? Outcome.success(info)
                                    : Outcome.failure(AppError.JOIN_CODE_NOT_FOUND));
                });
    }

    @Override
    public void joinCourse(
            @NonNull User student,
            @NonNull JoinCodeInfo joinCode,
            @NonNull Callback<JoinCodeInfo> callback) {
        final DocumentReference membership =
                memberships().document(joinCode.getCourseId() + "_" + student.getUid());
        firestore
                .runTransaction(transaction -> {
                    // A second write would be an update, which the rules deny; reading
                    // first turns that into a precise message.
                    if (transaction.get(membership).exists()) {
                        return false;
                    }
                    transaction.set(membership, CourseMappers.newMembership(student, joinCode));
                    return true;
                })
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                    } else if (task.getResult()) {
                        callback.onResult(Outcome.success(joinCode));
                    } else {
                        callback.onResult(Outcome.failure(AppError.ALREADY_JOINED));
                    }
                });
    }

    @Override
    public void loadMembers(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Member>> callback) {
        // The rules only allow a teacher to list memberships that carry their own id.
        memberships()
                .whereEqualTo(CourseMappers.TEACHER_ID, teacherId)
                .whereEqualTo(CourseMappers.COURSE_ID, courseId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<Member> members = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final Member member = CourseMappers.member(document);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                    members.sort(Comparator.comparing(Member::getRollNo));
                    withRosterDetails(members, callback);
                });
    }

    /**
     * Gives each member the name and lab group the roster has for them now, so a corrected name
     * shows everywhere at once. The roster is read in chunks of {@link #ROSTER_CHUNK} addresses,
     * the most one query may name. A chunk that cannot be read leaves those members as they joined:
     * the lists still work, with the names students joined with.
     */
    private void withRosterDetails(
            @NonNull List<Member> members, @NonNull Callback<List<Member>> callback) {
        if (members.isEmpty()) {
            callback.onResult(Outcome.success(members));
            return;
        }
        final List<String> emails = new ArrayList<>();
        for (Member member : members) {
            final String email = StudentIdentity.normaliseEmail(member.getEmail());
            if (!emails.contains(email)) {
                emails.add(email);
            }
        }
        final List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int i = 0; i < emails.size(); i += ROSTER_CHUNK) {
            chunks.add(
                    firestore
                            .collection(FirestoreRosterRepository.REGISTRY)
                            .whereIn(
                                    FieldPath.documentId(),
                                    emails.subList(i, Math.min(i + ROSTER_CHUNK, emails.size())))
                            .get());
        }
        Tasks.whenAllComplete(chunks)
                .addOnCompleteListener(done -> {
                    final Map<String, RosterEntry> byEmail = new HashMap<>();
                    for (Task<QuerySnapshot> chunk : chunks) {
                        if (!chunk.isSuccessful()) {
                            Log.w(TAG, "Roster details unavailable for some members", null);
                            continue;
                        }
                        for (DocumentSnapshot document : chunk.getResult().getDocuments()) {
                            final RosterEntry entry = RosterMappers.entry(document);
                            if (entry != null) {
                                byEmail.put(entry.getEmail(), entry);
                            }
                        }
                    }
                    final List<Member> merged = new ArrayList<>(members.size());
                    for (Member member : members) {
                        final RosterEntry entry =
                                byEmail.get(StudentIdentity.normaliseEmail(member.getEmail()));
                        merged.add(
                                entry == null
                                        ? member
                                        : member.withRoster(entry.getName(), entry.getLabGroup()));
                    }
                    callback.onResult(Outcome.success(merged));
                });
    }

    private static void deliverCourses(
            @NonNull List<DocumentSnapshot> documents, @NonNull Callback<List<Course>> callback) {
        final List<Course> courses = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            final Course course = CourseMappers.course(document);
            if (course == null) {
                Log.w(TAG, "Skipping a course document that is missing or malformed", null);
            } else {
                courses.add(course);
            }
        }
        courses.sort(Comparator.comparing(Course::getName, String.CASE_INSENSITIVE_ORDER));
        callback.onResult(Outcome.success(courses));
    }

    @Override
    public void syncSubjects(@NonNull User user, @NonNull Callback<Integer> callback) {
        final Task<Integer> sync =
                user.getRole() == Role.FACULTY ? claimSubjects(user) : joinSubjects(user);
        sync.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(Outcome.success(task.getResult()));
            } else {
                fail(callback, task.getException());
            }
        });
    }

    /** The subjects assigned to the teacher's address and not yet claimed become theirs. */
    @NonNull
    private Task<Integer> claimSubjects(@NonNull User teacher) {
        return courses()
                .whereEqualTo(
                        CourseMappers.TEACHER_EMAIL, teacher.getEmail().toLowerCase(Locale.ROOT))
                .whereEqualTo(CourseMappers.TEACHER_ID, "")
                .get()
                .onSuccessTask(pending -> {
                    final List<Task<Void>> claims = new ArrayList<>();
                    for (DocumentSnapshot subject : pending.getDocuments()) {
                        claims.add(
                                subject.getReference()
                                        .update(CourseMappers.TEACHER_ID, teacher.getUid()));
                    }
                    return Tasks.whenAll(claims)
                            .onSuccessTask(done -> Tasks.forResult(claims.size()));
                });
    }

    /**
     * The student's batch, its current semester and their lab group decide which subjects they are
     * in. A student of a cohort that is not a batch (from before batches) has none.
     */
    @NonNull
    private Task<Integer> joinSubjects(@NonNull User student) {
        final String cohort = student.getCohort();
        if (cohort == null) {
            return Tasks.forResult(0);
        }
        final Task<DocumentSnapshot> entry =
                firestore
                        .collection(FirestoreRosterRepository.REGISTRY)
                        .document(StudentIdentity.normaliseEmail(student.getEmail()))
                        .get();
        final Task<DocumentSnapshot> batch = firestore.collection(BATCHES).document(cohort).get();
        final Task<QuerySnapshot> mine =
                memberships().whereEqualTo(CourseMappers.STUDENT_ID, student.getUid()).get();
        return Tasks.whenAllSuccess(entry, batch, mine)
                .onSuccessTask(read -> {
                    final Long semester = Snapshots.integer(batch.getResult(), BATCH_SEMESTER);
                    if (semester == null) {
                        return Tasks.forResult(0);
                    }
                    final Long group =
                            Snapshots.integer(entry.getResult(), RosterMappers.LAB_GROUP);
                    final Map<String, String> teacherByCourse = new HashMap<>();
                    for (DocumentSnapshot membership : mine.getResult().getDocuments()) {
                        final String courseId =
                                Snapshots.string(membership, CourseMappers.COURSE_ID);
                        final String teacherId =
                                Snapshots.string(membership, CourseMappers.TEACHER_ID);
                        if (courseId != null && teacherId != null) {
                            teacherByCourse.put(courseId, teacherId);
                        }
                    }
                    return subjectsOf(cohort, semester.intValue())
                            .onSuccessTask(subjects ->
                                    enrol(
                                            student,
                                            SubjectEnrolment.plan(
                                                    subjects,
                                                    teacherByCourse,
                                                    group != null ? group.intValue() : 0)));
                });
    }

    @NonNull
    private Task<Integer> enrol(@NonNull User student, @NonNull SubjectEnrolment plan) {
        final List<Task<Void>> writes = new ArrayList<>();
        for (Course subject : plan.toJoin) {
            writes.add(
                    memberships()
                            .document(subject.getId() + "_" + student.getUid())
                            .set(CourseMappers.newSubjectMembership(student, subject)));
        }
        for (Course subject : plan.toMove) {
            writes.add(
                    memberships()
                            .document(subject.getId() + "_" + student.getUid())
                            .update(CourseMappers.TEACHER_ID, subject.getTeacherId()));
        }
        return Tasks.whenAll(writes).onSuccessTask(done -> Tasks.forResult(writes.size()));
    }

    @NonNull
    private Task<List<Course>> subjectsOf(@NonNull String batchId, int semester) {
        return courses()
                .whereEqualTo(CourseMappers.BATCH_ID, batchId)
                .whereEqualTo(CourseMappers.SEMESTER, semester)
                .get()
                .onSuccessTask(found -> {
                    final List<Course> subjects = new ArrayList<>();
                    for (DocumentSnapshot document : found.getDocuments()) {
                        final Course course = CourseMappers.course(document);
                        if (course != null && course.isSubject()) {
                            subjects.add(course);
                        }
                    }
                    subjects.sort(
                            Comparator.comparing(Course::getName, String.CASE_INSENSITIVE_ORDER)
                                    .thenComparingInt(Course::getLabGroup));
                    return Tasks.forResult(subjects);
                });
    }

    @Override
    public void loadSubjects(
            @NonNull String batchId, int semester, @NonNull Callback<List<Course>> callback) {
        subjectsOf(batchId, semester)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    @Override
    public void saveSubject(
            @NonNull User hod, @NonNull Course subject, @NonNull Callback<SaveOutcome> callback) {
        final DocumentReference ref = courses().document(subject.getId());
        firestore
                .runTransaction(transaction -> {
                    final DocumentSnapshot current = transaction.get(ref);
                    final Course was = current.exists() ? CourseMappers.course(current) : null;
                    if (was == null) {
                        transaction.set(ref, CourseMappers.newSubject(subject, hod.getUid()));
                        return SaveOutcome.CREATED;
                    }
                    final boolean sameTeacher =
                            was.getTeacherEmail().equals(subject.getTeacherEmail());
                    if (sameTeacher && was.getName().equals(subject.getName())) {
                        return SaveOutcome.UNCHANGED;
                    }
                    final Map<String, Object> change = new HashMap<>();
                    change.put(CourseMappers.NAME, subject.getName());
                    if (!sameTeacher) {
                        // A new teacher claims it with their own account.
                        change.put(CourseMappers.TEACHER_EMAIL, subject.getTeacherEmail());
                        change.put(CourseMappers.TEACHER_ID, "");
                    }
                    transaction.update(ref, change);
                    return SaveOutcome.UPDATED;
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    private static <T> void fail(@NonNull Callback<T> callback, @Nullable Exception error) {
        callback.onResult(Outcome.failure(FirebaseErrors.toAppError(error)));
    }

    private CollectionReference courses() {
        return firestore.collection(COURSES);
    }

    private CollectionReference joinCodes() {
        return firestore.collection(JOIN_CODES);
    }

    private CollectionReference memberships() {
        return firestore.collection(MEMBERSHIPS);
    }
}
