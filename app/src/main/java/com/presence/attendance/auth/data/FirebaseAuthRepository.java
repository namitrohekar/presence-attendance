package com.presence.attendance.auth.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Map;

/** {@link AuthRepository} backed by Firebase Authentication and the {@code users} collection. */
public final class FirebaseAuthRepository implements AuthRepository {

    private static final String USERS = "users";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    // The profile changes only at sign-in, so one read per process is enough.
    @Nullable private User cachedUser;

    public FirebaseAuthRepository(
            @NonNull FirebaseAuth auth, @NonNull FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    @Override
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
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
                        syncProfile(user, callback);
                    }
                });
    }

    @Override
    public void loadUser(@NonNull Callback<User> callback) {
        final FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            fail(callback, AppError.UNAUTHENTICATED);
            return;
        }
        if (cachedUser != null && cachedUser.getUid().equals(firebaseUser.getUid())) {
            callback.onResult(Outcome.success(cachedUser));
            return;
        }
        profile(firebaseUser.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, FirebaseErrors.toAppError(task.getException()));
                        return;
                    }
                    final User user = UserMapper.fromSnapshot(task.getResult());
                    if (user == null) {
                        syncProfile(firebaseUser, callback);
                    } else {
                        succeed(callback, user);
                    }
                });
    }

    @Override
    public void signOut() {
        cachedUser = null;
        auth.signOut();
    }

    /**
     * Writes name and email from the ID token into the profile. The rules compare the profile with
     * the token's claims, so the claims are read rather than the cached FirebaseUser fields.
     */
    private void syncProfile(@NonNull FirebaseUser firebaseUser, @NonNull Callback<User> callback) {
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
                        writeProfile(
                                firebaseUser.getUid(),
                                (String) name,
                                (String) email,
                                false,
                                callback);
                    } else {
                        rejectAccount(callback);
                    }
                });
    }

    /**
     * The app cannot read the teacher allowlist, so it cannot know the user's role. It proposes one
     * (the stored role, or STUDENT for a new profile) and lets the rules decide: a denial means the
     * other role is the right one, and a second denial means the account is not allowed at all.
     */
    private void writeProfile(
            @NonNull String uid,
            @NonNull String name,
            @NonNull String email,
            boolean otherRole,
            @NonNull Callback<User> callback) {
        final DocumentReference profile = profile(uid);
        firestore
                .runTransaction(transaction -> {
                    final DocumentSnapshot snapshot = transaction.get(profile);
                    final User stored = UserMapper.fromSnapshot(snapshot);
                    final Role current = stored != null ? stored.getRole() : Role.STUDENT;
                    final Role role = otherRole ? current.other() : current;
                    if (snapshot.exists()) {
                        transaction.update(
                                profile,
                                UserMapper.NAME,
                                name,
                                UserMapper.EMAIL,
                                email,
                                UserMapper.ROLE,
                                role.name());
                    } else {
                        transaction.set(profile, UserMapper.newDocument(name, email, role));
                    }
                    return role;
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        succeed(callback, new User(uid, name, email, task.getResult()));
                        return;
                    }
                    final AppError error = FirebaseErrors.toAppError(task.getException());
                    if (error != AppError.PERMISSION_DENIED) {
                        fail(callback, error);
                    } else if (!otherRole) {
                        writeProfile(uid, name, email, true, callback);
                    } else {
                        rejectAccount(callback);
                    }
                });
    }

    private void rejectAccount(@NonNull Callback<User> callback) {
        signOut();
        fail(callback, AppError.ACCOUNT_NOT_ALLOWED);
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
}
