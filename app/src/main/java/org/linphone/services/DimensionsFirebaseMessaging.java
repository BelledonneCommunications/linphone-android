package org.linphone.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.messaging.Constants;
import com.google.firebase.messaging.RemoteMessage;

import org.linphone.LinphoneApplication;
import org.linphone.core.tools.firebase.FirebaseMessaging;
import org.linphone.notifications.NotificationsManager;
import org.linphone.utils.Log;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DimensionsFirebaseMessaging extends FirebaseMessaging {
    public DimensionsFirebaseMessaging() {
    }

    static final String MISSED_CALL_BODY = "Missed call";

    @Override
    public void onNewToken(final String token) {
        super.onNewToken(token);

        Context context = getApplicationContext();
        PushTokenService pushTokenService = PushTokenService.Companion.getInstance(context);
        pushTokenService.updateToken(token);
        pushTokenService.updateVoipToken(token);
    }

    static final String ACTION_REMOTE_INTENT = "com.google.android.c2dm.intent.RECEIVE";

    @Override
    public void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_REMOTE_INTENT.equals(action) || ACTION_DIRECT_BOOT_REMOTE_INTENT.equals(action)) {
            Bundle bundle = getIntentBundle(intent);

            String message = bundle.getString("message");
            if (Objects.equals(message, MISSED_CALL_BODY)) return;

//            String body = bundle.getString(Constants.MessageNotificationKeys.NOTIFICATION_PREFIX_OLD + "body");
//            if (Objects.equals(body, MISSED_CALL_BODY)) return;
        }

        super.handleIntent(intent);
    }

    private Bundle getIntentBundle(Intent intent) {
        Bundle data = intent.getExtras();
        if (data == null) {
            // The intent should always have at least one extra so this shouldn't be null, but
            // this is the easiest way to handle the case where it does happen.
            data = new Bundle();
        }

        return data;
    }

    /** @noinspection DataFlowIssue*/
    @SuppressLint("LogNotTimber")
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
    }

    private final ConcurrentHashMap<String, Long> recentMissedCalls = new ConcurrentHashMap<>();
}
