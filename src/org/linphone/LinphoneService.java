/*
LinphoneService.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.linphone;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.linphone.LinphoneManager.NewOutgoingCallUiListener;
import org.linphone.LinphoneSimpleListener.LinphoneServiceListener;
import org.linphone.core.LinphoneCall;
import org.linphone.core.Log;
import org.linphone.core.OnlineStatus;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.mediastream.Version;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

/***
 * 
 * Linphone service, reacting to Incoming calls, ...<br />
 * 
 * Roles include:<ul>
 * <li>Initializing LinphoneManager</li>
 * <li>Starting C libLinphone through LinphoneManager</li>
 * <li>Reacting to LinphoneManager state changes</li>
 * <li>Delegating GUI state change actions to GUI listener</li>
 * 
 * 
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneService extends Service implements LinphoneServiceListener {
	/* Listener needs to be implemented in the Service as it calls
	 * setLatestEventInfo and startActivity() which needs a context.
	 */

	private Handler mHandler = new Handler();
	private static LinphoneService instance;

	public static boolean isReady() { return (instance!=null);	}

	/**
	 * @throws RuntimeException service not instantiated
	 */
	static LinphoneService instance()  {
		if (isReady()) return instance;

		throw new RuntimeException("LinphoneService not instantiated yet");
	}

	
	private final static int NOTIF_ID=1;

	private Notification mNotif;
	private PendingIntent mNotifContentIntent;
	private String notificationTitle;


	private static final int IC_LEVEL_ORANGE=0;
	/*private static final int IC_LEVEL_GREEN=1;
	private static final int IC_LEVEL_RED=2;*/
	private static final int IC_LEVEL_OFFLINE=3;

	
	
	

	@Override
	public void onCreate() {
		super.onCreate();
		
		// In case restart after a crash. Main in LinphoneActivity
		LinphonePreferenceManager.getInstance(this);

		// Set default preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

		
		notificationTitle = getString(R.string.app_name);

		// Dump some debugging information to the logs
		Log.i(START_LINPHONE_LOGS);
		dumpDeviceInformation();
		dumpInstalledLinphoneInformation();

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNotif = new Notification(R.drawable.status_level, "", System.currentTimeMillis());
		mNotif.iconLevel=IC_LEVEL_ORANGE;
		mNotif.flags |= Notification.FLAG_ONGOING_EVENT;

		Intent notifIntent = new Intent(this, LinphoneActivity.class);
		mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, 0);
		mNotif.setLatestEventInfo(this, notificationTitle,"", mNotifContentIntent);
	
		LinphoneManager.createAndStart(this, this);
		LinphoneManager.getLc().setPresenceInfo(0, null, OnlineStatus.Online);
		instance = this; // instance is ready once linphone manager has been created


		// Retrieve methods to publish notification and keep Android
		// from killing us and keep the audio quality high.
		if (Version.sdkStrictlyBelow(Version.API05_ECLAIR_20)) {
			try {
				mSetForeground = getClass().getMethod("setForeground", mSetFgSign);
			} catch (NoSuchMethodException e) {
				Log.e(e, "Couldn't find foreground method");
			}
		} else {
			try {
				mStartForeground = getClass().getMethod("startForeground", mStartFgSign);
				mStopForeground = getClass().getMethod("stopForeground", mStopFgSign);
			} catch (NoSuchMethodException e) {
				Log.e(e, "Couldn't find startGoreground or stopForeground");
			}
		}

		startForegroundCompat(NOTIF_ID, mNotif);
	}



	private static final Class<?>[] mSetFgSign = new Class[] {boolean.class};
	private static final Class<?>[] mStartFgSign = new Class[] {
		int.class, Notification.class};
	private static final Class<?>[] mStopFgSign = new Class[] {boolean.class};

	private NotificationManager mNM;
	private Method mSetForeground;
	private Method mStartForeground;
	private Method mStopForeground;
	private Object[] mSetForegroundArgs = new Object[1];
	private Object[] mStartForegroundArgs = new Object[2];
	private Object[] mStopForegroundArgs = new Object[1];

	void invokeMethod(Method method, Object[] args) {
		try {
			method.invoke(this, args);
		} catch (InvocationTargetException e) {
			// Should not happen.
			Log.w(e, "Unable to invoke method");
		} catch (IllegalAccessException e) {
			// Should not happen.
			Log.w(e, "Unable to invoke method");
		}
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 */
	void startForegroundCompat(int id, Notification notification) {
		// If we have the new startForeground API, then use it.
		if (mStartForeground != null) {
			mStartForegroundArgs[0] = Integer.valueOf(id);
			mStartForegroundArgs[1] = notification;
			invokeMethod(mStartForeground, mStartForegroundArgs);
			return;
		}

		// Fall back on the old API.
		if (mSetForeground != null) {
			mSetForegroundArgs[0] = Boolean.TRUE;
			invokeMethod(mSetForeground, mSetForegroundArgs);
			// continue
		}

		mNM.notify(id, notification);
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 */
	void stopForegroundCompat(int id) {
		// If we have the new stopForeground API, then use it.
		if (mStopForeground != null) {
			mStopForegroundArgs[0] = Boolean.TRUE;
			invokeMethod(mStopForeground, mStopForegroundArgs);
			return;
		}

		// Fall back on the old API.  Note to cancel BEFORE changing the
		// foreground state, since we could be killed at that point.
		mNM.cancel(id);
		if (mSetForeground != null) {
			mSetForegroundArgs[0] = Boolean.FALSE;
			invokeMethod(mSetForeground, mSetForegroundArgs);
		}
	}




	public static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
	private void dumpDeviceInformation() {
		StringBuilder sb = new StringBuilder();
		sb.append("DEVICE=").append(Build.DEVICE).append("\n");
		sb.append("MODEL=").append(Build.MODEL).append("\n");
		//MANUFACTURER doesn't exist in android 1.5.
		//sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
		sb.append("SDK=").append(Build.VERSION.SDK);
		Log.i(sb.toString());
	}



	private void dumpInstalledLinphoneInformation() {
		PackageInfo info = null;
		try {
		    info = getPackageManager().getPackageInfo(getPackageName(),0);
		} catch (NameNotFoundException nnfe) {}

		if (info != null) {
			Log.i("Linphone version is ", info.versionCode);
		} else {
			Log.i("Linphone version is unknown");
		}
	}

	private void sendNotification(int level, int textId) {
		mNotif.iconLevel = level;
		mNotif.when=System.currentTimeMillis();
		String text = getString(textId);
		if (text.contains("%s")) {
			String id = LinphoneManager.getLc().getDefaultProxyConfig().getIdentity();
			text = String.format(text, id);
		}
		
		mNotif.setLatestEventInfo(this, notificationTitle, text, mNotifContentIntent);
		mNM.notify(NOTIF_ID, mNotif);
	}




	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	    // Make sure our notification is gone.
	    stopForegroundCompat(NOTIF_ID);

		LinphoneManager.getLcIfManagerNotDestroyedOrNull().setPresenceInfo(0, null, OnlineStatus.Offline);
		LinphoneManager.destroy(this);
		instance=null;
	}

	
	private static final LinphoneGuiListener guiListener() {
		return DialerActivity.instance();
	}

	
	
	
	

	public void onDisplayStatus(final String message) {
		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null) guiListener().onDisplayStatus(message);				
			}
		});
	}

	public void onGlobalStateChanged(final GlobalState state, final String message) {
		if (state == GlobalState.GlobalOn) {
			sendNotification(IC_LEVEL_OFFLINE, R.string.notification_started);
			
			mHandler.post(new Runnable() {
				public void run() {
					if (guiListener() != null)
						guiListener().onGlobalStateChangedToOn(message);				
				}
			});
		}
	}


	public void onRegistrationStateChanged(final RegistrationState state,
			final String message) {
		if (state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
			sendNotification(IC_LEVEL_ORANGE, R.string.notification_registered);
		}

		if (state == RegistrationState.RegistrationFailed) {
			sendNotification(IC_LEVEL_OFFLINE, R.string.notification_register_failure);
		}

		if (state == RegistrationState.RegistrationOk || state == RegistrationState.RegistrationFailed) {
			mHandler.post(new Runnable() {
				public void run() {
					if (LinphoneActivity.isInstanciated())
							LinphoneActivity.instance().onRegistrationStateChanged(state, message);
				}
			});
		}
	}


	public void onCallStateChanged(final LinphoneCall call, final State state, final String message) {
		if (state == LinphoneCall.State.IncomingReceived) {
			//wakeup linphone
			startActivity(new Intent()
					.setClass(this, LinphoneActivity.class)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} else if (state == LinphoneCall.State.StreamsRunning) {
			if (Version.isVideoCapable() 
					&& getResources().getBoolean(R.bool.use_video_activity)
					&& !VideoCallActivity.launched && LinphoneActivity.isInstanciated()
					&& call.getCurrentParamsCopy().getVideoEnabled()) {
				// Do not call if video activity already launched as it would cause a pause() of the launched one
				// and a race condition with capture surfaceview leading to a crash
				LinphoneActivity.instance().startVideoActivity();
			}
		}

		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null)
					guiListener().onCallStateChanged(call, state, message);			
			}
		});
	}


	
	public interface LinphoneGuiListener extends NewOutgoingCallUiListener {
		void onDisplayStatus(String message);
		void onGlobalStateChangedToOn(String message);
//		void onRegistrationStateChanged(RegistrationState state, String message);
		void onCallStateChanged(LinphoneCall call, State state, String message);
		void onCallEncryptionChanged(LinphoneCall call, boolean encrypted,
				String authenticationToken);
	}


	public void onRingerPlayerCreated(MediaPlayer mRingerPlayer) {
		final Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		try {
			mRingerPlayer.setDataSource(getApplicationContext(), ringtoneUri);
		} catch (IOException e) {
			Log.e(e, "cannot set ringtone");
		}
	}

	public void tryingNewOutgoingCallButAlreadyInCall() {
		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null)
					guiListener().onAlreadyInCall();			
			}
		});
	}

	public void tryingNewOutgoingCallButCannotGetCallParameters() {
		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null)
					guiListener().onCannotGetCallParameters();			
			}
		});
	}

	public void tryingNewOutgoingCallButWrongDestinationAddress() {
		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null)
					guiListener().onWrongDestinationAddress();			
			}
		});
	}

	public void onAlreadyInVideoCall() {
		LinphoneActivity.instance().startVideoActivity();
	}

	public void onCallEncryptionChanged(final LinphoneCall call, final boolean encrypted,
			final String authenticationToken) {
		mHandler.post(new Runnable() {
			public void run() {
				if (guiListener() != null)
					guiListener().onCallEncryptionChanged(call, encrypted, authenticationToken);			
			}
		});
	}
}

