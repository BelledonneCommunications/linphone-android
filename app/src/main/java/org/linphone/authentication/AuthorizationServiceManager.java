package org.linphone.authentication;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.browser.AnyBrowserMatcher;
import net.openid.appauth.connectivity.ConnectionBuilder;

import org.linphone.utils.Log;

public class AuthorizationServiceManager {

    private static final AtomicReference<WeakReference<AuthorizationServiceManager>> INSTANCE_REF =
            new AtomicReference<>(new WeakReference<>(null));

    private static final String TAG = "AuthorizationServiceManager";

    private final Context mContext;
    private final AuthConfiguration mConfiguration;

    //FIXME - this needs cleanup on destroy
    private AuthorizationService AuthorizationServiceInstance;

    @AnyThread
    public static AuthorizationServiceManager getInstance(@NonNull Context context) {
        AuthorizationServiceManager manager = INSTANCE_REF.get().get();
        if (manager == null) {
            manager = new AuthorizationServiceManager(context.getApplicationContext());
            INSTANCE_REF.set(new WeakReference<>(manager));
        }

        return manager;
    }

    private AuthorizationServiceManager(Context context) {
        mContext = context;
        mConfiguration = AuthConfiguration.getInstance(context);
    }

    public AuthorizationService getAuthorizationServiceInstance() {
        if (AuthorizationServiceInstance == null) {
            Log.Log.i("Creating authorization service");
            try {
                ConnectionBuilder connectionBuilder = null;

                if (mConfiguration != null) {
                    mConfiguration.readConfiguration();
                    connectionBuilder = mConfiguration.getConnectionBuilder();
                }

                if (connectionBuilder != null)
                {
                    var builder = new AppAuthConfiguration.Builder();
                    builder.setBrowserMatcher(AnyBrowserMatcher.INSTANCE);
                    builder.setConnectionBuilder(connectionBuilder);
                    
                    AuthorizationServiceInstance = new AuthorizationService(mContext, builder.build());
                }

            } catch (AuthConfiguration.InvalidConfigurationException e) {
                return null;
            }
        }
        return AuthorizationServiceInstance;
    }

    public void clearAuthorizationServiceInstance() {
        AuthorizationServiceInstance.dispose();
        AuthorizationServiceInstance = null;
    }
}
