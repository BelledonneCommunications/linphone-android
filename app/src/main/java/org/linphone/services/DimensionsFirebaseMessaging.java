package org.linphone.services;

import android.content.Context;
import android.content.Intent;

import org.linphone.core.tools.firebase.FirebaseMessaging;

public class DimensionsFirebaseMessaging extends FirebaseMessaging {
    public DimensionsFirebaseMessaging() {
    }

    @Override
    public void onNewToken(final String token) {
        super.onNewToken(token);

        Context context = getApplicationContext();
        PushTokenService pushTokenService = PushTokenService.Companion.getInstance(context);
        pushTokenService.updateToken(token);
        pushTokenService.updateVoipToken(token);

        UserService.Companion.getInstance(context).createUserSession();
    }

    @Override
    public void handleIntent(Intent intent) {
        if (notWeirdPayload(intent) && notNoUIPayload(intent)) {
            super.handleIntent(intent);
        }
    }

    private boolean notNoUIPayload(Intent intent) {
        String body = intent.getExtras().getString("body");
        if (body == null || body.isBlank()) {
            body = intent.getExtras().getString("gcm.notification.body");
        }
        return (body == null || !body.equals("NOUI"));
    }

    private boolean notWeirdPayload(Intent intent) {
        String body = intent.getExtras().getString("body");
        String title = intent.getExtras().getString("title");

        return (body ==null || body.isBlank()) &&
                (title == null || title.isBlank());
    }
}
