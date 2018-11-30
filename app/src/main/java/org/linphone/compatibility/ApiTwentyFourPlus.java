package org.linphone.compatibility;

/*
ApiTwentyFourPlus.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import static org.linphone.compatibility.Compatibility.CHAT_NOTIFICATIONS_GROUP;
import static org.linphone.compatibility.Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_LOCAL_IDENTITY;
import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;
import static org.linphone.compatibility.Compatibility.INTENT_REPLY_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.KEY_TEXT_REPLY;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import org.linphone.R;
import org.linphone.notifications.Notifiable;
import org.linphone.notifications.NotifiableMessage;
import org.linphone.notifications.NotificationBroadcastReceiver;

@TargetApi(24)
public class ApiTwentyFourPlus {

    public static Notification createRepliedNotification(Context context, String reply) {
        return new Notification.Builder(context)
                .setSmallIcon(R.drawable.topbar_chat_notification)
                .setContentText(
                        context.getString(R.string.notification_replied_label).replace("%s", reply))
                .build();
    }

    public static Notification createMessageNotification(
            Context context, Notifiable notif, Bitmap contactIcon, PendingIntent intent) {
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

        Notification.Action action =
                new Notification.Action.Builder(
                                R.drawable.chat_send_over,
                                context.getString(R.string.notification_reply_label),
                                replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .setAllowGeneratedReplies(true)
                        .build();

        Notification.MessagingStyle style = new Notification.MessagingStyle(notif.getMyself());
        for (NotifiableMessage message : notif.getMessages()) {
            Notification.MessagingStyle.Message msg =
                    new Notification.MessagingStyle.Message(
                            message.getMessage(), message.getTime(), message.getSender());
            if (message.getFilePath() != null)
                msg.setData(message.getFileMime(), message.getFilePath());
            style.addMessage(msg);
        }
        if (notif.isGroup()) {
            style.setConversationTitle(notif.getGroupTitle());
        }

        return new Notification.Builder(context)
                .setSmallIcon(R.drawable.topbar_chat_notification)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setDefaults(
                        Notification.DEFAULT_SOUND
                                | Notification.DEFAULT_VIBRATE
                                | Notification.DEFAULT_LIGHTS)
                .setLargeIcon(contactIcon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroup(CHAT_NOTIFICATIONS_GROUP)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setNumber(notif.getMessages().size())
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColor(context.getColor(R.color.notification_color_led))
                .setStyle(style)
                .addAction(action)
                .build();
    }

    public static Notification createInCallNotification(
            Context context,
            int callId,
            boolean showAnswerAction,
            String msg,
            int iconID,
            Bitmap contactIcon,
            String contactName,
            PendingIntent intent) {

        Intent hangupIntent = new Intent(context, NotificationBroadcastReceiver.class);
        hangupIntent.setAction(INTENT_HANGUP_CALL_NOTIF_ACTION);
        hangupIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent hangupPendingIntent =
                PendingIntent.getBroadcast(
                        context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder =
                new Notification.Builder(context)
                        .setContentTitle(contactName)
                        .setContentText(msg)
                        .setSmallIcon(iconID)
                        .setAutoCancel(false)
                        .setContentIntent(intent)
                        .setLargeIcon(contactIcon)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setColor(context.getColor(R.color.notification_color_led))
                        .addAction(
                                R.drawable.call_hangup,
                                context.getString(R.string.notification_call_hangup_label),
                                hangupPendingIntent);

        if (showAnswerAction) {
            Intent answerIntent = new Intent(context, NotificationBroadcastReceiver.class);
            answerIntent.setAction(INTENT_ANSWER_CALL_NOTIF_ACTION);
            answerIntent.putExtra(INTENT_NOTIF_ID, callId);

            PendingIntent answerPendingIntent =
                    PendingIntent.getBroadcast(
                            context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(
                    R.drawable.call_audio_start,
                    context.getString(R.string.notification_call_answer_label),
                    answerPendingIntent);
        }

        return builder.build();
    }
}
