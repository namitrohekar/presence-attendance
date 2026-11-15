package com.presence.attendance.course.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** {@link CourseRepository} backed by Cloud Firestore. */
public final class FirestoreCourseRepository implements CourseRepository {

    private static final String TAG = "CourseRepository";
    private static final String COURSES = "courses";
    private static final String JOIN_CODES = "joinCodes";
    private static final String MEMBERSHIPS = "memberships";

    // 32^6 codes: a collision is rare, five in a row means something else is wrong.
    private static final int MAX_JOIN_CODE_ATTEMPTS = 5;

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
                    callback.onResult(Outcome.success(members));
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
