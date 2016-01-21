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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;

/**
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
public final class LinphoneService extends Service {
	/* Listener needs to be implemented in the Service as it calls
	 * setLatestEventInfo and startActivity() which needs a context.
	 */
	public static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
	public static final int IC_LEVEL_ORANGE=0;
	/*private static final int IC_LEVEL_GREEN=1;
	private static final int IC_LEVEL_RED=2;*/
	//public static final int IC_LEVEL_OFFLINE=3;
	
	private static LinphoneService instance;
	
	private final static int NOTIF_ID=1;
	private final static int INCALL_NOTIF_ID=2;
	private final static int MESSAGE_NOTIF_ID=3;
	private final static int CUSTOM_NOTIF_ID=4;
	
	public static boolean isReady() {
		return instance != null && instance.mTestDelayElapsed;
	}

	/**
	 * @throws RuntimeException service not instantiated
	 */
	public static LinphoneService instance()  {
		if (isReady()) return instance;

		throw new RuntimeException("LinphoneService not instantiated yet");
	}

	public Handler mHandler = new Handler();

//	private boolean mTestDelayElapsed; // add a timer for testing
	private boolean mTestDelayElapsed = true; // no timer
	private NotificationManager mNM;

	private Notification mNotif;
	private Notification mIncallNotif;
	private Notification mMsgNotif;
	private Notification mCustomNotif;
	private int mMsgNotifCount;
	private PendingIntent mNotifContentIntent;
	private PendingIntent mkeepAlivePendingIntent;
	private String mNotificationTitle;
	private boolean mDisableRegistrationStatus;
	private LinphoneCoreListenerBase mListener;
	public static int notifcationsPriority = (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41) ? Notification.PRIORITY_MIN : 0);

	public int getMessageNotifCount() {
		return mMsgNotifCount;
	}
	
	public void resetMessageNotifCount() {
		mMsgNotifCount = 0;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// In case restart after a crash. Main in LinphoneActivity
		mNotificationTitle = getString(R.string.service_name);

		// Needed in order for the two next calls to succeed, libraries must have been loaded first
		LinphoneCoreFactory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
		LinphoneCoreFactory.instance().enableLogCollection(!(getResources().getBoolean(R.bool.disable_every_log)));
		
		// Dump some debugging information to the logs
		Log.i(START_LINPHONE_LOGS);
		dumpDeviceInformation();
		dumpInstalledLinphoneInformation();

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.cancel(INCALL_NOTIF_ID); // in case of crash the icon is not removed

		Intent notifIntent = new Intent(this, incomingReceivedActivity);
		notifIntent.putExtra("Notification", true);
		mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Bitmap bm = null;
		try {
			bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		} catch (Exception e) {
		}
		mNotif = Compatibility.createNotification(this, mNotificationTitle, "", R.drawable.linphone_notification_icon, R.mipmap.ic_launcher, bm, mNotifContentIntent, true,notifcationsPriority);

		LinphoneManager.createAndStart(LinphoneService.this);

		instance = this; // instance is ready once linphone manager has been created
		LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase(){

			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
				if (instance == null) {
					Log.i("Service not ready, discarding call state change to ",state.toString());
					return;
				}
				
				if (state == LinphoneCall.State.IncomingReceived) {
					onIncomingReceived();
				}
				
				if (state == State.CallUpdatedByRemote) {
					// If the correspondent proposes video while audio call
					boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
					boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
					boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
					if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
						try {
							LinphoneManager.getLc().deferCallUpdate(call);
						} catch (LinphoneCoreException e) {
							e.printStackTrace();
						}
					}
				}

				if (state == State.StreamsRunning) {
					// Workaround bug current call seems to be updated after state changed to streams running
					if (getResources().getBoolean(R.bool.enable_call_notification))
						refreshIncallIcon(call);
				} else {
					if (getResources().getBoolean(R.bool.enable_call_notification))
						refreshIncallIcon(LinphoneManager.getLc().getCurrentCall());
				}
			}
			
			@Override
			public void globalState(LinphoneCore lc,LinphoneCore.GlobalState state, String message) {
				if (state == GlobalState.GlobalOn) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
				}
			}

			@Override
			public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
//				if (instance == null) {
//					Log.i("Service not ready, discarding registration state change to ",state.toString());
//					return;
//				}
				if (!mDisableRegistrationStatus) {
					if (state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
						sendNotification(IC_LEVEL_ORANGE, R.string.notification_registered);
					}
			
					if ((state == RegistrationState.RegistrationFailed || state == RegistrationState.RegistrationCleared) && (LinphoneManager.getLc().getDefaultProxyConfig() == null || !LinphoneManager.getLc().getDefaultProxyConfig().isRegistered())) {
						sendNotification(IC_LEVEL_ORANGE, R.string.notification_register_failure);
					}
					
					if (state == RegistrationState.RegistrationNone) {
						sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
					}
				}
			}
		});
		
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
				Log.e(e, "Couldn't find startForeground or stopForeground");
			}
		}

		this.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mObserver);

		startForegroundCompat(NOTIF_ID, mNotif);

		if (!mTestDelayElapsed) {
			// Only used when testing. Simulates a 5 seconds delay for launching service
			mHandler.postDelayed(new Runnable() {
				@Override public void run() {
					mTestDelayElapsed = true;
				}
			}, 5000);
		}
		
		//make sure the application will at least wakes up every 10 mn
		Intent intent = new Intent(this, KeepAliveHandler.class);
	    mkeepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
		((AlarmManager) this.getSystemService(Context.ALARM_SERVICE)).setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP
																							, SystemClock.elapsedRealtime()+600000
																							, 600000
																							, mkeepAlivePendingIntent);
	}

	private ContentObserver mObserver = new ContentObserver(new Handler()) {

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
		}

	};
		

	private enum IncallIconState {INCALL, PAUSE, VIDEO, IDLE}
	private IncallIconState mCurrentIncallIconState = IncallIconState.IDLE;
	private synchronized void setIncallIcon(IncallIconState state) {
		if (state == mCurrentIncallIconState) return;
		mCurrentIncallIconState = state;

		int notificationTextId = 0;
		int inconId = 0;
		
		switch (state) {
		case IDLE:
			mNM.cancel(INCALL_NOTIF_ID);
			return;
		case INCALL:
			inconId = R.drawable.topbar_call_notification;
			notificationTextId = R.string.incall_notif_active;
			break;
		case PAUSE:
			inconId = R.drawable.topbar_call_notification;
			notificationTextId = R.string.incall_notif_paused;
			break;
		case VIDEO:
			inconId = R.drawable.topbar_videocall_notification;
			notificationTextId = R.string.incall_notif_video;
			break;	
		default:
			throw new IllegalArgumentException("Unknown state " + state);
		}
		
		if (LinphoneManager.getLc().getCallsNb() == 0) {
			return;
		}
		
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		String userName = call.getRemoteAddress().getUserName();
		String domain = call.getRemoteAddress().getDomain();
		String displayName = call.getRemoteAddress().getDisplayName();
		LinphoneAddress address = LinphoneCoreFactory.instance().createLinphoneAddress(userName,domain,null);
		address.setDisplayName(displayName);

		Contact contact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), address);
		Uri pictureUri = contact != null ? contact.getPhotoUri() : null;
		Bitmap bm = null;
		try {
			bm = MediaStore.Images.Media.getBitmap(getContentResolver(), pictureUri);
		} catch (Exception e) {
			bm = BitmapFactory.decodeResource(getResources(), R.drawable.avatar);
		}
		String name = address.getDisplayName() == null ? address.getUserName() : address.getDisplayName();
		mIncallNotif = Compatibility.createInCallNotification(getApplicationContext(), mNotificationTitle, getString(notificationTextId), inconId, bm, name, mNotifContentIntent);

		notifyWrapper(INCALL_NOTIF_ID, mIncallNotif);
	}

	public void refreshIncallIcon(LinphoneCall currentCall) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (currentCall != null) {
			if (currentCall.getCurrentParamsCopy().getVideoEnabled() && currentCall.cameraEnabled()) {
				// checking first current params is mandatory
				setIncallIcon(IncallIconState.VIDEO);
			} else {
				setIncallIcon(IncallIconState.INCALL);
			}
		} else if (lc.getCallsNb() == 0) {
			setIncallIcon(IncallIconState.IDLE);
		}  else if (lc.isInConference()) {
			setIncallIcon(IncallIconState.INCALL);
		} else {
			setIncallIcon(IncallIconState.PAUSE);
		}
	}

	@Deprecated 
	public void addNotification(Intent onClickIntent, int iconResourceID, String title, String message) {
		addCustomNotification(onClickIntent, iconResourceID, title, message, true);
	}
	
	public void addCustomNotification(Intent onClickIntent, int iconResourceID, String title, String message, boolean isOngoingEvent) {
		PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		Bitmap bm = null;
		try {
			bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		} catch (Exception e) {
		}
		mCustomNotif = Compatibility.createNotification(this, title, message, iconResourceID, 0, bm, notifContentIntent, isOngoingEvent,notifcationsPriority);
		
		mCustomNotif.defaults |= Notification.DEFAULT_VIBRATE;
		mCustomNotif.defaults |= Notification.DEFAULT_SOUND;
		mCustomNotif.defaults |= Notification.DEFAULT_LIGHTS;
		
		notifyWrapper(CUSTOM_NOTIF_ID, mCustomNotif);
	}
	
	public void removeCustomNotification() {
		mNM.cancel(CUSTOM_NOTIF_ID);
		resetIntentLaunchedOnNotificationClick();
	}
	
	public void displayMessageNotification(String fromSipUri, String fromName, String message) {
		Intent notifIntent = new Intent(this, LinphoneActivity.class);
		notifIntent.putExtra("GoToChat", true);
		notifIntent.putExtra("ChatContactSipUri", fromSipUri);
		
		PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		if (fromName == null) {
			fromName = fromSipUri;
		}
		
		if (mMsgNotif == null) {
			mMsgNotifCount = 1;
		} else {
			mMsgNotifCount++;
		}
		
		Uri pictureUri = null;
		try {
			Contact contact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), LinphoneCoreFactory.instance().createLinphoneAddress(fromSipUri));
			if (contact != null)
				pictureUri = contact.getThumbnailUri();
		} catch (LinphoneCoreException e1) {
			Log.e("Cannot parse from address ", e1);
		}
		
		Bitmap bm = null;
		if (pictureUri != null) {
			try {
				bm = MediaStore.Images.Media.getBitmap(getContentResolver(), pictureUri);
			} catch (Exception e) {
				bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
			}
		} else {
			bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
		}
		mMsgNotif = Compatibility.createMessageNotification(getApplicationContext(), mMsgNotifCount, fromName, message, bm, notifContentIntent);
		
		notifyWrapper(MESSAGE_NOTIF_ID, mMsgNotif);
	}
	
	public void removeMessageNotification() {
		mNM.cancel(MESSAGE_NOTIF_ID);
		resetIntentLaunchedOnNotificationClick();
	}

	private static final Class<?>[] mSetFgSign = new Class[] {boolean.class};
	private static final Class<?>[] mStartFgSign = new Class[] {
		int.class, Notification.class};
	private static final Class<?>[] mStopFgSign = new Class[] {boolean.class};

	private Method mSetForeground;
	private Method mStartForeground;
	private Method mStopForeground;
	private Object[] mSetForegroundArgs = new Object[1];
	private Object[] mStartForegroundArgs = new Object[2];
	private Object[] mStopForegroundArgs = new Object[1];
	private Class<? extends Activity> incomingReceivedActivity = LinphoneActivity.class;

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

		notifyWrapper(id, notification);
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
	
	@SuppressWarnings("deprecation")
	private void dumpDeviceInformation() {
		StringBuilder sb = new StringBuilder();
		sb.append("DEVICE=").append(Build.DEVICE).append("\n");
		sb.append("MODEL=").append(Build.MODEL).append("\n");
		//MANUFACTURER doesn't exist in android 1.5.
		//sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
		sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
		sb.append("EABI=").append(Version.getCpuAbis().get(0)).append("\n");
		Log.i(sb.toString());
	}

	private void dumpInstalledLinphoneInformation() {
		PackageInfo info = null;
		try {
		    info = getPackageManager().getPackageInfo(getPackageName(),0);
		} catch (NameNotFoundException nnfe) {}

		if (info != null) {
			Log.i("Linphone version is ", info.versionName + " (" + info.versionCode + ")");
		} else {
			Log.i("Linphone version is unknown");
		}
	}
	
	public void disableNotificationsAutomaticRegistrationStatusContent() {
		mDisableRegistrationStatus = true;
	}

	public synchronized void sendNotification(int level, int textId) {
		String text = getString(textId);
		if (text.contains("%s") && LinphoneManager.getLc() != null) {
			// Test for null lc is to avoid a NPE when Android mess up badly with the String resources.
			LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
			String id = lpc != null ? lpc.getIdentity() : "";
			text = String.format(text, id);
		}

		Bitmap bm = null;
		try {
			bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		} catch (Exception e) {
		}
		mNotif = Compatibility.createNotification(this, mNotificationTitle, text, R.drawable.status_level, 0, bm, mNotifContentIntent, true,notifcationsPriority);
		notifyWrapper(NOTIF_ID, mNotif);
	}

	/**
	 * Wrap notifier to avoid setting the linphone icons while the service
	 * is stopping. When the (rare) bug is triggered, the linphone icon is
	 * present despite the service is not running. To trigger it one could
	 * stop linphone as soon as it is started. Transport configured with TLS.
	 */
	private synchronized void notifyWrapper(int id, Notification notification) {
		if (instance != null && notification != null) {
			mNM.notify(id, notification);
		} else {
			Log.i("Service not ready, discarding notification");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTaskRemoved(Intent rootIntent) {
		if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
			Log.d("Task removed, stop service");
			LinphoneManager.getLc().setNetworkReachable(false);
			stopSelf();
		}
		super.onTaskRemoved(rootIntent);
	}

	@Override
	public synchronized void onDestroy() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		
		instance = null;
		LinphoneManager.destroy();

	    // Make sure our notification is gone.
	    stopForegroundCompat(NOTIF_ID);
	    mNM.cancel(INCALL_NOTIF_ID);
	    mNM.cancel(MESSAGE_NOTIF_ID);

	    ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE)).cancel(mkeepAlivePendingIntent);
		getContentResolver().unregisterContentObserver(mObserver);
		super.onDestroy();
	}
	
	public void setActivityToLaunchOnIncomingReceived(Class<? extends Activity> activity) {
		incomingReceivedActivity = activity;
		resetIntentLaunchedOnNotificationClick();
	}
	
	private void resetIntentLaunchedOnNotificationClick() {
		Intent notifIntent = new Intent(this, incomingReceivedActivity);
		mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		if (mNotif != null) {
			mNotif.contentIntent = mNotifContentIntent;
		}
		notifyWrapper(NOTIF_ID, mNotif);
	}
	
	protected void onIncomingReceived() {
		//wakeup linphone
		startActivity(new Intent()
				.setClass(this, incomingReceivedActivity)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
	}


	public void tryingNewOutgoingCallButAlreadyInCall() {
	}

	public void tryingNewOutgoingCallButCannotGetCallParameters() {
	}

	public void tryingNewOutgoingCallButWrongDestinationAddress() {
	}

	public void onCallEncryptionChanged(final LinphoneCall call, final boolean encrypted,
			final String authenticationToken) {
	}
}

