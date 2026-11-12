package com.presence.attendance.auth.ui;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.presence.attendance.BuildConfig;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;

/** Asks Credential Manager for a Google ID token. The token is exchanged by the repository. */
public final class GoogleIdTokenRequester {

    private static final String TAG = "GoogleSignIn";

    private GoogleIdTokenRequester() {}

    /** Shows Google's account sheet. The callback gets the ID token or the reason it failed. */
    public static void request(@NonNull Activity activity, @NonNull Callback<String> callback) {
        final GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build();
        final GetCredentialRequest request =
                new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity)
                .getCredentialAsync(
                        activity,
                        request,
                        null,
                        ContextCompat.getMainExecutor(activity),
                        new CredentialManagerCallback<>() {
                            @Override
                            public void onResult(@NonNull GetCredentialResponse response) {
                                callback.onResult(idToken(response.getCredential()));
                            }

                            @Override
                            public void onError(@NonNull GetCredentialException error) {
                                callback.onResult(Outcome.failure(toAppError(error)));
                            }
                        });
    }

    /** Forgets the chosen account, so the next sign-in offers the account sheet again. */
    public static void clearState(@NonNull Context context) {
        CredentialManager.create(context)
                .clearCredentialStateAsync(
                        new ClearCredentialStateRequest(),
                        null,
                        ContextCompat.getMainExecutor(context),
                        new CredentialManagerCallback<>() {
                            @Override
                            public void onResult(Void result) {}

                            @Override
                            public void onError(@NonNull ClearCredentialException error) {
                                Log.w(TAG, "Could not clear the credential state", error);
                            }
                        });
    }

    @NonNull
    private static Outcome<String> idToken(@NonNull Credential credential) {
        final boolean isGoogleIdToken =
                credential instanceof CustomCredential
                        && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(
                                credential.getType());
        if (!isGoogleIdToken) {
            return Outcome.failure(AppError.SIGN_IN_FAILED);
        }
        try {
            return Outcome.success(
                    GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
        } catch (Exception malformed) {
            // createFrom is Kotlin: it throws a checked parsing exception it does not declare.
            Log.w(TAG, "Google returned a credential that could not be parsed", malformed);
            return Outcome.failure(AppError.SIGN_IN_FAILED);
        }
    }

    @NonNull
    private static AppError toAppError(@NonNull GetCredentialException error) {
        if (error instanceof GetCredentialCancellationException) {
            return AppError.SIGN_IN_CANCELLED;
        }
        if (error instanceof NoCredentialException) {
            return AppError.NO_GOOGLE_ACCOUNT;
        }
        Log.w(TAG, "Credential request failed", error);
        return AppError.SIGN_IN_FAILED;
    }
}
