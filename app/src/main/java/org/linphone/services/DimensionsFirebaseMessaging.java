package org.linphone.services;

import android.annotation.SuppressLint;
import android.content.Context;

import com.google.firebase.messaging.RemoteMessage;
import org.linphone.LinphoneApplication;
import org.linphone.core.tools.firebase.FirebaseMessaging;
import org.linphone.notifications.NotificationsManager;
import java.util.Objects;
import org.linphone.utils.Log;

public class DimensionsFirebaseMessaging extends FirebaseMessaging {
    public DimensionsFirebaseMessaging() {
    }

    String EVENT_TYPE = "Missed call";

    @Override
    public void onNewToken(final String token) {
        super.onNewToken(token);

        Context context = getApplicationContext();
        PushTokenService pushTokenService = PushTokenService.Companion.getInstance(context);
        pushTokenService.updateToken(token);
        pushTokenService.updateVoipToken(token);
    }

    /** @noinspection DataFlowIssue*/
    @SuppressLint("LogNotTimber")
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        try {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            if (notification != null) {
                String notificationBody = notification.getBody();
                if (Objects.equals(notificationBody, EVENT_TYPE)) {
                    NotificationsManager notificationsManager = LinphoneApplication.coreContext.getNotificationsManager();
                    notificationsManager.displayDimensionsMissedCallNotification(notification.getTitle());

                    Log.Log.d("FirebaseMessaging", "[Push Notification] Missed Call Received");
                    return;
                }
            }
        } catch (Exception e) {
            Log.Log.e("onMessageReceived", e);
        }

        super.onMessageReceived(remoteMessage);
    }
}
