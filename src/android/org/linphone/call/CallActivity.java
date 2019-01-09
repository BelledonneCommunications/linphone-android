package org.linphone.call;

/*
CallActivity.java
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

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.core.ChatRoomSecurityLevel;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.ZrtpPeerStatus;
import org.linphone.receivers.BluetoothManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.activities.LinphoneGenericActivity;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallListenerStub;
import org.linphone.core.Call.State;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.AddressFamily;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.MediaEncryption;
import org.linphone.core.Player;
import org.linphone.core.PayloadType;
import org.linphone.core.StreamType;
import org.linphone.fragments.StatusFragment;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.Numpad;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static org.linphone.LinphoneUtils.getSecurityLevelForSipUri;
import static org.linphone.LinphoneUtils.getZrtpStatus;

public class CallActivity extends LinphoneGenericActivity implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
	private final static int SECONDS_BEFORE_HIDING_CONTROLS = 4000;
	private final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;
	private static final int PERMISSIONS_REQUEST_CAMERA = 202;
	private static final int PERMISSIONS_ENABLED_CAMERA = 203;
	private static final int PERMISSIONS_ENABLED_MIC = 204;

	private static CallActivity instance;

	private Handler mControlsHandler = new Handler();
	private Runnable mControls;
	private ImageView switchCamera;
	private TextView missedChats;
	private RelativeLayout mActiveCallHeader, sideMenuContent, avatar_layout, action_bar, mCallPaused;
	private ImageView pause, pause2, hangUp, dialer, video, micro, speaker, options, addCall, transfer, conference, conferenceStatus, contactPicture;
	private ImageView audioRoute, routeSpeaker, routeEarpiece, routeBluetooth, menu, chat, encryption;
	private LinearLayout mNoCurrentCall, callInfo;
	private ProgressBar videoProgress;
	private StatusFragment status;
	private CallAudioFragment audioCallFragment;
	private CallVideoFragment videoCallFragment;
	private boolean isSpeakerEnabled = false, isMicMuted = false, isTransferAllowed, isVideoAsk, isZrtpAsk;
	private LinearLayout mControlsLayout;
	private Numpad numpad;
	private int cameraNumber;
	private CountDownTimer timer;
	private boolean isVideoCallPaused = false;
	private Dialog dialog = null;
	private static long TimeRemind = 0;
	private HeadsetReceiver headsetReceiver;
	private Dialog ZRTPdialog = null;

	private LinearLayout callsList, conferenceList;
	private LayoutInflater inflater;
	private ViewGroup container;
	private boolean isConferenceRunning = false;
	private CoreListenerStub mListener;
	private DrawerLayout sideMenu;

	private Handler mHandler = new Handler();
	private Timer mTimer;
	private TimerTask mTask;
	private HashMap<String, String> mEncoderTexts;
	private HashMap<String, String> mDecoderTexts;
	private CallListenerStub mCallListener;
	private Call mCallDisplayedInStats;

	private boolean oldIsSpeakerEnabled = false;

	public static CallActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;

		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		setContentView(R.layout.call);

		//Earset Connectivity Broadcast Processing
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("android.intent.action.HEADSET_PLUG");
		headsetReceiver = new HeadsetReceiver();
		registerReceiver(headsetReceiver, intentFilter);

		isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

		cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;

		mEncoderTexts = new HashMap<String, String>();
		mDecoderTexts = new HashMap<String, String>();

		mListener = new CoreListenerStub() {
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
		        displayMissedChats();
			}

			@Override
			public void onCallStateChanged(Core lc, final Call call, Call.State state, String message) {
				if (LinphoneManager.getLc().getCallsNb() == 0) {
					LinphoneService.instance().removeSasNotification();
					setisZrtpAsk(false);
					finish();
					return;
				}

				if (state == State.IncomingReceived) {
					startIncomingCallActivity();
					return;
				} else if (state == State.Paused || state == State.PausedByRemote ||  state == State.Pausing) {
					if(LinphoneManager.getLc().getCurrentCall() != null) {
						enabledVideoButton(false);
					}
					if(isVideoEnabled(call)){
						showAudioView();
					}
				} else if (state == State.Resuming) {
					if(LinphonePreferences.instance().isVideoEnabled()){
						refreshStatusItems(call, isVideoEnabled(call));
						if(call.getCurrentParams().videoEnabled()){
							showVideoView();
						}
					}
					if(LinphoneManager.getLc().getCurrentCall() != null) {
						enabledVideoButton(true);
					}
				} else if (state == State.StreamsRunning) {
					switchVideo(isVideoEnabled(call));
					enableAndRefreshInCallActions();

					videoProgress.setVisibility(View.GONE);
					refreshStatusItems(call, isVideoEnabled(call));
				} else if (state == State.UpdatedByRemote) {
					// If the correspondent proposes video while audio call
					boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
					if (!videoEnabled) {
						acceptCallUpdate(false);
					}

					boolean remoteVideo = call.getRemoteParams().videoEnabled();
					boolean localVideo = call.getCurrentParams().videoEnabled();
					boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
					if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
							showAcceptCallUpdateDialog();
							createTimerForDialog(SECONDS_BEFORE_DENYING_CALL_UPDATE);
					}
//        			else if (remoteVideo && !LinphoneManager.getLc().(getConference() != null) && autoAcceptCameraPolicy) {
//        				mHandler.post(new Runnable() {
//        					@Override
//        					public void run() {
//        						acceptCallUpdate(true);
//        					}
//        				});
//        			}
				}

				refreshIncallUi();
				transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
			}

			@Override
			public void onCallEncryptionChanged(Core lc, final Call call, boolean encrypted, String authenticationToken) {
				if(call.getCurrentParams().getMediaEncryption().equals(MediaEncryption.ZRTP) && !call.getAuthenticationTokenVerified()){
						showZRTPDialog(call);
					}
					refreshStatusItems(call, call.getCurrentParams().videoEnabled());
			}

		};

		if (findViewById(R.id.fragmentContainer) != null) {
			initUI();

			if (LinphoneManager.getLc().getCallsNb() > 0) {
				Call call = LinphoneManager.getLc().getCalls()[0];

				if (LinphoneUtils.isCallEstablished(call)) {
					enableAndRefreshInCallActions();
				}
			}
			if (savedInstanceState != null) {
				// Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
				isSpeakerEnabled = savedInstanceState.getBoolean("Speaker");
				isMicMuted = savedInstanceState.getBoolean("Mic");
				isVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
				if (savedInstanceState.getBoolean("AskingVideo")) {
					showAcceptCallUpdateDialog();
					TimeRemind = savedInstanceState.getLong("TimeRemind");
					createTimerForDialog(TimeRemind);
				}
                if (savedInstanceState.getBoolean("AskingZrtp")) {
                    setisZrtpAsk(savedInstanceState.getBoolean("AskingZrtp"));
                }
				refreshInCallActions();
				return;
			} else {
				isSpeakerEnabled = LinphoneManager.getInstance().isSpeakerEnabled();
				isMicMuted = !LinphoneManager.getLc().micEnabled();
			}

			Fragment callFragment;
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				callFragment = new CallVideoFragment();
				videoCallFragment = (CallVideoFragment) callFragment;
				displayVideoCall(false);
				LinphoneManager.getInstance().routeAudioToSpeaker();
				isSpeakerEnabled = true;
			} else {
				callFragment = new CallAudioFragment();
				audioCallFragment = (CallAudioFragment) callFragment;
			}

			if(BluetoothManager.getInstance().isBluetoothHeadsetAvailable()){
				BluetoothManager.getInstance().routeAudioToBluetooth();
			}

			callFragment.setArguments(getIntent().getExtras());
			getFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();
		}
	}

	public void createTimerForDialog(long time) {
		timer = new CountDownTimer(time , 1000) {
			public void onTick(long millisUntilFinished) {
				TimeRemind = millisUntilFinished;
			}
			public void onFinish() {
				if (dialog != null) {
					dialog.dismiss();
					dialog = null;
				}
				acceptCallUpdate(false);
			}
		}.start();
	}

	private boolean isVideoEnabled(Call call) {
		if(call != null){
			return call.getCurrentParams().videoEnabled();
		}
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("Speaker", LinphoneManager.getInstance().isSpeakerEnabled());
		outState.putBoolean("Mic", !LinphoneManager.getLc().micEnabled());
		outState.putBoolean("VideoCallPaused", isVideoCallPaused);
		outState.putBoolean("AskingVideo", isVideoAsk);
		outState.putLong("TimeRemind", TimeRemind);
        if (status != null) outState.putBoolean("AskingZrtp", getisZrtpAsk());
		if (dialog != null) dialog.dismiss();
		super.onSaveInstanceState(outState);
	}

	private boolean isTablet() {
		return getResources().getBoolean(R.bool.isTablet);
	}

	private void initUI() {
		inflater = LayoutInflater.from(this);
		container = (ViewGroup) findViewById(R.id.topLayout);
		callsList = (LinearLayout) findViewById(R.id.calls_list);
		conferenceList = (LinearLayout) findViewById(R.id.conference_list);

		//TopBar
		video = (ImageView) findViewById(R.id.video);
		video.setOnClickListener(this);
		enabledVideoButton(false);

		videoProgress =  (ProgressBar) findViewById(R.id.video_in_progress);
		videoProgress.setVisibility(View.GONE);

		micro = (ImageView) findViewById(R.id.micro);
		micro.setOnClickListener(this);

		speaker = (ImageView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);

		options = (ImageView) findViewById(R.id.options);
		options.setOnClickListener(this);
		options.setEnabled(false);

		//BottonBar
		hangUp = (ImageView) findViewById(R.id.hang_up);
		hangUp.setOnClickListener(this);

		dialer = (ImageView) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);

		numpad = (Numpad) findViewById(R.id.numpad);
		numpad.getBackground().setAlpha(240);

		chat = (ImageView) findViewById(R.id.chat);
		chat.setOnClickListener(this);
		missedChats = (TextView) findViewById(R.id.missed_chats);

		//Others

		//Active Call
		callInfo = (LinearLayout) findViewById(R.id.active_call_info);

		pause = (ImageView) findViewById(R.id.pause);
		pause.setOnClickListener(this);
		pause2 = (ImageView) findViewById(R.id.pause2);
		pause2.setOnClickListener(this);
		enabledPauseButton(false);

		mActiveCallHeader = (RelativeLayout) findViewById(R.id.active_call);
		mNoCurrentCall = (LinearLayout) findViewById(R.id.no_current_call);
		mCallPaused = (RelativeLayout) findViewById(R.id.remote_pause);

		contactPicture = (ImageView) findViewById(R.id.contact_picture);
		avatar_layout = (RelativeLayout) findViewById(R.id.avatar_layout);
		action_bar = (RelativeLayout) findViewById(R.id.action_menu);

		//Options
		addCall = (ImageView) findViewById(R.id.add_call);
		addCall.setOnClickListener(this);
		addCall.setEnabled(false);

		transfer = (ImageView) findViewById(R.id.transfer);
		transfer.setOnClickListener(this);
		transfer.setEnabled(false);

		conference = (ImageView) findViewById(R.id.conference);
		conference.setEnabled(false);
		conference.setOnClickListener(this);

		try {
			audioRoute = (ImageView) findViewById(R.id.audio_route);
			audioRoute.setOnClickListener(this);
			routeSpeaker = (ImageView) findViewById(R.id.route_speaker);
			routeSpeaker.setOnClickListener(this);
			routeEarpiece = (ImageView) findViewById(R.id.route_earpiece);
			routeEarpiece.setOnClickListener(this);
			routeBluetooth = (ImageView) findViewById(R.id.route_bluetooth);
			routeBluetooth.setOnClickListener(this);
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (1)");
		}

		switchCamera = (ImageView) findViewById(R.id.switchCamera);
		switchCamera.setOnClickListener(this);

		mControlsLayout = (LinearLayout) findViewById(R.id.menu);

		encryption = (ImageView) findViewById(R.id.encryption);
		encryption.setVisibility(View.VISIBLE);

		if (!isTransferAllowed) {
			addCall.setBackgroundResource(R.drawable.options_add_call);
		}

		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			try {
				audioRoute.setVisibility(View.VISIBLE);
				speaker.setVisibility(View.GONE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (2)"); }
		} else {
			try {
				audioRoute.setVisibility(View.GONE);
				speaker.setVisibility(View.VISIBLE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (3)"); }
		}

		createInCallStats();
		LinphoneManager.getInstance().changeStatusToOnThePhone();
	}

	public void checkAndRequestPermission(String permission, int result) {
		int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
		Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
				Log.i("[Permission] Asking for " + permission);
				ActivityCompat.requestPermissions(this, new String[] { permission }, result);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, final int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}

		switch (requestCode) {
			case PERMISSIONS_REQUEST_CAMERA:
				LinphoneUtils.dispatchOnUIThread(new Runnable() {
					@Override
					public void run() {
						acceptCallUpdate(grantResults[0] == PackageManager.PERMISSION_GRANTED);
					}
				});
				break;
			case PERMISSIONS_ENABLED_CAMERA:
				LinphoneUtils.dispatchOnUIThread(new Runnable() {
					@Override
					public void run() {
						disableVideo(grantResults[0] != PackageManager.PERMISSION_GRANTED);
					}
				});
				break;
			case PERMISSIONS_ENABLED_MIC:
				LinphoneUtils.dispatchOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
							toggleMicro();
						}
					}
				});
				break;
		}
	}

	public void createInCallStats() {
		sideMenu = (DrawerLayout) findViewById(R.id.side_menu);
		menu = (ImageView) findViewById(R.id.call_quality);

		sideMenuContent = (RelativeLayout) findViewById(R.id.side_menu_content);

		menu.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (sideMenu.isDrawerVisible(Gravity.LEFT)) {
					sideMenu.closeDrawer(sideMenuContent);
				} else {
					sideMenu.openDrawer(sideMenuContent);
				}
			}
		});

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			initCallStatsRefresher(lc.getCurrentCall(), findViewById(R.id.incall_stats));
		}
	}

	private void refreshIncallUi(){
		refreshInCallActions();
		refreshCallList(getResources());
		enableAndRefreshInCallActions();
		displayMissedChats();
	}

	public void setSpeakerEnabled(boolean enabled){
		isSpeakerEnabled = enabled;
	}

	public void refreshInCallActions() {
		if (!LinphonePreferences.instance().isVideoEnabled() || isConferenceRunning) {
			enabledVideoButton(false);
		} else {
			if(video.isEnabled()) {
				if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
					video.setImageResource(R.drawable.camera_default);
					videoProgress.setVisibility(View.INVISIBLE);
				} else {
					video.setImageResource(R.drawable.camera_disabled);
				}
			} else {
				video.setImageResource(R.drawable.camera_button);
			}
		}
		if (getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			video.setImageResource(R.drawable.camera_button);
		}

		if (isSpeakerEnabled) {
			speaker.setImageResource(R.drawable.speaker);
		} else {
			speaker.setImageResource(R.drawable.speaker_default);
		}

		if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			isMicMuted = true;
		}
		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_selected);
		} else {
			micro.setImageResource(R.drawable.micro_default);
		}

		try {
			routeSpeaker.setImageResource(R.drawable.speaker_default);
			if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
				isSpeakerEnabled = false; // We need this if isSpeakerEnabled wasn't set correctly
				//routeEarpiece.setImageResource(R.drawable.route_earpiece);
				//routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
				routeEarpiece.setImageAlpha(120);
				routeBluetooth.setImageAlpha(255);
				return;
			} else {
				//routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);
				//routeBluetooth.setImageResource(R.drawable.route_bluetooth);
				routeEarpiece.setImageAlpha(255);
				routeBluetooth.setImageAlpha(120);
			}

			if (isSpeakerEnabled) {
				routeSpeaker.setImageResource(R.drawable.route_speaker);
				//routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
				//routeEarpiece.setImageResource(R.drawable.route_earpiece);
				//routeBluetooth.setImageResource(R.drawable.route_bluetooth);
				routeEarpiece.setImageAlpha(120);
				routeBluetooth.setImageAlpha(120);
			}
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (4)");
		}
	}

	private void enableAndRefreshInCallActions() {
		int confsize = 0;

		if(LinphoneManager.getLc().isInConference()) {
			confsize = LinphoneManager.getLc().getConferenceSize() - (LinphoneManager.getLc().getConference() != null ? 1 : 0);
		}

		//Enabled transfer button
		if(isTransferAllowed  && !LinphoneManager.getLc().soundResourcesLocked()) {
			enabledTransferButton(true);
		} else {
			enabledTransferButton(false);
		}

		//Enable conference button
		if(LinphoneManager.getLc().getCallsNb() > 1 && LinphoneManager.getLc().getCallsNb() > confsize && !LinphoneManager.getLc().soundResourcesLocked()) {
			enabledConferenceButton(true);
		} else {
			enabledConferenceButton(false);
		}

		addCall.setEnabled(LinphoneManager.getLc().getCallsNb() < LinphoneManager.getLc().getMaxCalls() && !LinphoneManager.getLc().soundResourcesLocked());
		options.setEnabled(!getResources().getBoolean(R.bool.disable_options_in_call) && (addCall.isEnabled() || transfer.isEnabled()));

		if(LinphoneManager.getLc().getCurrentCall() != null && LinphonePreferences.instance().isVideoEnabled() && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()) {
			enabledVideoButton(true);
		} else {
			enabledVideoButton(false);
		}
		if(LinphoneManager.getLc().getCurrentCall() != null && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()){
			enabledPauseButton(true);
		} else {
			enabledPauseButton(false);
		}
		micro.setEnabled(true);
		if(!isTablet()){
			speaker.setEnabled(true);
		}
		pause.setEnabled(true);
		dialer.setEnabled(true);
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			//displayVideoCallControlsIfHidden();
		}

		if (id == R.id.video) {
			int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
			Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

			if (camera == PackageManager.PERMISSION_GRANTED) {
				disableVideo(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()));
			} else {
				checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_ENABLED_CAMERA);

			}
		}
		else if (id == R.id.micro) {
			int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
			Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

			if (recordAudio == PackageManager.PERMISSION_GRANTED) {
				toggleMicro();
			} else {
				checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_ENABLED_MIC);
			}
		}
		else if (id == R.id.speaker) {
			toggleSpeaker();
		}
		else if (id == R.id.add_call) {
			goBackToDialer();
		}
		else if (id == R.id.pause || id == R.id.pause2) {
			pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
		}
		else if (id == R.id.hang_up) {
			hangUp();
		}
		else if (id == R.id.dialer) {
			hideOrDisplayNumpad();
		}
		else if (id == R.id.chat) {
			goToChatList();
		}
		else if (id == R.id.conference) {
			enterConference();
			hideOrDisplayCallOptions();
		}
		else if (id == R.id.switchCamera) {
			if (videoCallFragment != null) {
				videoCallFragment.switchCamera();
			}
		}
		else if (id == R.id.transfer) {
			goBackToDialerAndDisplayTransferButton();
		}
		else if (id == R.id.options) {
			hideOrDisplayCallOptions();
		}
		else if (id == R.id.audio_route) {
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.route_bluetooth) {
			if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
				isSpeakerEnabled = false;
				/*routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
				routeSpeaker.setImageResource(R.drawable.route_speaker);
				routeEarpiece.setImageResource(R.drawable.route_earpiece);*/
				routeEarpiece.setImageAlpha(120);
				routeBluetooth.setImageAlpha(255);
				routeSpeaker.setImageResource(R.drawable.speaker_default);
			}
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.route_earpiece) {
			LinphoneManager.getInstance().routeAudioToReceiver();
			isSpeakerEnabled = false;
			/*routeBluetooth.setImageResource(R.drawable.route_bluetooth);
			routeSpeaker.setImageResource(R.drawable.route_speaker);
			routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);*/
			routeEarpiece.setImageAlpha(255);
			routeBluetooth.setImageAlpha(120);
			routeSpeaker.setImageResource(R.drawable.speaker_default);
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.route_speaker) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
			/*routeBluetooth.setImageResource(R.drawable.route_bluetooth);
			routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
			routeEarpiece.setImageResource(R.drawable.route_earpiece);*/
			routeEarpiece.setImageAlpha(120);
			routeBluetooth.setImageAlpha(120);
			routeSpeaker.setImageResource(R.drawable.speaker);
			hideOrDisplayAudioRoutes();
		}

		else if (id == R.id.call_pause) {
			Call call = (Call) v.getTag();
			pauseOrResumeCall(call);
		}
		else if (id == R.id.conference_pause) {
			pauseOrResumeConference();
		}
	}

	private void enabledVideoButton(boolean enabled){
		if(enabled) {
			video.setEnabled(true);
		} else {
			video.setEnabled(false);
		}
	}

	private void enabledPauseButton(boolean enabled){
		if(enabled) {
			pause.setEnabled(true);
			pause2.setEnabled(true);
			pause.setImageResource(R.drawable.pause_big_default);
		} else {
			pause.setEnabled(false);
			pause2.setEnabled(false);
			//pause.setImageResource(R.drawable.pause_big_disabled);
		}
	}

	private void enabledTransferButton(boolean enabled){
		if(enabled) {
			transfer.setEnabled(true);
		} else {
			transfer.setEnabled(false);
		}
	}

	private void enabledConferenceButton(boolean enabled){
		if (enabled) {
			conference.setEnabled(true);
			conference.setImageAlpha(255);
		} else {
			conference.setEnabled(false);
			conference.setImageAlpha(102);
		}
	}

	private void disableVideo(final boolean videoDisabled) {
		final Call call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		if (videoDisabled) {
			CallParams params = LinphoneManager.getLc().createCallParams(call);
			params.enableVideo(false);
			LinphoneManager.getLc().updateCall(call, params);
		} else {
			videoProgress.setVisibility(View.VISIBLE);
			if (call.getRemoteParams() != null && !call.getRemoteParams().lowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	public void displayCustomToast(final String message, final int duration) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}

	private void switchVideo(final boolean displayVideo) {
		final Call call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		//Check if the call is not terminated
		if(call.getState() == State.End || call.getState() == State.Released) return;

		if (!displayVideo) {
			showAudioView();
		} else {
			if (!call.getRemoteParams().lowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
				if (videoCallFragment == null || !videoCallFragment.isVisible())
					showVideoView();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	private void showAudioView() {
		if (LinphoneManager.getLc().getCurrentCall() != null) {
			if (!isSpeakerEnabled) {
				LinphoneManager.getInstance().enableProximitySensing(true);
			}
		}
		replaceFragmentVideoByAudio();
		displayAudioCall();
		showStatusBar();
		removeCallbacks();
	}

	private void showVideoView() {
		if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			Log.w("Bluetooth not available, using speaker");
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
		}
		refreshInCallActions();

		LinphoneManager.getInstance().enableProximitySensing(false);

		replaceFragmentAudioByVideo();
		hideStatusBar();
	}

	private void displayNoCurrentCall(boolean display){
		/*if(!display) {
			mActiveCallHeader.setVisibility(View.VISIBLE);
			mNoCurrentCall.setVisibility(View.GONE);
		} else {
			mActiveCallHeader.setVisibility(View.GONE);
			mNoCurrentCall.setVisibility(View.VISIBLE);
		}*/
	}

	private void displayCallPaused(boolean display){
		if(display){
			mCallPaused.setVisibility(View.VISIBLE);
			avatar_layout.setVisibility(View.INVISIBLE);
			action_bar.setVisibility(View.GONE);
		} else {
			mCallPaused.setVisibility(View.GONE);
			avatar_layout.setVisibility(View.VISIBLE);
			action_bar.setVisibility(View.VISIBLE);
		}
	}

	private void displayAudioCall(){
		mControlsLayout.setVisibility(View.VISIBLE);
		mActiveCallHeader.setVisibility(View.VISIBLE);
		callInfo.setVisibility(View.VISIBLE);
		avatar_layout.setVisibility(View.VISIBLE);
		switchCamera.setVisibility(View.GONE);
	}

	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new CallAudioFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, audioCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void replaceFragmentAudioByVideo() {
//		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
		videoCallFragment = new CallVideoFragment();

		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void toggleMicro() {
		Core lc = LinphoneManager.getLc();
		isMicMuted = !isMicMuted;
		lc.enableMic(!isMicMuted);
		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_selected);
		} else {
			micro.setImageResource(R.drawable.micro_default);
		}
	}

	protected void toggleSpeaker() {
		isSpeakerEnabled = !isSpeakerEnabled;
		if (LinphoneManager.getLc().getCurrentCall() != null) {
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()))
				LinphoneManager.getInstance().enableProximitySensing(false);
			else
				LinphoneManager.getInstance().enableProximitySensing(!isSpeakerEnabled);
		}
		if (isSpeakerEnabled) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			speaker.setImageResource(R.drawable.speaker);
			LinphoneManager.getInstance().enableSpeaker(isSpeakerEnabled);
		} else {
			Log.d("Toggle speaker off, routing back to earpiece");
			LinphoneManager.getInstance().routeAudioToReceiver();
			speaker.setImageResource(R.drawable.speaker_default);
		}
	}

	public void pauseOrResumeCall(Call call) {
		Core lc = LinphoneManager.getLc();
		if (call != null && LinphoneManager.getLc().getCurrentCall() == call) {
			lc.pauseCall(call);
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				isVideoCallPaused = true;
			}
			//pause.setImageResource(R.drawable.pause_big_over_selected);
		} else if (call != null) {
			if (call.getState() == State.Paused) {
				call.resume();
				if (isVideoCallPaused) {
					isVideoCallPaused = false;
				}
				pause.setImageResource(R.drawable.pause_big_default);
			}
		} else if (lc.getCallsNb() > 0) {
			for (int cpt = 0 ; cpt < lc.getCallsNb() ; cpt++) {
				if (lc.getCalls()[cpt].getState() == State.Paused) {
					lc.getCalls()[cpt].resume();
					return;
				}
			}
		}
	}

	private void hangUp() {
		Core lc = LinphoneManager.getLc();
		Call currentCall = lc.getCurrentCall();

		if (currentCall != null) {
			lc.terminateCall(currentCall);
		} else if (lc.isInConference()) {
			lc.terminateConference();
		} else {
			lc.terminateAllCalls();
		}
	}

	public void displayVideoCall(boolean display){
		if(display) {
			if (!isSpeakerEnabled) toggleSpeaker();
			showStatusBar();
			mControlsLayout.setVisibility(View.VISIBLE);
			mActiveCallHeader.setVisibility(View.VISIBLE);
			callInfo.setVisibility(View.VISIBLE);
			avatar_layout.setVisibility(View.INVISIBLE);
			callsList.setVisibility(View.VISIBLE);
			if (cameraNumber > 1) {
				switchCamera.setVisibility(View.VISIBLE);
			}
		} else {
			if (isSpeakerEnabled) toggleSpeaker();
			hideStatusBar();
			mControlsLayout.setVisibility(View.GONE);
			mActiveCallHeader.setVisibility(View.GONE);
			switchCamera.setVisibility(View.GONE);
			callsList.setVisibility(View.INVISIBLE);
		}
	}


	public void displayVideoCallControlsIfHidden() {
		if (mControlsLayout != null) {
			if (mControlsLayout.getVisibility() != View.VISIBLE) {
				displayVideoCall(true);
			}
			resetControlsHidingCallBack();
		}
	}

	public void resetControlsHidingCallBack() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
			mControlsHandler.postDelayed(mControls = new Runnable() {
				public void run() {
					hideNumpad();
					video.setEnabled(true);
					transfer.setVisibility(View.INVISIBLE);
					addCall.setVisibility(View.INVISIBLE);
					conference.setVisibility(View.INVISIBLE);
					displayVideoCall(false);
					numpad.setVisibility(View.GONE);
					//options.setImageResource(R.drawable.options_default);
					options.setImageAlpha(255);
				}
			}, SECONDS_BEFORE_HIDING_CONTROLS);
		}
	}

	public void removeCallbacks() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
	}

	private void hideNumpad() {
		if (numpad == null || numpad.getVisibility() != View.VISIBLE) {
			return;
		}

		dialer.setImageResource(R.drawable.footer_dialer);
		numpad.setVisibility(View.GONE);
	}

	private void hideOrDisplayNumpad() {
		if (numpad == null) {
			return;
		}

		if (numpad.getVisibility() == View.VISIBLE) {
			hideNumpad();
		} else {
			dialer.setImageResource(R.drawable.dialer_alt_back);
			numpad.setVisibility(View.VISIBLE);
		}
	}

	private void hideOrDisplayAudioRoutes() {
		if (routeSpeaker.getVisibility() == View.VISIBLE) {
			routeSpeaker.setVisibility(View.INVISIBLE);
			routeBluetooth.setVisibility(View.INVISIBLE);
			routeEarpiece.setVisibility(View.INVISIBLE);
		} else {
			routeSpeaker.setVisibility(View.VISIBLE);
			routeBluetooth.setVisibility(View.VISIBLE);
			routeEarpiece.setVisibility(View.VISIBLE);
		}
	}

	private void hideOrDisplayCallOptions() {
		//Hide options
		if (addCall.getVisibility() == View.VISIBLE) {
			//options.setImageResource(R.drawable.options_disabled);
			options.setImageAlpha(255);
			if (isTransferAllowed) {
				transfer.setVisibility(View.INVISIBLE);
			}
			addCall.setVisibility(View.INVISIBLE);
			conference.setVisibility(View.INVISIBLE);
		} else { //Display options
			if (isTransferAllowed) {
				transfer.setVisibility(View.VISIBLE);
			}
			addCall.setVisibility(View.VISIBLE);
			conference.setVisibility(View.VISIBLE);
			//options.setImageResource(R.drawable.options_default);
			options.setImageAlpha(100);
			transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
		}
	}

	public void goBackToDialer() {
		Intent intent = new Intent();
		intent.setClass(this, LinphoneActivity.class);
		intent.putExtra("AddCall", true);
		startActivity(intent);
	}

	private void goBackToDialerAndDisplayTransferButton() {
		Intent intent = new Intent();
		intent.setClass(this, LinphoneActivity.class);
		intent.putExtra("Transfer", true);
		startActivity(intent);
	}

	private void goToChatList() {
		Intent intent = new Intent();
		intent.setClass(this, LinphoneActivity.class);
		intent.putExtra("GoToChat", true);
		startActivity(intent);
	}

	public void acceptCallUpdate(boolean accept) {
		if (timer != null) {
			timer.cancel();
		}

		Call call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		CallParams params = LinphoneManager.getLc().createCallParams(call);
		if (accept) {
			params.enableVideo(true);
			LinphoneManager.getLc().enableVideoCapture(true);
			LinphoneManager.getLc().enableVideoDisplay(true);
		}

		LinphoneManager.getLc().acceptCallUpdate(call, params);
	}

	public void startIncomingCallActivity() {
		startActivity(new Intent(this, CallIncomingActivity.class));
	}

	public void hideStatusBar() {
		if (isTablet()) {
			return;
		}

		findViewById(R.id.status).setVisibility(View.GONE);
		findViewById(R.id.fragmentContainer).setPadding(0, 0, 0, 0);
	}

	public void showStatusBar() {
		if (isTablet()) {
			return;
		}

		if (status != null && !status.isVisible()) {
			// Hack to ensure statusFragment is visible after coming back to
			// dialer from chat
			status.getView().setVisibility(View.VISIBLE);
		}
		findViewById(R.id.status).setVisibility(View.VISIBLE);
		//findViewById(R.id.fragmentContainer).setPadding(0, LinphoneUtils.pixelsToDpi(getResources(), 40), 0, 0);
	}


	private void showAcceptCallUpdateDialog() {
		dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
		d.setAlpha(200);
		dialog.setContentView(R.layout.dialog);
		dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
		dialog.getWindow().setBackgroundDrawable(d);

		TextView customText = (TextView) dialog.findViewById(R.id.customText);
		customText.setText(getResources().getString(R.string.add_video_dialog));
		Button delete = (Button) dialog.findViewById(R.id.delete_button);
		delete.setText(R.string.accept);
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setText(R.string.decline);
        isVideoAsk = true;

		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
				Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

				if (camera == PackageManager.PERMISSION_GRANTED) {
					CallActivity.instance().acceptCallUpdate(true);
				} else {
					checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_REQUEST_CAMERA);
				}
                isVideoAsk = false;
				dialog.dismiss();
				dialog = null;
			}
		});

		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick (View view){
				if (CallActivity.isInstanciated()) {
					CallActivity.instance().acceptCallUpdate(false);
				}
                isVideoAsk = false;
				dialog.dismiss();
				dialog = null;
			}
		});
		dialog.show();
	}

	@Override
	protected void onResume() {

		instance = this;
		super.onResume();

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
		isSpeakerEnabled = LinphoneManager.getInstance().isSpeakerEnabled();

		refreshIncallUi();
		handleViewIntent();

		if (lc != null && lc.getCurrentCall() != null) {
			refreshStatusItems(lc.getCurrentCall(), lc.getCurrentCall().getCurrentParams().videoEnabled());
		}

        if (lc != null && getisZrtpAsk()) {
            showZRTPDialog(lc.getCurrentCall());
        }

		if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			if (!isSpeakerEnabled) {
				LinphoneManager.getInstance().enableProximitySensing(true);
				removeCallbacks();
			}
		}
	}

	public void showZRTPDialog(final Call call) {
		if(call != null && (ZRTPdialog == null || !ZRTPdialog.isShowing())) {
			String token = call.getAuthenticationToken();

			if (token == null){
				Log.w("Can't display ZRTP popup, no token !");
				return;
			}
			if (token.length()<4){
				Log.w("Can't display ZRTP popup, token is invalid ("+token+")");
				return;
			}

			ZRTPdialog = new Dialog(this);
			ZRTPdialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			ZRTPdialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
			ZRTPdialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
			ZRTPdialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorB));
			d.setAlpha(200);
			ZRTPdialog.setContentView(R.layout.dialog);
			ZRTPdialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			ZRTPdialog.getWindow().setBackgroundDrawable(d);
			String zrtpToRead, zrtpToListen;
			isZrtpAsk = true;

			if (call.getDir().equals(Call.Dir.Incoming)) {
				zrtpToRead = token.substring(0,2);
				zrtpToListen = token.substring(2);
			} else {
				zrtpToListen = token.substring(0,2);
				zrtpToRead = token.substring(2);
			}

			// Obiane specific dev : display sas notif only if screen locked
			KeyguardManager myKM = (KeyguardManager) this.getSystemService(Context.KEYGUARD_SERVICE);
			if( myKM.inKeyguardRestrictedInputMode()) {
				//Screen is locked
				LinphoneService.instance().displaySasNotification(call.getAuthenticationToken());
			}
			TextView customText = (TextView) ZRTPdialog.findViewById(R.id.customText);
			String newText = getString(R.string.zrtp_dialog1).replace("%s", zrtpToRead)
					+ getString(R.string.zrtp_dialog2).replace("%s", zrtpToListen);
			customText.setText(newText);
			Button delete = (Button) ZRTPdialog.findViewById(R.id.delete_button);
			delete.setText(R.string.accept);
			Button cancel = (Button) ZRTPdialog.findViewById(R.id.cancel);
			cancel.setText(R.string.deny);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					call.setAuthenticationTokenVerified(true);
					if (encryption != null) {
						encryption.setImageResource(R.drawable.security_button_default);
					}
					isZrtpAsk = false;
					ZRTPdialog.dismiss();
					LinphoneService.instance().removeSasNotification();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					if (call != null) {
						call.setAuthenticationTokenVerified(false);
						if (encryption != null) {
							encryption.setImageResource(R.drawable.security_button1_over);
						}
					}
					isZrtpAsk = false;
					ZRTPdialog.dismiss();
					LinphoneService.instance().removeSasNotification();
				}
			});
			ZRTPdialog.show();
		}
	}

	public boolean getisZrtpAsk() {
		return isZrtpAsk;
	}

	public void setisZrtpAsk(boolean bool) {
		isZrtpAsk = bool;
	}

	public void refreshStatusItems(final Call call, boolean isVideoEnabled) {
		if (call != null) {
			MediaEncryption mediaEncryption = call.getCurrentParams().getMediaEncryption();

			if (isVideoEnabled) {
				//background.setVisibility(View.GONE);
			} else {
				//background.setVisibility(View.VISIBLE);
			}

			ZrtpPeerStatus zrtpStatus = getZrtpStatus(LinphoneManager.getLc(), call.getRemoteAddress().asStringUriOnly());
			if (zrtpStatus == ZrtpPeerStatus.Valid) {
				encryption.setImageResource(R.drawable.security_button_default);
				contactPicture.setImageResource(R.drawable.avatar_big_secure2);
			} else if (zrtpStatus == ZrtpPeerStatus.Invalid) {
				encryption.setImageResource(R.drawable.security_button1_over);
				contactPicture.setImageResource(R.drawable.avatar_big_unsecure);
			} else {
				encryption.setImageResource(R.drawable.security_button1_default);
				contactPicture.setImageResource(R.drawable.avatar_big_secure1);
			}

			if (mediaEncryption == MediaEncryption.ZRTP) {
				encryption.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						showZRTPDialog(call);
					}
				});
			} else {
				encryption.setOnClickListener(null);
			}
		}
	}

	private void handleViewIntent() {
		Intent intent = getIntent();
		if(intent != null && intent.getAction() == "android.intent.action.VIEW") {
			Call call = LinphoneManager.getLc().getCurrentCall();
			if(call != null && isVideoEnabled(call)) {
				Player player = call.getPlayer();
				String path = intent.getData().getPath();
				Log.i("Openning " + path);
				/*int openRes = */player.open(path);
				/*if(openRes == -1) {
					String message = "Could not open " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
					return;
				}*/
				Log.i("Start playing");
				/*if(*/player.start()/* == -1) {*/;
					/*player.close();
					String message = "Could not start playing " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}*/
			}
		}
	}

	@Override
	protected void onPause() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		super.onPause();

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
	}

	@Override
	protected void onDestroy() {
		LinphoneManager.getInstance().changeStatusToOnline();
		LinphoneManager.getInstance().enableProximitySensing(false);

		unregisterReceiver(headsetReceiver);

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		mControlsHandler = null;

		unbindDrawables(findViewById(R.id.topLayout));
		if (mTimer != null) {
			mTimer.cancel();
		}
		instance = null;
		super.onDestroy();
		System.gc();
	}

	private void unbindDrawables(View view) {
		if (view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ImageView) {
			view.setOnClickListener(null);
		}
		if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			((ViewGroup) view).removeAllViews();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}

	@Override // Never invoke actually
	public void onBackPressed() {
		if (dialog != null) {
			acceptCallUpdate(false);
			dialog.dismiss();
			dialog = null;
		}
		return;
	}

	public void bindAudioFragment(CallAudioFragment fragment) {
		audioCallFragment = fragment;
	}

	public void bindVideoFragment(CallVideoFragment fragment) {
		videoCallFragment = fragment;
	}


	//CALL INFORMATION
	private void displayCurrentCall(Call call){
		Address lAddress = call.getRemoteAddress();
		TextView contactName = (TextView) findViewById(R.id.current_contact_name);
		setContactInformation(contactName, contactPicture, lAddress);
		registerCallDurationTimer(null, call);
	}

	private void displayPausedCalls(Resources resources, final Call call, int index) {
		// Control Row
		LinearLayout callView;

		if(call == null) {
			callView = (LinearLayout) inflater.inflate(R.layout.conference_paused_row, container, false);
			callView.setId(index + 1);
			callView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					pauseOrResumeConference();
				}
			});
		} else {
			callView = (LinearLayout) inflater.inflate(R.layout.call_inactive_row, container, false);
			callView.setId(index+1);

			TextView contactName = (TextView) callView.findViewById(R.id.contact_name);
			ImageView contactImage = (ImageView) callView.findViewById(R.id.contact_picture);
			Address lAddress = call.getRemoteAddress();
			setContactInformation(contactName, contactImage, lAddress);
			displayCallStatusIconAndReturnCallPaused(callView, call);
			registerCallDurationTimer(callView, call);
		}
		callsList.addView(callView);
	}

	private void setContactInformation(TextView contactName, ImageView contactPicture,  Address lAddress) {
		LinphoneContact lContact  = ContactsManager.getInstance().findContactFromAddress(lAddress);
		if (lContact == null) {
			contactName.setText(LinphoneUtils.getAddressDisplayName(lAddress));
		} else {
			contactName.setText(lContact.getFullName());
			//LinphoneUtils.setImagePictureFromUri(contactPicture.getContext(), contactPicture, lContact.getPhotoUri(), lContact.getThumbnailUri());
		}

		//Obiane spec
		ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
		Address ourUri = (prx != null) ? prx.getIdentityAddress() : null;
		ChatRoomSecurityLevel securityLevel = getSecurityLevelForSipUri(LinphoneManager.getLc(), ourUri, lAddress);
		if (securityLevel == ChatRoomSecurityLevel.Safe) {
			contactPicture.setImageResource(R.drawable.avatar_big_secure2);
		} else if (securityLevel == ChatRoomSecurityLevel.Unsafe) {
			contactPicture.setImageResource(R.drawable.avatar_big_unsecure);
		} else if (securityLevel == ChatRoomSecurityLevel.Encrypted) {
			contactPicture.setImageResource(R.drawable.avatar_big_secure1);
		} else {
			ZrtpPeerStatus zrtpStatus = getZrtpStatus(LinphoneManager.getLc(), lAddress.asStringUriOnly());
			if (zrtpStatus == ZrtpPeerStatus.Valid) {
				contactPicture.setImageResource(R.drawable.avatar_medium_secure2);
			} else if (zrtpStatus == ZrtpPeerStatus.Invalid) {
				contactPicture.setImageResource(R.drawable.avatar_medium_unsecure);
			} else {
				if (!ContactsManager.getInstance().isContactPresenceDisabled() && lContact != null && lContact.getFriend() != null) {
					PresenceModel presenceModel = lContact.getFriend().getPresenceModel();
					if (presenceModel != null) {
						contactPicture.setImageResource(R.drawable.avatar_medium_secure1);
					} else {
						contactPicture.setImageResource(R.drawable.avatar_medium_unregistered);
					}
				} else {
					contactPicture.setImageResource(R.drawable.avatar_medium_unregistered);
				}
			}
		}
	}

	private boolean displayCallStatusIconAndReturnCallPaused(LinearLayout callView, Call call) {
		boolean isCallPaused, isInConference;
		ImageView onCallStateChanged = (ImageView) callView.findViewById(R.id.call_pause);
		onCallStateChanged.setTag(call);
		onCallStateChanged.setOnClickListener(this);

		if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
			onCallStateChanged.setImageResource(R.drawable.pause);
			isCallPaused = true;
			isInConference = false;
		} else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
			isCallPaused = false;
			isInConference = false;
		} else {
			isInConference = isConferenceRunning && call.getConference() != null;
			isCallPaused = false;
		}

		return isCallPaused || isInConference;
	}

	private void registerCallDurationTimer(View v, Call call) {
		int callDuration = call.getDuration();
		if (callDuration == 0 && call.getState() != State.StreamsRunning) {
			return;
		}

		Chronometer timer;
		if(v == null){
			timer = (Chronometer) findViewById(R.id.current_call_timer);
		} else {
			timer = (Chronometer) v.findViewById(R.id.call_timer);
		}

		if (timer == null) {
			throw new IllegalArgumentException("no callee_duration view found");
		}

		timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
		timer.start();
	}

	public void refreshCallList(Resources resources) {
		isConferenceRunning = LinphoneManager.getLc().isInConference();
		List<Call> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.PausedByRemote));

		//MultiCalls
		if(LinphoneManager.getLc().getCallsNb() > 1){
			callsList.setVisibility(View.VISIBLE);
		}

		//Active call
		if(LinphoneManager.getLc().getCurrentCall() != null) {
			displayNoCurrentCall(false);
			if(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && !isConferenceRunning && pausedCalls.size() == 0) {
				displayVideoCall(false);
			} else {
				displayAudioCall();
			}
		} else {
			showAudioView();
			displayNoCurrentCall(true);
			if(LinphoneManager.getLc().getCallsNb() == 1) {
				callsList.setVisibility(View.VISIBLE);
			}
		}

		//Conference
		if (isConferenceRunning) {
			displayConference(true);
		} else {
			displayConference(false);
		}

		if(callsList != null) {
			callsList.removeAllViews();
			int index = 0;

			if (LinphoneManager.getLc().getCallsNb() == 0) {
				goBackToDialer();
				return;
			}

			boolean isConfPaused = false;
			for (Call call : LinphoneManager.getLc().getCalls()) {
				if (call.getConference() != null && !isConferenceRunning) {
					isConfPaused = true;
					index++;
				} else {
					if (call != LinphoneManager.getLc().getCurrentCall() && !(call.getConference() != null)) {
						displayPausedCalls(resources, call, index);
						index++;
					} else {
						displayCurrentCall(call);
					}
				}
			}

			if (!isConferenceRunning) {
				if (isConfPaused) {
					callsList.setVisibility(View.VISIBLE);
					displayPausedCalls(resources, null, index);
				}

			}

		}

		//Paused by remote
		if (pausedCalls.size() == 1) {
			displayCallPaused(true);
		} else {
			displayCallPaused(false);
		}
	}

	//Conference
	private void exitConference(final Call call){
		Core lc = LinphoneManager.getLc();

		if (lc.isInConference()) {
			lc.removeFromConference(call);
			if (lc.getConferenceSize() <= 1) {
				lc.leaveConference();
			}
		}
		refreshIncallUi();
	}

	private void enterConference() {
		LinphoneManager.getLc().addAllToConference();
	}

	public void pauseOrResumeConference() {
		Core lc = LinphoneManager.getLc();
		conferenceStatus = (ImageView) findViewById(R.id.conference_pause);
		if(conferenceStatus != null) {
			if (lc.isInConference()) {
				//conferenceStatus.setImageResource(R.drawable.pause_big_over_selected);
				lc.leaveConference();
			} else {
				conferenceStatus.setImageResource(R.drawable.pause_big_default);
				lc.enterConference();
			}
		}
		refreshCallList(getResources());
	}

	private void displayConferenceParticipant(int index, final Call call){
		LinearLayout confView = (LinearLayout) inflater.inflate(R.layout.conf_call_control_row, container, false);
		conferenceList.setId(index + 1);
		TextView contact = (TextView) confView.findViewById(R.id.contactNameOrNumber);
		ImageView picture = confView.findViewById(R.id.contactPicture);

		Address lAddress = call.getRemoteAddress();
		LinphoneContact lContact  = ContactsManager.getInstance().findContactFromAddress(call.getRemoteAddress());
		if (lContact == null) {
			contact.setText(call.getRemoteAddress().getUsername());
		} else {
			contact.setText(lContact.getFullName());
		}

		//Obiane spec
		ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
		Address ourUri = (prx != null) ? prx.getIdentityAddress() : null;
		ChatRoomSecurityLevel securityLevel = getSecurityLevelForSipUri(LinphoneManager.getLc(), ourUri, lAddress);
		if (securityLevel == ChatRoomSecurityLevel.Safe) {
			picture.setImageResource(R.drawable.avatar_big_secure2);
		} else if (securityLevel == ChatRoomSecurityLevel.Unsafe) {
			picture.setImageResource(R.drawable.avatar_big_unsecure);
		} else if (securityLevel == ChatRoomSecurityLevel.Encrypted) {
			picture.setImageResource(R.drawable.avatar_big_secure1);
		} else {
			ZrtpPeerStatus zrtpStatus = getZrtpStatus(LinphoneManager.getLc(), lAddress.asStringUriOnly());
			if (zrtpStatus == ZrtpPeerStatus.Valid) {
				picture.setImageResource(R.drawable.avatar_medium_secure2);
			} else if (zrtpStatus == ZrtpPeerStatus.Invalid) {
				picture.setImageResource(R.drawable.avatar_medium_unsecure);
			} else {
				if (!ContactsManager.getInstance().isContactPresenceDisabled() && lContact != null && lContact.getFriend() != null) {
					PresenceModel presenceModel = lContact.getFriend().getPresenceModel();
					if (presenceModel != null) {
						picture.setImageResource(R.drawable.avatar_medium_secure1);
					} else {
						picture.setImageResource(R.drawable.avatar_medium_unregistered);
					}
				} else {
					picture.setImageResource(R.drawable.avatar_medium_unregistered);
				}
			}
		}

		registerCallDurationTimer(confView, call);

		ImageView quitConference = (ImageView) confView.findViewById(R.id.quitConference);
		quitConference.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				exitConference(call);
			}
		});
		conferenceList.addView(confView);

	}

	private void displayConferenceHeader(){
		conferenceList.setVisibility(View.VISIBLE);
		RelativeLayout headerConference = (RelativeLayout) inflater.inflate(R.layout.conference_header, container, false);
		conferenceStatus = (ImageView) headerConference.findViewById(R.id.conference_pause);
		conferenceStatus.setOnClickListener(this);
		conferenceList.addView(headerConference);

	}

	private void displayConference(boolean isInConf){
		if(isInConf) {
			mControlsLayout.setVisibility(View.VISIBLE);
			mActiveCallHeader.setVisibility(View.GONE);
			mNoCurrentCall.setVisibility(View.GONE);
			conferenceList.removeAllViews();

			//Conference Header
			displayConferenceHeader();

			//Conference participant
			int index = 1;
			for (Call call : LinphoneManager.getLc().getCalls()) {
				if (call.getConference() != null) {
					displayConferenceParticipant(index, call);
					index++;
				}
			}
			conferenceList.setVisibility(View.VISIBLE);
		} else {
			conferenceList.setVisibility(View.GONE);
		}
	}

	private void displayMissedChats() {
		int count = LinphoneManager.getInstance().getUnreadMessageCount();

		if (count > 0) {
			missedChats.setText(count + "");
			missedChats.setVisibility(View.VISIBLE);
		} else {
			missedChats.clearAnimation();
			missedChats.setVisibility(View.GONE);
		}
	}

	@SuppressWarnings("deprecation")
	private void formatText(TextView tv, String name, String value) {
		tv.setText(Html.fromHtml("<b>" + name + " </b>" + value));
	}

	private String getEncoderText(String mime){
		String ret = mEncoderTexts.get(mime);
		if (ret == null){
			org.linphone.mediastream.Factory msfactory = LinphoneManager.getLc().getMediastreamerFactory();
			ret = msfactory.getEncoderText(mime);
			mEncoderTexts.put(mime, ret);
		}
		return ret;
	}
	private String getDecoderText(String mime){
		String ret = mDecoderTexts.get(mime);
		if (ret == null){
			org.linphone.mediastream.Factory msfactory = LinphoneManager.getLc().getMediastreamerFactory();
			ret = msfactory.getDecoderText(mime);
			mDecoderTexts.put(mime, ret);
		}
		return ret;
	}

	private void displayMediaStats(CallParams params, CallStats stats
			, PayloadType media , View layout, TextView title, TextView codec, TextView dl
			, TextView ul, TextView edl, TextView ice, TextView ip, TextView senderLossRate
			, TextView receiverLossRate, TextView enc, TextView dec, TextView videoResolutionSent
			, TextView videoResolutionReceived, TextView videoFpsSent, TextView videoFpsReceived
			, boolean isVideo, TextView jitterBuffer) {
		if (stats != null) {
			String mime = null;

			layout.setVisibility(View.VISIBLE);
			title.setVisibility(TextView.VISIBLE);
			if (media != null) {
				mime = media.getMimeType();
				formatText(codec, getString(R.string.call_stats_codec),
						mime + " / " + (media.getClockRate() / 1000) + "kHz");
			}
			if (mime != null ){
				formatText(enc, getString(R.string.call_stats_encoder_name), getEncoderText(mime));
				formatText(dec, getString(R.string.call_stats_decoder_name), getDecoderText(mime));
			}
			formatText(dl, getString(R.string.call_stats_download),
					String.valueOf((int) stats.getDownloadBandwidth()) + " kbits/s");
			formatText(ul, getString(R.string.call_stats_upload),
					String.valueOf((int) stats.getUploadBandwidth()) + " kbits/s");
			if (isVideo) {
				formatText(edl, getString(R.string.call_stats_estimated_download),
						String.valueOf(stats.getEstimatedDownloadBandwidth()) + " kbits/s");
			}
			formatText(ice, getString(R.string.call_stats_ice),
					stats.getIceState().toString());
			formatText(ip, getString(R.string.call_stats_ip),
					(stats.getIpFamilyOfRemote() == AddressFamily.Inet6) ?
							"IpV6" : (stats.getIpFamilyOfRemote() == AddressFamily.Inet) ?
							"IpV4" : "Unknown");
			formatText(senderLossRate, getString(R.string.call_stats_sender_loss_rate),
					new DecimalFormat("##.##").format(stats.getSenderLossRate()) + "%");
			formatText(receiverLossRate, getString(R.string.call_stats_receiver_loss_rate),
					new DecimalFormat("##.##").format(stats.getReceiverLossRate())+ "%");
			if (isVideo) {
				formatText(videoResolutionSent,
						getString(R.string.call_stats_video_resolution_sent),
						"\u2191 " + params.getSentVideoDefinition().getName());
				formatText(videoResolutionReceived,
						getString(R.string.call_stats_video_resolution_received),
						"\u2193 " + params.getReceivedVideoDefinition().getName());
				formatText(videoFpsSent,
						getString(R.string.call_stats_video_fps_sent),
						"\u2191 " + params.getSentFramerate());
				formatText(videoFpsReceived,
						getString(R.string.call_stats_video_fps_received),
						"\u2193 " + params.getReceivedFramerate());
			} else {
				formatText(jitterBuffer, getString(R.string.call_stats_jitter_buffer),
						new DecimalFormat("##.##").format(stats.getJitterBufferSizeMs()) + " ms");
			}
		} else {
			layout.setVisibility(View.GONE);
			title.setVisibility(TextView.GONE);
		}
	}

	public void initCallStatsRefresher(final Call call, final View view) {
		if (mCallDisplayedInStats == call) return;

		if (mTimer != null && mTask != null) {
			mTimer.cancel();
			mTimer = null;
			mTask = null;
		}
		mCallDisplayedInStats = call;

		if (call == null) return;

		final TextView titleAudio = (TextView) view.findViewById(R.id.call_stats_audio);
		final TextView titleVideo = (TextView) view.findViewById(R.id.call_stats_video);
		final TextView codecAudio = (TextView) view.findViewById(R.id.codec_audio);
		final TextView codecVideo = (TextView) view.findViewById(R.id.codec_video);
		final TextView encoderAudio = (TextView) view.findViewById(R.id.encoder_audio);
		final TextView decoderAudio = (TextView) view.findViewById(R.id.decoder_audio);
		final TextView encoderVideo = (TextView) view.findViewById(R.id.encoder_video);
		final TextView decoderVideo = (TextView) view.findViewById(R.id.decoder_video);
		final TextView dlAudio = (TextView) view.findViewById(R.id.downloadBandwith_audio);
		final TextView ulAudio = (TextView) view.findViewById(R.id.uploadBandwith_audio);
		final TextView dlVideo = (TextView) view.findViewById(R.id.downloadBandwith_video);
		final TextView ulVideo = (TextView) view.findViewById(R.id.uploadBandwith_video);
		final TextView edlVideo = (TextView) view.findViewById(R.id.estimatedDownloadBandwidth_video);
		final TextView iceAudio = (TextView) view.findViewById(R.id.ice_audio);
		final TextView iceVideo = (TextView) view.findViewById(R.id.ice_video);
		final TextView videoResolutionSent = (TextView) view.findViewById(R.id.video_resolution_sent);
		final TextView videoResolutionReceived = (TextView) view.findViewById(R.id.video_resolution_received);
		final TextView videoFpsSent = (TextView) view.findViewById(R.id.video_fps_sent);
		final TextView videoFpsReceived = (TextView) view.findViewById(R.id.video_fps_received);
		final TextView senderLossRateAudio = (TextView) view.findViewById(R.id.senderLossRateAudio);
		final TextView receiverLossRateAudio = (TextView) view.findViewById(R.id.receiverLossRateAudio);
		final TextView senderLossRateVideo = (TextView) view.findViewById(R.id.senderLossRateVideo);
		final TextView receiverLossRateVideo = (TextView) view.findViewById(R.id.receiverLossRateVideo);
		final TextView ipAudio = (TextView) view.findViewById(R.id.ip_audio);
		final TextView ipVideo = (TextView) view.findViewById(R.id.ip_video);
		final TextView jitterBufferAudio = (TextView) view.findViewById(R.id.jitterBufferAudio);
		final View videoLayout = view.findViewById(R.id.callStatsVideo);
		final View audioLayout = view.findViewById(R.id.callStatsAudio);

		mCallListener = new CallListenerStub(){
			public void onStateChanged(Call call, Call.State cstate, String message){
				if (cstate == Call.State.End || cstate == Call.State.Error){
					if (mTimer != null) {
						Log.i("Call is terminated, stopping timer in charge of stats refreshing.");
						mTimer.cancel();
					}
				}
			}
		};

	 	mTimer = new Timer();
		mTask = new TimerTask() {
			@Override
			public void run() {
				if (call == null) {
					mTimer.cancel();
					return;
				}

				if (titleAudio == null || codecAudio == null || dlVideo == null || edlVideo == null || iceAudio == null
						|| videoResolutionSent == null || videoLayout == null || titleVideo == null
						|| ipVideo == null || ipAudio == null || codecVideo == null
						|| dlAudio == null || ulAudio == null || ulVideo == null || iceVideo == null
						|| videoResolutionReceived == null) {
					mTimer.cancel();
					return;
				}

				mHandler.post(new Runnable() {
					@Override
					public void run() {
						if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() == null) return;
						synchronized(LinphoneManager.getLc()) {
							if (LinphoneActivity.isInstanciated() && call.getState() != Call.State.Released) {
								CallParams params = call.getCurrentParams();
								if (params != null) {
									CallStats audioStats = call.getStats(StreamType.Audio);
									CallStats videoStats = null;

									if (params.videoEnabled())
										videoStats = call.getStats(StreamType.Video);

									PayloadType payloadAudio = params.getUsedAudioPayloadType();
									PayloadType payloadVideo = params.getUsedVideoPayloadType();

									displayMediaStats(params, audioStats, payloadAudio, audioLayout
											, titleAudio, codecAudio, dlAudio, ulAudio, null, iceAudio
											, ipAudio, senderLossRateAudio, receiverLossRateAudio
											, encoderAudio, decoderAudio, null, null, null, null
											, false, jitterBufferAudio);

									displayMediaStats(params, videoStats, payloadVideo, videoLayout
											, titleVideo, codecVideo, dlVideo, ulVideo, edlVideo, iceVideo
											, ipVideo, senderLossRateVideo, receiverLossRateVideo
											, encoderVideo, decoderVideo
											, videoResolutionSent, videoResolutionReceived
											,videoFpsSent, videoFpsReceived
											, true, null);
								}
							}
						}
					}
				});
			}
		};
		call.addListener(mCallListener);
		mTimer.scheduleAtFixedRate(mTask, 0, 1000);
	}

	////Earset Connectivity Broadcast innerClass
	public class HeadsetReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
				if (intent.hasExtra("state")) {
					switch (intent.getIntExtra("state", 0)) {
						case 0:
							if (oldIsSpeakerEnabled) {
								LinphoneManager.getInstance().routeAudioToSpeaker();
								isSpeakerEnabled = true;
								speaker.setEnabled(true);
							}
							break;
						case 1:
							LinphoneManager.getInstance().routeAudioToReceiver();
							oldIsSpeakerEnabled = isSpeakerEnabled;
							isSpeakerEnabled = false;
							speaker.setEnabled(false);
							break;
					}
					refreshInCallActions();
				}
			}
		}
	}
}
