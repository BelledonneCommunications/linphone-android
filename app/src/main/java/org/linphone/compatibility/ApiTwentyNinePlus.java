/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.compatibility;

import static org.linphone.compatibility.Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_LOCAL_IDENTITY;
import static org.linphone.compatibility.Compatibility.INTENT_MARK_AS_READ_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;
import static org.linphone.compatibility.Compatibility.INTENT_REPLY_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.KEY_TEXT_REPLY;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import org.linphone.R;
import org.linphone.notifications.Notifiable;
import org.linphone.notifications.NotificationBroadcastReceiver;

@TargetApi(29)
public class ApiTwentyNinePlus {
    public static Notification.Action getReplyMessageAction(Context context, Notifiable notif) {
        String replyLabel = context.getResources().getString(R.string.notification_reply_label);
        RemoteInput remoteInput =
                new RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build();

        Intent replyIntent = new Intent(context, NotificationBroadcastReceiver.class);
        replyIntent.setAction(INTENT_REPLY_NOTIF_ACTION);
        replyIntent.putExtra(INTENT_NOTIF_ID, notif.getNotificationId());
        replyIntent.putExtra(INTENT_LOCAL_IDENTITY, notif.getLocalIdentity());

        PendingIntent replyPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        notif.getNotificationId(),
                        replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Action.Builder(
                        R.drawable.chat_send_over,
                        context.getString(R.string.notification_reply_label),
                        replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .build();
    }

    public static Notification.Action getMarkMessageAsReadAction(
            Context context, Notifiable notif) {
        Intent markAsReadIntent = new Intent(context, NotificationBroadcastReceiver.class);
        markAsReadIntent.setAction(INTENT_MARK_AS_READ_ACTION);
        markAsReadIntent.putExtra(INTENT_NOTIF_ID, notif.getNotificationId());
        markAsReadIntent.putExtra(INTENT_LOCAL_IDENTITY, notif.getLocalIdentity());

        PendingIntent markAsReadPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        notif.getNotificationId(),
                        markAsReadIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Action.Builder(
                        R.drawable.chat_send_over,
                        context.getString(R.string.notification_mark_as_read_label),
                        markAsReadPendingIntent)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build();
    }

    public static Notification.Action getCallAnswerAction(Context context, int callId) {
        Intent answerIntent = new Intent(context, NotificationBroadcastReceiver.class);
        answerIntent.setAction(INTENT_ANSWER_CALL_NOTIF_ACTION);
        answerIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent answerPendingIntent =
                PendingIntent.getBroadcast(
                        context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Action.Builder(
                        R.drawable.call_audio_start,
                        context.getString(R.string.notification_call_answer_label),
                        answerPendingIntent)
                .build();
    }

    public static Notification.Action getCallDeclineAction(Context context, int callId) {
        Intent hangupIntent = new Intent(context, NotificationBroadcastReceiver.class);
        hangupIntent.setAction(INTENT_HANGUP_CALL_NOTIF_ACTION);
        hangupIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent hangupPendingIntent =
                PendingIntent.getBroadcast(
                        context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Action.Builder(
                        R.drawable.call_hangup,
                        context.getString(R.string.notification_call_hangup_label),
                        hangupPendingIntent)
                .build();
    }
}
