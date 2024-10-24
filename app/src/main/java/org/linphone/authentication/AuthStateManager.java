/*
 * Copyright 2017 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.linphone.authentication;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.linphone.utils.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.EndSessionRequest;
import net.openid.appauth.RegistrationResponse;
import net.openid.appauth.TokenResponse;
import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.linphone.activities.main.MainActivity;
import org.linphone.activities.main.LoginActivity;
import org.linphone.models.AuthenticatedUser;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

/**
 * An example persistence mechanism for an {@link AuthState} instance.
 * This stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
public class AuthStateManager {
    public static final String AUTH_KEY = "auth";
    public static final String LOGOUT_VALUE = "logout";
    private static final String STORE_NAME = "AuthState";
    private static final String KEY_STATE = "state";

    private static final AtomicReference<WeakReference<AuthStateManager>> INSTANCE_REF =
            new AtomicReference<>(new WeakReference<>(null));

    private final SharedPreferences mPrefs;
    private final ReentrantLock mPrefsLock;
    private final AtomicReference<AuthState> mCurrentAuthState;

    private final BehaviorSubject<AuthenticatedUser> userSubject = BehaviorSubject.createDefault(
        new AuthenticatedUser(AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER, null, null, null, null, null )
    );
    public final Observable<AuthenticatedUser> user = userSubject.map(x -> x);

    @AnyThread
    public static AuthStateManager getInstance(@NonNull Context context) {
        AuthStateManager manager = INSTANCE_REF.get().get();
        if (manager == null) {
            manager = new AuthStateManager(context.getApplicationContext());
            INSTANCE_REF.set(new WeakReference<>(manager));
        }

        return manager;
    }

    private AuthStateManager(Context context) {
        Log.Log.i("CREATE AuthStateManager");
        mPrefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        mPrefsLock = new ReentrantLock();
        mCurrentAuthState = new AtomicReference<>();
    }

    @AnyThread
    @NonNull
    public AuthState getCurrent() {
        AuthState state = mCurrentAuthState.get();
        if (state != null) return state;

        state = readState();
        if (mCurrentAuthState.compareAndSet(null, state)) {
            updateObservable(state, "getCurrent");
            return state;
        } else {
            return mCurrentAuthState.get();
        }
    }

    @AnyThread
    @NonNull
    public AuthState replace(@NonNull AuthState state, String caller) {
        Log.Log.d("AuthStateManager.replace: " + caller);
        writeState(state);
        mCurrentAuthState.set(state);
        return state;
    }


    @AnyThread
    @NonNull
    public AuthState updateAfterAuthorization(
            @Nullable AuthorizationResponse response,
            @Nullable AuthorizationException ex) {
        Log.Log.i(String.format("updateAfterAuthorization::Response(%s)::Ex(%s)", response, ex));
        AuthState current = getCurrent();

        if (response != null || ex != null) {
            current.update(response, ex);
        }

        if (response != null) {
            if (response.accessToken != null) Log.Log.d("updateAfterAuthorization::access token: " + response.accessToken);
            if (response.authorizationCode!= null) Log.Log.d("updateAfterAuthorization::authorization code: " + response.authorizationCode);
            Log.Log.d("updateAfterAuthorization::response: " + response.jsonSerialize());
        }

        return replace(current, "updateAfterAuthorization");
    }

    @AnyThread
    @NonNull
    public AuthState updateAfterTokenResponse(
            @Nullable TokenResponse response,
            @Nullable AuthorizationException ex) {

        JSONObject json = null;
        if (response != null) json = response.jsonSerialize();
        Log.Log.i(String.format("updateAfterTokenResponse::Response(%s)::Ex(%s)", json, ex));

        AuthState current = getCurrent();

        current.update(response, ex);

        return replace(current, "updateAfterTokenResponse");
    }

    @AnyThread
    @NonNull
    public AuthState updateAfterRegistration(
            RegistrationResponse response,
            AuthorizationException ex) {
        Log.Log.i(String.format("updateAfterRegistration::Response(%s)::Ex(%s)", response, ex));

        AuthState current = getCurrent();

        current.update(response);

        return replace(current, "updateAfterRegistration");
    }

    @AnyThread
    @NonNull
    private AuthState readState() {
        mPrefsLock.lock();
        try {
            String currentState = mPrefs.getString(KEY_STATE, null);
            if (currentState == null) {
                return new AuthState();
            }

            try {
                return AuthState.jsonDeserialize(currentState);
            } catch (JSONException ex) {
                Log.Log.w("Failed to deserialize stored auth state - discarding");
                return new AuthState();
            }
        } finally {
            mPrefsLock.unlock();
        }
    }

    @AnyThread
    private void writeState(@Nullable AuthState state) {
        mPrefsLock.lock();
        try {
            SharedPreferences.Editor editor = mPrefs.edit();
            if (state == null) {
                editor.remove(KEY_STATE);
            } else {
                editor.putString(KEY_STATE, state.jsonSerializeString());
            }

            if (!editor.commit()) {
                throw new IllegalStateException("Failed to write state to shared prefs");
            }
        } finally {
            updateObservable(state, "writeState");
            mPrefsLock.unlock();
        }
    }

    public void logout(Context context) {
        final var current = getCurrent();
        final var authService = new AuthorizationService(context);
        final var config = AuthConfiguration.getInstance(context);
        final var authConfig = current.getAuthorizationServiceConfiguration();
        if (authConfig == null) {
            // TODO: handle this
            return;
        }
        Log.Log.i("AuthStateManager.logout");

        replace(new AuthState(), "logout");

        EndSessionRequest endSessionRequest =
                new EndSessionRequest.Builder(authConfig)
                        .setIdTokenHint(current.getIdToken())
                        .setPostLogoutRedirectUri(config.getEndSessionRedirectUri())
                        .build();

        var endSessionIntent = new Intent(context, LoginActivity.class);
        endSessionIntent.putExtra(AUTH_KEY, LOGOUT_VALUE);
        endSessionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);

        var logoutIntent = PendingIntent.getActivity(context, 0, endSessionIntent, PendingIntent.FLAG_IMMUTABLE);

        authService.performEndSessionRequest(
                endSessionRequest,
                logoutIntent,
                PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
    }

    private void updateObservable(@Nullable AuthState state, String caller) {
        Log.Log.d("AuthStateManager.updateObservable: " + caller);

        var accessToken = state == null ? AuthenticatedUser.UNINTIALIZED_ACCESS_TOKEN : state.getAccessToken();
        Log.Log.d("AuthStateManager.updateObservable:Access token: " + accessToken);

        AuthenticatedUser user = AuthenticatedUser.Companion.fromToken(accessToken);
        Log.Log.d("AuthStateManager.updateObservable:User:ID(" + user.getId() + ")::Name(" + user.getName() + ")");

        userSubject.onNext(user);
    }

    public AuthenticatedUser getUser() {
        return userSubject.getValue();
    }
}
