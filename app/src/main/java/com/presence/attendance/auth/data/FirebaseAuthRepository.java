package com.presence.attendance.auth.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.core.data.Transactions;
import com.presence.attendance.roster.data.FirestoreRosterRepository;
import com.presence.attendance.roster.data.RosterMappers;
import com.presence.attendance.roster.domain.AlertRepository;
import com.presence.attendance.roster.domain.IdentityAlert;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.Locale;
import java.util.Map;

/**
 * {@link AuthRepository} backed by Firebase Authentication, the {@code users} collection and the
 * student roster.
 *
 * <p>The app cannot read the faculty allowlist, so it cannot know a new account's role. It asks the
 * security rules instead: it proposes a faculty profile, and a denial means the account is not
 * faculty. A student address then has to register; any other address is turned away.
 */
public final class FirebaseAuthRepository implements AuthRepository {

    private static final String USERS = "users";
    private static final String HODS = "hods";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";

    /** Receives the name and email from the sign-in token. */
    private interface Claims {
        void accept(@NonNull String name, @NonNull String email);
    }

    /** Outcome of the registration transaction: the roster entry it matched, or why not. */
    private static final class Attempt {
        @Nullable final RosterEntry entry;
        @Nullable final AppError error;

        Attempt(@Nullable RosterEntry entry, @Nullable AppError error) {
            this.entry = entry;
            this.error = error;
        }
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AlertRepository alerts;

    // The profile changes only at sign-in and registration, so one resolve per process is enough.
    @Nullable private User cachedUser;

    public FirebaseAuthRepository(
            @NonNull FirebaseAuth auth,
            @NonNull FirebaseFirestore firestore,
            @NonNull AlertRepository alerts) {
        this.auth = auth;
        this.firestore = firestore;
        this.alerts = alerts;
    }

    @Override
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    @Nullable
    @Override
    public String signedInEmail() {
        final FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    @Override
    public void signInWithGoogle(@NonNull String googleIdToken, @NonNull Callback<User> callback) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
                .addOnCompleteListener(task -> {
                    final FirebaseUser user =
                            task.isSuccessful() ? task.getResult().getUser() : null;
                    if (user == null) {
                        fail(callback, FirebaseErrors.toAppError(task.getException()));
                    } else {
                        resolve(user, callback);
                    }
                });
    }

    @Override
    public void loadUser(@NonNull Callback<User> callback) {
        final FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            fail(callback, AppError.UNAUTHENTICATED);
        } else if (cachedUser != null && cachedUser.getUid().equals(firebaseUser.getUid())) {
            callback.onResult(Outcome.success(cachedUser));
        } else {
            resolve(firebaseUser, callback);
        }
    }

    @Override
    public void signOut() {
        cachedUser = null;
        auth.signOut();
    }

    // ---- Resolving who the signed-in account is ---------------------------------------------

    private void resolve(@NonNull FirebaseUser firebaseUser, @NonNull Callback<User> callback) {
        final String uid = firebaseUser.getUid();
        withClaims(
                firebaseUser,
                callback,
                (name, email) ->
                        profile(uid)
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        onProfile(uid, name, email, task.getResult(), callback);
                                    } else {
                                        fail(
                                                callback,
                                                FirebaseErrors.toAppError(task.getException()));
                                    }
                                }));
    }

    private void onProfile(
            @NonNull String uid,
            @NonNull String name,
            @NonNull String email,
            @NonNull DocumentSnapshot snapshot,
            @NonNull Callback<User> callback) {
        final User stored = UserMapper.fromSnapshot(snapshot);
        if (stored == null) {
            // No profile yet, or one from before the roster existed.
            proposeFaculty(uid, name, email, callback);
        } else if (stored.getRole() == Role.FACULTY) {
            keepFacultyProfileFresh(stored, name, email, callback);
        } else {
            confirmStillRegistered(stored, name, email, callback);
        }
    }

    private void proposeFaculty(
            @NonNull String uid,
            @NonNull String name,
            @NonNull String email,
            @NonNull Callback<User> callback) {
        final DocumentReference profile = profile(uid);
        final Task<Void> write =
                firestore.runTransaction(transaction -> {
                    if (transaction.get(profile).exists()) {
                        transaction.update(profile, UserMapper.facultyFields(name, email));
                    } else {
                        transaction.set(profile, UserMapper.newFacultyDocument(name, email));
                    }
                    return null;
                });
        write.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                succeedAsFaculty(callback, new User(uid, name, email, Role.FACULTY));
                return;
            }
            final AppError error = FirebaseErrors.toAppError(task.getException());
            if (error == AppError.PERMISSION_DENIED) {
                notFaculty(uid, email, callback);
            } else {
                fail(callback, error);
            }
        });
    }

    /** Rewrites the profile only when the token's name or email has changed. */
    private void keepFacultyProfileFresh(
            @NonNull User stored,
            @NonNull String name,
            @NonNull String email,
            @NonNull Callback<User> callback) {
        if (stored.getName().equals(name) && stored.getEmail().equals(email)) {
            succeedAsFaculty(callback, stored);
        } else {
            proposeFaculty(stored.getUid(), name, email, callback);
        }
    }

    /**
     * A stored student profile is only as good as the roster entry behind it: faculty may have
     * corrected or removed the entry since, which releases the registration.
     */
    private void confirmStillRegistered(
            @NonNull User stored,
            @NonNull String name,
            @NonNull String email,
            @NonNull Callback<User> callback) {
        rosterEntry(email)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, FirebaseErrors.toAppError(task.getException()));
                        return;
                    }
                    final RosterEntry entry = RosterMappers.entry(task.getResult());
                    final boolean registered =
                            entry != null
                                    && stored.getUid().equals(entry.getRegisteredUid())
                                    && entry.getRollNo().equals(stored.getRollNo())
                                    && entry.getCohort().equals(stored.getCohort());
                    if (!registered) {
                        fail(callback, AppError.REGISTRATION_REQUIRED);
                    } else if (stored.getName().equals(name) && stored.getEmail().equals(email)) {
                        succeed(callback, stored);
                    } else {
                        refreshStudentProfile(stored, name, email, callback);
                    }
                });
    }

    private void refreshStudentProfile(
            @NonNull User stored,
            @NonNull String name,
            @NonNull String email,
            @NonNull Callback<User> callback) {
        final String rollNo = stored.getRollNo();
        final String cohort = stored.getCohort();
        if (rollNo == null || cohort == null) {
            fail(callback, AppError.REGISTRATION_REQUIRED);
            return;
        }
        final DocumentReference profile = profile(stored.getUid());
        final Map<String, Object> fields = UserMapper.studentFields(name, email, rollNo, cohort);
        final Task<Void> write =
                firestore.runTransaction(transaction -> {
                    transaction.update(profile, fields);
                    return null;
                });
        write.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                succeed(
                        callback,
                        new User(stored.getUid(), name, email, Role.STUDENT, rollNo, cohort));
            } else {
                fail(callback, FirebaseErrors.toAppError(task.getException()));
            }
        });
    }

    /** The rules refused a faculty profile: a student address must register, others must leave. */
    private void notFaculty(
            @NonNull String uid, @NonNull String email, @NonNull Callback<User> callback) {
        if (StudentIdentity.hasStudentDomain(email)) {
            fail(callback, AppError.REGISTRATION_REQUIRED);
            return;
        }
        // Reported before signing out, while the write can still be made as this account.
        alerts.report(
                uid,
                StudentIdentity.normaliseEmail(email),
                IdentityAlert.Type.NON_VJTI_EMAIL,
                null,
                IdentityAlert.AUDIENCE_ALL,
                () -> {
                    signOut();
                    fail(callback, AppError.NOT_VJTI_ACCOUNT);
                });
    }

    // ---- Registration -----------------------------------------------------------------------

    @Override
    public void register(@NonNull String enteredRollNo, @NonNull Callback<User> callback) {
        final FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            fail(callback, AppError.UNAUTHENTICATED);
            return;
        }
        final String uid = firebaseUser.getUid();
        withClaims(
                firebaseUser,
                callback,
                (name, email) ->
                        Transactions.runDecidingAgainIfDenied(
                                        firestore,
                                        transaction -> {
                                            final DocumentReference entryRef = rosterEntry(email);
                                            final DocumentReference profile = profile(uid);
                                            final RosterEntry entry =
                                                    RosterMappers.entry(transaction.get(entryRef));
                                            if (entry == null) {
                                                return new Attempt(null, AppError.NOT_ON_ROSTER);
                                            }
                                            final String holder = entry.getRegisteredUid();
                                            if (holder != null && !holder.equals(uid)) {
                                                return new Attempt(entry, AppError.DUPLICATE_CLAIM);
                                            }
                                            if (!entry.getRollNo().equals(enteredRollNo)) {
                                                return new Attempt(entry, AppError.ROLL_MISMATCH);
                                            }
                                            final boolean profileExists =
                                                    transaction.get(profile).exists();
                                            if (holder == null) {
                                                transaction.update(
                                                        entryRef, RosterMappers.claim(uid));
                                            }
                                            // The profile takes the roster's values, not what was
                                            // typed.
                                            if (profileExists) {
                                                transaction.update(
                                                        profile,
                                                        UserMapper.studentFields(
                                                                name,
                                                                email,
                                                                entry.getRollNo(),
                                                                entry.getCohort()));
                                            } else {
                                                transaction.set(
                                                        profile,
                                                        UserMapper.newStudentDocument(
                                                                name,
                                                                email,
                                                                entry.getRollNo(),
                                                                entry.getCohort()));
                                            }
                                            return new Attempt(entry, null);
                                        })
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        onAttempt(
                                                uid,
                                                name,
                                                email,
                                                enteredRollNo,
                                                task.getResult(),
                                                callback);
                                    } else {
                                        fail(
                                                callback,
                                                FirebaseErrors.toAppError(task.getException()));
                                    }
                                }));
    }

    private void onAttempt(
            @NonNull String uid,
            @NonNull String name,
            @NonNull String email,
            @NonNull String enteredRollNo,
            @NonNull Attempt attempt,
            @NonNull Callback<User> callback) {
        final RosterEntry entry = attempt.entry;
        if (attempt.error == null && entry != null) {
            succeed(
                    callback,
                    new User(uid, name, email, Role.STUDENT, entry.getRollNo(), entry.getCohort()));
            return;
        }
        final AppError error = attempt.error != null ? attempt.error : AppError.UNKNOWN;
        final IdentityAlert.Type type =
                switch (error) {
                    case NOT_ON_ROSTER -> IdentityAlert.Type.NOT_ON_REGISTER;
                    case DUPLICATE_CLAIM -> IdentityAlert.Type.DUPLICATE_CLAIM;
                    default -> IdentityAlert.Type.ROLL_MISMATCH;
                };
        // Not waited for: the student already has their answer.
        alerts.report(
                uid,
                StudentIdentity.normaliseEmail(email),
                type,
                enteredRollNo,
                entry != null ? entry.getAddedBy() : IdentityAlert.AUDIENCE_ALL,
                () -> {});
        fail(callback, error);
    }

    // ---- Helpers ------------------------------------------------------------------------------

    /**
     * Reads name and email from the ID token. The rules compare documents with the token's claims,
     * so the claims are used rather than the cached FirebaseUser fields.
     */
    private void withClaims(
            @NonNull FirebaseUser firebaseUser,
            @NonNull Callback<User> callback,
            @NonNull Claims receiver) {
        firebaseUser
                .getIdToken(false)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, FirebaseErrors.toAppError(task.getException()));
                        return;
                    }
                    final Map<String, Object> claims = task.getResult().getClaims();
                    final Object name = claims.get(CLAIM_NAME);
                    final Object email = claims.get(CLAIM_EMAIL);
                    if (name instanceof String && email instanceof String) {
                        receiver.accept((String) name, (String) email);
                    } else {
                        // No usable identity in the token: nothing can be verified.
                        signOut();
                        fail(callback, AppError.NOT_VJTI_ACCOUNT);
                    }
                });
    }

    /**
     * Faculty learn whether they are the HOD by reading their own entry in {@code hods}, which the
     * rules allow for nobody else's. It only decides which screens show: every HOD write is checked
     * by the rules again. If it cannot be read, they carry on as faculty.
     */
    private void succeedAsFaculty(@NonNull Callback<User> callback, @NonNull User faculty) {
        firestore
                .collection(HODS)
                .document(faculty.getEmail().toLowerCase(Locale.ROOT))
                .get()
                .addOnCompleteListener(task ->
                        succeed(
                                callback,
                                task.isSuccessful() && task.getResult().exists()
                                        ? faculty.asHod()
                                        : faculty));
    }

    private void succeed(@NonNull Callback<User> callback, @NonNull User user) {
        cachedUser = user;
        callback.onResult(Outcome.success(user));
    }

    private static void fail(@NonNull Callback<User> callback, @NonNull AppError error) {
        callback.onResult(Outcome.failure(error));
    }

    private DocumentReference profile(@NonNull String uid) {
        return firestore.collection(USERS).document(uid);
    }

    private DocumentReference rosterEntry(@NonNull String email) {
        return firestore
                .collection(FirestoreRosterRepository.REGISTRY)
                .document(StudentIdentity.normaliseEmail(email));
    }
}
