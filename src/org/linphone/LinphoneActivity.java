package org.linphone;
/*
LinphoneActivity.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
import static android.content.Intent.ACTION_MAIN;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceived;
import org.linphone.LinphoneSimpleListener.LinphoneOnRegistrationStateChangedListener;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCallLog.CallStatus;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.Log;
import org.linphone.setup.SetupActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.Fragment.SavedState;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class LinphoneActivity extends FragmentActivity implements OnClickListener, ContactPicked, 
										LinphoneOnCallStateChangedListener, 
										LinphoneOnMessageReceived,
										LinphoneOnRegistrationStateChangedListener {
    public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static final int SETTINGS_ACTIVITY = 123;
    private static final int FIRST_LOGIN_ACTIVITY = 101;
	private static final int callActivity = 19;
	private static LinphoneActivity instance;
	private StatusFragment statusFragment;
	private TextView missedCalls;
	private ImageView history, contacts, dialer, settings, chat;
	private FragmentsAvailable currentFragment;
	private Fragment dialerFragment, messageListenerFragment;
	private SavedState dialerSavedState;
	private ChatStorage chatStorage;
	private Handler mHandler = new Handler();
	private boolean isInCallLayout = false;
	
	static final boolean isInstanciated() {
		return instance != null;
	}
	
	public static final LinphoneActivity instance() {
		if (instance != null) return instance;
		throw new RuntimeException("LinphoneActivity not instantiated yet");
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        
        if (!LinphoneManager.isInstanciated()) {
			Log.e("No service running: avoid crash by starting the launcher", this.getClass().getName());
			// super.onCreate called earlier
			finish();
			startActivity(getIntent().setClass(this, LinphoneLauncherActivity.class));
			return;
		}
        
        boolean useFirstLoginActivity = getResources().getBoolean(R.bool.useFirstLoginActivity);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (useFirstLoginActivity && !pref.getBoolean(getString(R.string.first_launch_suceeded_once_key), false)) {
			startActivityForResult(new Intent().setClass(this, SetupActivity.class), FIRST_LOGIN_ACTIVITY);
		}

        setContentView(R.layout.main);
        initButtons();
        
        if (LinphoneManager.isInstanciated()) {
			LinphoneManager.addListener(this);
		}
        
        if (findViewById(R.id.fragmentContainer) != null) {
            if (savedInstanceState != null) {
                return;
            }
            
            dialerFragment = new DialerFragment();
            dialerFragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, dialerFragment).commit();
            currentFragment = FragmentsAvailable.DIALER;
        }
        
        int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
		displayMissedCalls(missedCalls);
		
        instance = this;    
	}
	
	private void initButtons() {
		history = (ImageView) findViewById(R.id.history);
        history.setOnClickListener(this);
        contacts  = (ImageView) findViewById(R.id.contacts);
        contacts.setOnClickListener(this);
        dialer = (ImageView) findViewById(R.id.dialer);
        dialer.setOnClickListener(this);
		dialer.setSelected(true);
        settings = (ImageView) findViewById(R.id.settings);
        settings.setOnClickListener(this);
        chat = (ImageView) findViewById(R.id.chat);
		chat.setOnClickListener(this);
		missedCalls = (TextView) findViewById(R.id.missedCalls);
	}
	
	private void changeCurrentFragment(FragmentsAvailable newFragmentType, Bundle extras) {
		if (newFragmentType == currentFragment) {
			return;
		}
		
		if (currentFragment == FragmentsAvailable.DIALER) {
			dialerSavedState = getSupportFragmentManager().saveFragmentInstanceState(dialerFragment);
		}
		
		Fragment newFragment = null;
		
		switch (newFragmentType) {
		case HISTORY:
			newFragment = new HistoryFragment();
			break;
		case HISTORY_DETAIL:
			newFragment = new HistoryDetailFragment();
			break;
		case CONTACTS:
			newFragment = new ContactsFragment();
			break;
		case CONTACT:
			newFragment = new ContactFragment();
			break;
		case DIALER:
			newFragment = new DialerFragment();
			if (extras == null) {
				newFragment.setInitialSavedState(dialerSavedState);
			}
			dialerFragment = newFragment;
			break;
		case SETTINGS:
			break;
		case CHAT:
			newFragment = new ChatFragment();
			messageListenerFragment = newFragment;
			break;
		case CHATLIST:
			newFragment = new ChatListFragment();
			break;
		}
		
		if (newFragment != null) {
			newFragment.setArguments(extras);
			changeFragment(newFragment, newFragmentType);
		}
	}
	
	private void changeFragment(Fragment newFragment, FragmentsAvailable newFragmentType) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

		if (currentFragment.shouldAddToBackStack()) {
			if (!getResources().getBoolean(R.bool.disable_animations) && currentFragment.shouldAnimate()) {
				if (newFragmentType.isRightOf(currentFragment)) {
					transaction.setCustomAnimations(R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left, R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right);
				} else {
					transaction.setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right, R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left);
				}
			}
			transaction.addToBackStack("Add to back stack");
		}
		else {
			try {
				getSupportFragmentManager().popBackStack("Add to back stack", FragmentManager.POP_BACK_STACK_INCLUSIVE);
			} catch (java.lang.IllegalStateException e) {
				
			}
		}
		transaction.replace(R.id.fragmentContainer, newFragment);

		transaction.commitAllowingStateLoss();
		currentFragment = newFragmentType;
	}
	
	public void displayHistoryDetail(String sipUri, LinphoneCallLog log) {
		LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, getContentResolver());
		
		Bundle extras = new Bundle();
		extras.putString("SipUri", sipUri);
		if (lAddress.getDisplayName() != null) {
			extras.putString("DisplayName", lAddress.getDisplayName());
			extras.putString("PictureUri", uri.toString());
		}
		
		String status;
		if (log.getDirection() == CallDirection.Outgoing) {
			status = "Outgoing";
		} else {
			if (log.getStatus() == CallStatus.Missed) {
				status = "Missed";
			} else {
				status = "Incoming";
			}
		}
		extras.putString("CallStatus", status);
		extras.putString("CallTime", secondsToDisplayableString(log.getCallDuration()));
		extras.putString("CallDate", log.getStartDate());
		
		changeCurrentFragment(FragmentsAvailable.HISTORY_DETAIL, extras);
	}
	
	private String secondsToDisplayableString(int secs) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		cal.set(0, 0, 0, 0, 0, secs);
		return dateFormat.format(cal.getTime());
	}
	
	public void displayContact(Contact contact) {
		Bundle extras = new Bundle();
		extras.putSerializable("Contact", contact);
		changeCurrentFragment(FragmentsAvailable.CONTACT, extras);
	}
	
	public void displayChat(String sipUri) {
		LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, getContentResolver());
		
		Bundle extras = new Bundle();
		extras.putString("SipUri", sipUri);
		if (lAddress.getDisplayName() != null) {
			extras.putString("DisplayName", lAddress.getDisplayName());
			extras.putString("PictureUri", uri.toString());
		}
		changeCurrentFragment(FragmentsAvailable.CHAT, extras);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		resetSelection();
		
		if (id == R.id.history) {
			changeCurrentFragment(FragmentsAvailable.HISTORY, null);
			history.setSelected(true);
			LinphoneManager.getLc().resetMissedCallsCount();
			displayMissedCalls(0);
		}
		else if (id == R.id.contacts) {
			changeCurrentFragment(FragmentsAvailable.CONTACTS, null);
			contacts.setSelected(true);
		}
		else if (id == R.id.dialer) {
			changeCurrentFragment(FragmentsAvailable.DIALER, null);
			dialer.setSelected(true);
		}
		else if (id == R.id.settings) {
//			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
//				changeCurrentFragment(FragmentsAvailable.SETTINGS, null);
//				settings.setSelected(true);
//			} else {
				Intent intent = new Intent(ACTION_MAIN);
				intent.setClass(this, PreferencesActivity.class);
				startActivityForResult(intent, SETTINGS_ACTIVITY);
				if (FragmentsAvailable.SETTINGS.isRightOf(currentFragment)) {
					Compatibility.overridePendingTransition(this, R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left);
				} else {
					Compatibility.overridePendingTransition(this, R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right);
				}
//			}
		}
		else if (id == R.id.chat) {
			changeCurrentFragment(FragmentsAvailable.CHATLIST, null);
			chat.setSelected(true);
		}
	}
	
	private void resetSelection() {
		history.setSelected(false);
		contacts.setSelected(false);
		dialer.setSelected(false);
		settings.setSelected(false);
		chat.setSelected(false);
	}
	
	public void selectMenu(FragmentsAvailable menuToSelect) {
		currentFragment = menuToSelect;
		resetSelection();
		
		switch (menuToSelect) {
		case HISTORY:
		case HISTORY_DETAIL:
			history.setSelected(true);
			break;
		case CONTACTS:
		case CONTACT:
			contacts.setSelected(true);
			break;
		case DIALER:
			dialer.setSelected(true);
			break;
		case SETTINGS:
			settings.setSelected(true);
			break;
		case CHAT:
		case CHATLIST:
			chat.setSelected(true);
			break;
		}
	}

	public void updateDialerFragment(DialerFragment fragment) {
		dialerFragment = fragment;
		// Hack to maintain ADJUST_PAN flag
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
	}
	
	public void updateChatFragment(ChatFragment fragment) {
		messageListenerFragment = fragment;
		// Hack to maintain ADJUST_PAN flag
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
	}
	
	public void updateStatusFragment(StatusFragment fragment) {
		statusFragment = fragment;
		
		if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
			statusFragment.registrationStateChanged(LinphoneManager.getLc().getDefaultProxyConfig().getState());
		}
	}
	
	public StatusFragment getStatusFragment() {
		return statusFragment;
	}

	public ArrayList<String> getChatList() {
		if (chatStorage != null) {
			return chatStorage.getChatList();
		}
		return null;
	}
	
	public List<ChatMessage> getChatMessages(String correspondent) {
		if (chatStorage == null) {
			chatStorage = new ChatStorage(this);
		}
		
		return chatStorage.getMessages(correspondent);
	}
	
	public void removeFromChatList(String sipUri) {
		if (chatStorage == null) {
			chatStorage = new ChatStorage(this);
		}
		
		chatStorage.removeDiscussion(sipUri);
	}
	
	@Override
	public void onMessageReceived(LinphoneAddress from, String message) {
		if (chatStorage == null) {
			chatStorage = new ChatStorage(this);
		}
		
		chatStorage.saveMessage(from.asStringUriOnly(), "", message);
		
		Log.d("Message received from " + from + ": " + message);
		if (messageListenerFragment != null && messageListenerFragment.isVisible()) {
			((ChatFragment) messageListenerFragment).onMessageReceived(from, message);
		}
		
		if (LinphoneService.isReady()) {
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(from, getContentResolver());
			LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(), from.getDisplayName(), message);
		}
	}
	
	public void onMessageSent(String to, String message) {
		if (chatStorage == null) {
			chatStorage = new ChatStorage(this);
		}
		
		chatStorage.saveMessage("", to, message);
		Log.d("Message sent to " + to + ": " + message);
	}

	@Override
	public void onRegistrationStateChanged(RegistrationState state) {
		if (statusFragment != null) {
			statusFragment.registrationStateChanged(state);
		}
	}
	
	private void displayMissedCalls(final int missedCallsCount) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (missedCallsCount > 0) {
					missedCalls.setText(missedCallsCount + "");
					missedCalls.setVisibility(View.VISIBLE);
				} else {
					missedCalls.setVisibility(View.GONE);
				}
			}
		});
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, State state,
			String message) {
		if (state == State.IncomingReceived) {
			Intent intent = new Intent(this, IncomingCallActivity.class);
			startActivity(intent);
		} else if (state == State.OutgoingInit) {
			if (call.getCurrentParamsCopy().getVideoEnabled()) {
				startVideoActivity(call);
			} else {
				startIncallActivity(call);
			}
		} else if (state == State.CallEnd || state == State.Error || state == State.CallReleased) {
			resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
		}
		
		int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
		displayMissedCalls(missedCalls);
	}

	@Override
	public void setAddressAndGoToDialer(String number, String name, Uri photo) {
		Bundle extras = new Bundle();
		extras.putString("SipUri", number);
		extras.putString("DisplayName", name);
		extras.putString("Photo", photo == null ? null : photo.toString());
		changeCurrentFragment(FragmentsAvailable.DIALER, extras);
	}
	
	public void setAddressAndGoToDialer(String number) {
		Bundle extras = new Bundle();
		extras.putString("SipUri", number);
		changeCurrentFragment(FragmentsAvailable.DIALER, extras);
	}

	@Override
	public void goToDialer() {
		changeCurrentFragment(FragmentsAvailable.DIALER, null);
	}

	public void onRegistrationStateChanged(RegistrationState state, String message) {
		if (statusFragment != null) {
			statusFragment.registrationStateChanged(state);
		}
	}

	public void startVideoActivity(LinphoneCall currentCall) {
		Intent intent = new Intent(this, InCallActivity.class);
		intent.putExtra("VideoEnabled", true);
		startActivityForResult(intent, callActivity);
	}
	
	public void startIncallActivity(LinphoneCall currentCall) {
		Intent intent = new Intent(this, InCallActivity.class);
		intent.putExtra("VideoEnabled", false);
		startActivityForResult(intent, callActivity);
	}

	public void showPreferenceErrorDialog(String message) {
		
	}
	
	private void initInCallMenuLayout() {
		isInCallLayout = true;
		selectMenu(FragmentsAvailable.DIALER);
		((DialerFragment) dialerFragment).resetLayout();
	}
	
	public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				isInCallLayout = false;
				((DialerFragment) dialerFragment).resetLayout();
				
				if (LinphoneManager.getLc().getCallsNb() > 0) {
					LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
					if (call.getCurrentParamsCopy().getVideoEnabled()) {
						startVideoActivity(call);
					} else {
						startIncallActivity(call);
					}
				}
			}
		});
	}
	
	public boolean isInCallLayout() {
		return isInCallLayout;
	}
	
	public FragmentsAvailable getCurrentFragment() {
		return currentFragment;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
			if (data.getExtras().getBoolean("Exit", false)) {
				finish();
				stopService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
			} else {
				FragmentsAvailable newFragment = (FragmentsAvailable) data.getExtras().getSerializable("FragmentToDisplay");
				changeCurrentFragment(newFragment, null);
				selectMenu(newFragment);
			}
		}
		else if (requestCode == callActivity) {
			if (LinphoneManager.getLc().getCallsNb() > 0) {
				initInCallMenuLayout(); 
			} else {
				resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		}
		super.onActivityResult(resultCode, requestCode, data);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (chatStorage != null) {
			chatStorage.close();
		}
		chatStorage = new ChatStorage(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		chatStorage.close();
		chatStorage = null;
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		Bundle extras = intent.getExtras();
		if (extras != null && extras.getBoolean("GoToChat", false)) {
			LinphoneService.instance().removeMessageNotification();
			String sipUri = extras.getString("ChatContactSipUri");
			displayChat(sipUri);
		}
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && currentFragment == FragmentsAvailable.DIALER) {
			if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) {
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
}

interface ContactPicked {
	void setAddressAndGoToDialer(String number, String name, Uri photo);
	void goToDialer();
}