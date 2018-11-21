package org.linphone.notifications;

/*
NotificationsManager.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.mediastream.Version;

import java.util.HashMap;

import static android.content.Context.NOTIFICATION_SERVICE;

public class NotificationsManager {
    private static final int SERVICE_NOTIF_ID = 1;
    private static final int MISSED_CALLS_NOTIF_ID = 2;
    private static final int IN_APP_NOTIF_ID = 3;

    private Context mContext;
    private NotificationManager mNM;
    private HashMap<String, Notifiable> mChatNotifMap, mCallNotifMap;
    private int mLastNotificationId;
    private Notification mServiceNotification;

    public NotificationsManager(Context context) {
        mContext = context;
        mChatNotifMap = new HashMap<>();
        mCallNotifMap = new HashMap<>();

        mNM = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);
        mNM.cancelAll();

        mLastNotificationId = 5; // Do not conflict with hardcoded notifications ids !

        Compatibility.createNotificationChannels(mContext);

        Intent notifIntent = new Intent(mContext, LinphoneService.instance().getIncomingReceivedActivity());
        notifIntent.putExtra("Notification", true);

        //Disable service notification for Android O
        if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
            LinphonePreferences.instance().setServiceNotificationVisibility(false);
        }

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.ic_launcher);
        } catch (Exception e) {
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mServiceNotification = Compatibility.createNotification(mContext, mContext.getString(R.string.service_name), "",
                R.drawable.linphone_notification_icon, R.mipmap.ic_launcher, bm, pendingIntent, true,
                Notification.PRIORITY_MIN);

        if (isServiceNotificationDisplayed()) {
            startForeground();
        }
    }

    public void destroy() {
        mNM.cancelAll();
    }

    public void startForeground() {
        LinphoneService.instance().startForeground(SERVICE_NOTIF_ID, mServiceNotification);
    }

    public void stopForeground() {
        LinphoneService.instance().stopForeground(true);
    }

    public void sendNotification(int id, Notification notif) {
        mNM.notify(id, notif);
    }

    public void resetMessageNotifCount(String address) {
        Notifiable notif = mChatNotifMap.get(address);
        if (notif != null) {
            notif.resetMessages();
            mNM.cancel(notif.getNotificationId());
        }
    }

    public boolean isServiceNotificationDisplayed() {
        return LinphonePreferences.instance().getServiceNotificationVisibility();
    }

    public String getSipUriForNotificationId(int notificationId) {
        for (String addr : mChatNotifMap.keySet()) {
            if (mChatNotifMap.get(addr).getNotificationId() == notificationId) {
                return addr;
            }
        }
        return null;
    }

    public void displayGroupChatMessageNotification(String subject, String conferenceAddress, String fromName, Uri fromPictureUri, String message, Address localIdentity, long timestamp) {
        Intent notifIntent = new Intent(mContext, LinphoneActivity.class);
        notifIntent.putExtra("GoToChat", true);
        notifIntent.putExtra("ChatContactSipUri", conferenceAddress);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bm = LinphoneUtils.getRoundBitmapFromUri(mContext, fromPictureUri);
        Notifiable notif = mChatNotifMap.get(conferenceAddress);
        NotifiableMessage notifMessage = new NotifiableMessage(message, fromName, timestamp);
        if (notif == null) {
            notif = new Notifiable(mLastNotificationId);
            mLastNotificationId += 1;
            mChatNotifMap.put(conferenceAddress, notif);
        }

        notifMessage.setSenderBitmap(bm);
        notif.addMessage(notifMessage);
        notif.setIsGroup(true);
        notif.setGroupTitle(subject);
        notif.setMyself(LinphoneUtils.getAddressDisplayName(localIdentity));
        notif.setLocalIdentity(localIdentity.asString());

        Notification notification = Compatibility.createMessageNotification(mContext, notif, subject,
                mContext.getString(R.string.group_chat_notif).replace("%1", fromName).replace("%2", message), bm, pendingIntent);
        sendNotification(notif.getNotificationId(), notification);
    }

    public void displayMessageNotification(String fromSipUri, String fromName, Uri fromPictureUri, String message, Address localIdentity, long timestamp) {
        Intent notifIntent = new Intent(mContext, LinphoneActivity.class);
        notifIntent.putExtra("GoToChat", true);
        notifIntent.putExtra("ChatContactSipUri", fromSipUri);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (fromName == null) {
            fromName = fromSipUri;
        }

        Bitmap bm = LinphoneUtils.getRoundBitmapFromUri(mContext, fromPictureUri);
        Notifiable notif = mChatNotifMap.get(fromSipUri);
        NotifiableMessage notifMessage = new NotifiableMessage(message, fromName, timestamp);
        if (notif == null) {
            notif = new Notifiable(mLastNotificationId);
            mLastNotificationId += 1;
            mChatNotifMap.put(fromSipUri, notif);
        }

        notifMessage.setSenderBitmap(bm);
        notif.addMessage(notifMessage);
        notif.setIsGroup(false);
        notif.setMyself(LinphoneUtils.getAddressDisplayName(localIdentity));
        notif.setLocalIdentity(localIdentity.asString());

        Notification notification = Compatibility.createMessageNotification(mContext, notif, fromName, message, bm, pendingIntent);
        sendNotification(notif.getNotificationId(), notification);
    }

    public void displayMissedCallNotification(Call call) {
        Intent missedCallNotifIntent = new Intent(mContext, LinphoneActivity.class);
        missedCallNotifIntent.putExtra("GoToHistory", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, missedCallNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int missedCallCount = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getMissedCallsCount();
        String body;
        if (missedCallCount > 1) {
            body = mContext.getString(R.string.missed_calls_notif_body).replace("%i", String.valueOf(missedCallCount));
        } else {
            Address address = call.getRemoteAddress();
            LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(address);
            if (c != null) {
                body = c.getFullName();
            } else {
                body = address.getDisplayName();
                if (body == null) {
                    body = address.asStringUriOnly();
                }
            }
        }

        Notification notif = Compatibility.createMissedCallNotification(mContext, mContext.getString(R.string.missed_calls_notif_title), body, pendingIntent);
        sendNotification(MISSED_CALLS_NOTIF_ID, notif);
    }

    public void displayCallNotification(Call call) {
        if (call == null) return;
        Intent callNotifIntent = new Intent(mContext, LinphoneService.instance().getIncomingReceivedActivity());
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, callNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Address address = call.getRemoteAddress();
        String addressAsString = address.asStringUriOnly();
        Notifiable notif = mCallNotifMap.get(addressAsString);
        if (notif == null) {
            notif = new Notifiable(mLastNotificationId);
            mLastNotificationId += 1;
            mCallNotifMap.put(addressAsString, notif);
        }

        if (!isServiceNotificationDisplayed()) {
            if (call.getCore().getCallsNb() == 0) {
                stopForeground();
            } else {
                startForeground();
            }
        }

        int notificationTextId;
        int iconId;
        switch (call.getState()) {
            case Released:
            case End:
                mNM.cancel(notif.getNotificationId());
                mCallNotifMap.remove(addressAsString);
                return;
            case Paused:
            case PausedByRemote:
            case Pausing:
                iconId = R.drawable.topbar_call_notification;
                notificationTextId = R.string.incall_notif_paused;
                break;
            default:
                if (call.getCurrentParams().videoEnabled()) {
                    iconId = R.drawable.topbar_videocall_notification;
                    notificationTextId = R.string.incall_notif_video;
                } else {
                    iconId = R.drawable.topbar_call_notification;
                    notificationTextId = R.string.incall_notif_active;
                }
                break;
        }

        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        Uri pictureUri = contact != null ? contact.getPhotoUri() : null;
        Bitmap bm = LinphoneUtils.getRoundBitmapFromUri(mContext, pictureUri);
        String name = LinphoneUtils.getAddressDisplayName(address);

        boolean showAnswerAction = call.getState() == Call.State.IncomingReceived || call.getState() == Call.State.IncomingEarlyMedia;
        Notification notification = Compatibility.createInCallNotification(mContext, notif.getNotificationId(),
                showAnswerAction, mContext.getString(R.string.service_name),
                mContext.getString(notificationTextId), iconId, bm, name, pendingIntent);
        sendNotification(notif.getNotificationId(), notification);
    }

    public String getSipUriForCallNotificationId(int notificationId) {
        for (String addr : mCallNotifMap.keySet()) {
            if (mCallNotifMap.get(addr).getNotificationId() == notificationId) {
                return addr;
            }
        }
        return null;
    }

    public void displayInappNotification(String message) {
        Intent notifIntent = new Intent(mContext, LinphoneActivity.class);
        notifIntent.putExtra("GoToInapp", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = Compatibility.createSimpleNotification(mContext, mContext.getString(R.string.inapp_notification_title), message, pendingIntent);
        sendNotification(IN_APP_NOTIF_ID, notif);
    }
}
