package org.linphone;

/*
 LinphoneActivity.java
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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager.AddressType;
import org.linphone.assistant.AssistantActivity;
import org.linphone.assistant.RemoteProvisioningLoginActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallLog;
import org.linphone.core.LinphoneCallLog.CallStatus;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;
import org.linphone.purchase.InAppPurchaseActivity;
import org.linphone.ui.AddressText;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.linphone.LinphoneActivity.ChatRoomContainer.createChatroomContainer;

public class LinphoneActivity extends LinphoneGenericActivity implements OnClickListener, ContactPicked, ActivityCompat.OnRequestPermissionsResultCallback {
	public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static final int SETTINGS_ACTIVITY = 123;
	private static final int CALL_ACTIVITY = 19;
	private static final int PERMISSIONS_REQUEST_OVERLAY = 206;
	private static final int PERMISSIONS_REQUEST_SYNC = 207;
	private static final int PERMISSIONS_REQUEST_CONTACTS = 208;
	private static final int PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER = 209;
	private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
	private static final int PERMISSIONS_RECORD_AUDIO_ECHO_TESTER = 211;

	private static LinphoneActivity instance;

	private StatusFragment statusFragment;
	private TextView missedCalls, missedChats;
	private RelativeLayout contacts, history, dialer, chat;
	private View contacts_selected, history_selected, dialer_selected, chat_selected;
	private RelativeLayout mTopBar;
	private ImageView cancel;
	private FragmentsAvailable pendingFragmentTransaction, currentFragment;
	private Fragment fragment;
	private List<FragmentsAvailable> fragmentsHistory;
	private Fragment.SavedState dialerSavedState;
	private boolean newProxyConfig;
	private boolean emptyFragment = false;
	private boolean isTrialAccount = false;
	private OrientationEventListener mOrientationHelper;
	private LinphoneCoreListenerBase mListener;
	private LinearLayout mTabBar;

	private DrawerLayout sideMenu;
	private RelativeLayout sideMenuContent, quitLayout, defaultAccount;
	private ListView accountsList, sideMenuItemList;
	private ImageView menu;
	private boolean doNotGoToCallActivity = false;
	private List<String> sideMenuItems;
	private boolean callTransfer = false;
	private boolean isOnBackground = false;

	public String mAddressWaitingToBeCalled;

	static final boolean isInstanciated() {
		return instance != null;
	}

	public static final LinphoneActivity instance() {
		if (instance != null)
			return instance;
		throw new RuntimeException("LinphoneActivity not instantiated yet");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//This must be done before calling super.onCreate().
		super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
		boolean useFirstLoginActivity = getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
		if (LinphonePreferences.instance().isProvisioningLoginViewEnabled()) {
			Intent wizard = new Intent();
			wizard.setClass(this, RemoteProvisioningLoginActivity.class);
			wizard.putExtra("Domain", LinphoneManager.getInstance().wizardLoginViewDomain);
			startActivity(wizard);
			finish();
			return;
		} else if (savedInstanceState == null && (useFirstLoginActivity && LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null
				&& LinphonePreferences.instance().isFirstLaunch())) {
			if (LinphonePreferences.instance().getAccountCount() > 0) {
				LinphonePreferences.instance().firstLaunchSuccessful();
			} else {
				startActivity(new Intent().setClass(this, AssistantActivity.class));
				finish();
				return;
			}
		}

		if (getIntent() != null && getIntent().getExtras() != null) {
			newProxyConfig = getIntent().getExtras().getBoolean("isNewProxyConfig");
		}

		if (getResources().getBoolean(R.bool.use_linphone_tag)) {
			if (getPackageManager().checkPermission(Manifest.permission.WRITE_SYNC_SETTINGS, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
				checkSyncPermission();
			} else {
				if (LinphoneService.isReady())
					ContactsManager.getInstance().initializeSyncAccount(getApplicationContext(), getContentResolver());
			}
		} else {
			if (LinphoneService.isReady())
				ContactsManager.getInstance().initializeContactManager(getApplicationContext(), getContentResolver());
		}

		setContentView(R.layout.main);
		instance = this;
		fragmentsHistory = new ArrayList<FragmentsAvailable>();
		pendingFragmentTransaction = FragmentsAvailable.UNKNOW;

		initButtons();
		initSideMenu();

		currentFragment = FragmentsAvailable.EMPTY;
		if (savedInstanceState == null) {
			changeCurrentFragment(FragmentsAvailable.DIALER, getIntent().getExtras());
		} else {
			currentFragment = (FragmentsAvailable) savedInstanceState.getSerializable("currentFragment");
		}

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
		        displayMissedChats(getUnreadMessageCount());
			}

			@Override
			public void registrationState(LinphoneCore lc, LinphoneProxyConfig proxy, LinphoneCore.RegistrationState state, String smessage) {
				LinphoneAuthInfo authInfo = lc.findAuthInfo(proxy.getIdentity(), proxy.getRealm(), proxy.getDomain());

				refreshAccounts();

				if(getResources().getBoolean(R.bool.use_phone_number_validation)
						&& authInfo != null && authInfo.getDomain().equals(getString(R.string.default_domain))) {
					if (state.equals(RegistrationState.RegistrationOk)) {
						LinphoneManager.getInstance().isAccountWithAlias();
					}
				}

				if(state.equals(RegistrationState.RegistrationFailed) && newProxyConfig) {
					newProxyConfig = false;
					if (proxy.getError() == Reason.BadCredentials) {
						//displayCustomToast(getString(R.string.error_bad_credentials), Toast.LENGTH_LONG);
					}
					if (proxy.getError() == Reason.Unauthorized) {
						displayCustomToast(getString(R.string.error_unauthorized), Toast.LENGTH_LONG);
					}
					if (proxy.getError() == Reason.IOError) {
						displayCustomToast(getString(R.string.error_io_error), Toast.LENGTH_LONG);
					}
				}
			}

			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
				if (state == State.IncomingReceived) {
					startActivity(new Intent(LinphoneActivity.instance(), CallIncomingActivity.class));
				} else if (state == State.OutgoingInit || state == State.OutgoingProgress) {
					startActivity(new Intent(LinphoneActivity.instance(), CallOutgoingActivity.class));
				} else if (state == State.CallEnd || state == State.Error || state == State.CallReleased) {
					resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
				}

				int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
				displayMissedCalls(missedCalls);
			}
		};

		int missedCalls = (LinphoneManager.isInstanciated()) ? LinphoneManager.getLc().getMissedCallsCount() : 0;
		displayMissedCalls(missedCalls);

		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		switch (rotation) {
		case Surface.ROTATION_0:
			rotation = 0;
			break;
		case Surface.ROTATION_90:
			rotation = 90;
			break;
		case Surface.ROTATION_180:
			rotation = 180;
			break;
		case Surface.ROTATION_270:
			rotation = 270;
			break;
		}

		if (LinphoneManager.isInstanciated()) {
			LinphoneManager.getLc().setDeviceRotation(rotation);
		}
		mAlwaysChangingPhoneAngle = rotation;
	}

	private void initButtons() {
		mTabBar = (LinearLayout)  findViewById(R.id.footer);
		mTopBar = (RelativeLayout) findViewById(R.id.top_bar);

		cancel = (ImageView) findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		history = (RelativeLayout) findViewById(R.id.history);
		history.setOnClickListener(this);
		contacts = (RelativeLayout) findViewById(R.id.contacts);
		contacts.setOnClickListener(this);
		dialer = (RelativeLayout) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);
		chat = (RelativeLayout) findViewById(R.id.chat);
		chat.setOnClickListener(this);

		history_selected = findViewById(R.id.history_select);
		contacts_selected = findViewById(R.id.contacts_select);
		dialer_selected = findViewById(R.id.dialer_select);
		chat_selected = findViewById(R.id.chat_select);

		missedCalls = (TextView) findViewById(R.id.missed_calls);
		missedChats = (TextView) findViewById(R.id.missed_chats);
	}

	private boolean isTablet() {
		return getResources().getBoolean(R.bool.isTablet);
	}

	public void hideStatusBar() {
		if (isTablet()) {
			return;
		}

		findViewById(R.id.status).setVisibility(View.GONE);
	}

	public void showStatusBar() {
		if (isTablet()) {
			return;
		}

		if (statusFragment != null && !statusFragment.isVisible()) {
			statusFragment.getView().setVisibility(View.VISIBLE);
		}
		findViewById(R.id.status).setVisibility(View.VISIBLE);
	}

	public void isNewProxyConfig(){
		newProxyConfig = true;
	}

	private void changeCurrentFragment(FragmentsAvailable newFragmentType, Bundle extras) {
		changeCurrentFragment(newFragmentType, extras, false);
	}

	private void changeCurrentFragment(FragmentsAvailable newFragmentType, Bundle extras, boolean withoutAnimation) {
		if (newFragmentType == currentFragment && newFragmentType != FragmentsAvailable.CHAT) {
			return;
		}

		if (currentFragment == FragmentsAvailable.DIALER) {
			try {
				DialerFragment dialerFragment = DialerFragment.instance();
				dialerSavedState = getFragmentManager().saveFragmentInstanceState(dialerFragment);
			} catch (Exception e) {
			}
		}

		fragment = null;

		switch (newFragmentType) {
		case HISTORY_LIST:
			fragment = new HistoryListFragment();
			break;
		case HISTORY_DETAIL:
			fragment = new HistoryDetailFragment();
			break;
		case CONTACTS_LIST:
			checkAndRequestWriteContactsPermission();
			fragment = new ContactsListFragment();
			break;
		case CONTACT_DETAIL:
			fragment = new ContactDetailsFragment();
			break;
		case CONTACT_EDITOR:
			fragment = new ContactEditorFragment();
			break;
		case DIALER:
			fragment = new DialerFragment();
			if (extras == null) {
				fragment.setInitialSavedState(dialerSavedState);
			}
			break;
		case SETTINGS:
			fragment = new SettingsFragment();
			break;
		case ACCOUNT_SETTINGS:
			fragment = new AccountPreferencesFragment();
			break;
		case ABOUT:
			fragment = new AboutFragment();
			break;
		case EMPTY:
			fragment = new EmptyFragment();
			break;
		case CHAT_LIST:
			fragment = new ChatListFragment();
			break;
		case CHAT:
			fragment = new ChatFragment();
			break;
		default:
			break;
		}

		if (fragment != null) {
			fragment.setArguments(extras);
			if (isTablet()) {
				changeFragmentForTablets(fragment, newFragmentType, withoutAnimation);
				switch (newFragmentType) {
				case HISTORY_LIST:
					((HistoryListFragment) fragment).displayFirstLog();
					break;
				case CONTACTS_LIST:
					((ContactsListFragment) fragment).displayFirstContact();
					break;
				case CHAT_LIST:
					((ChatListFragment) fragment).displayFirstChat();
					break;
				}
			} else {
				changeFragment(fragment, newFragmentType, withoutAnimation);
			}
		}
	}

	private void changeFragment(Fragment newFragment, FragmentsAvailable newFragmentType, boolean withoutAnimation) {
		FragmentManager fm = getFragmentManager();
		FragmentTransaction transaction = fm.beginTransaction();

		/*if (!withoutAnimation && !isAnimationDisabled && currentFragment.shouldAnimate()) {
			if (newFragmentType.isRightOf(currentFragment)) {
				transaction.setCustomAnimations(R.anim.slide_in_right_to_left,
						R.anim.slide_out_right_to_left,
						R.anim.slide_in_left_to_right,
						R.anim.slide_out_left_to_right);
			} else {
				transaction.setCustomAnimations(R.anim.slide_in_left_to_right,
						R.anim.slide_out_left_to_right,
						R.anim.slide_in_right_to_left,
						R.anim.slide_out_right_to_left);
			}
		}*/

		if (newFragmentType != FragmentsAvailable.DIALER
				&& newFragmentType != FragmentsAvailable.CONTACTS_LIST
				&& newFragmentType != FragmentsAvailable.CHAT_LIST
				&& newFragmentType != FragmentsAvailable.HISTORY_LIST) {
			transaction.addToBackStack(newFragmentType.toString());
		} else {
			while (fm.getBackStackEntryCount() > 0) {
				fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
			}
		}

		transaction.replace(R.id.fragmentContainer, newFragment, newFragmentType.toString());
		transaction.commitAllowingStateLoss();
		fm.executePendingTransactions();

		currentFragment = newFragmentType;
	}

	private void changeFragmentForTablets(Fragment newFragment, FragmentsAvailable newFragmentType, boolean withoutAnimation) {
		if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
			if (newFragmentType == FragmentsAvailable.DIALER) {
				showStatusBar();
			} else {
				hideStatusBar();
			}
		}
		emptyFragment = false;
		LinearLayout ll = (LinearLayout) findViewById(R.id.fragmentContainer2);

		FragmentTransaction transaction = getFragmentManager().beginTransaction();

		if(newFragmentType == FragmentsAvailable.EMPTY){
			ll.setVisibility(View.VISIBLE);
			emptyFragment = true;
			transaction.replace(R.id.fragmentContainer2, newFragment);
			transaction.commitAllowingStateLoss();
			getFragmentManager().executePendingTransactions();
		} else {
			if (newFragmentType.shouldAddItselfToTheRightOf(currentFragment)) {
				ll.setVisibility(View.VISIBLE);

				if (newFragmentType == FragmentsAvailable.CONTACT_EDITOR) {
					transaction.addToBackStack(newFragmentType.toString());
				}
				transaction.replace(R.id.fragmentContainer2, newFragment);
			} else {
				if (newFragmentType == FragmentsAvailable.EMPTY) {
					ll.setVisibility(View.VISIBLE);
					transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
					emptyFragment = true;
				}

				if (newFragmentType == FragmentsAvailable.DIALER
						|| newFragmentType == FragmentsAvailable.ABOUT
						|| newFragmentType == FragmentsAvailable.SETTINGS
						|| newFragmentType == FragmentsAvailable.ACCOUNT_SETTINGS) {
					ll.setVisibility(View.GONE);
				} else {
					ll.setVisibility(View.VISIBLE);
					transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
				}

				/*if (!withoutAnimation && !isAnimationDisabled && currentFragment.shouldAnimate()) {
					if (newFragmentType.isRightOf(currentFragment)) {
						transaction.setCustomAnimations(R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left, R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right);
					} else {
						transaction.setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right, R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left);
					}
				}*/
				transaction.replace(R.id.fragmentContainer, newFragment);
			}
			transaction.commitAllowingStateLoss();
			getFragmentManager().executePendingTransactions();

			currentFragment = newFragmentType;
			if (newFragmentType == FragmentsAvailable.DIALER
					|| newFragmentType == FragmentsAvailable.SETTINGS
					|| newFragmentType == FragmentsAvailable.CONTACTS_LIST
					|| newFragmentType == FragmentsAvailable.CHAT_LIST
					|| newFragmentType == FragmentsAvailable.HISTORY_LIST) {
				try {
					getFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
				} catch (java.lang.IllegalStateException e) {

				}
			}
			fragmentsHistory.add(currentFragment);
		}
	}

	public void displayHistoryDetail(String sipUri, LinphoneCallLog log) {
		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		} catch (LinphoneCoreException e) {
			Log.e("Cannot display history details",e);
			//TODO display error message
			return;
		}
		LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(lAddress);

		String displayName = c != null ? c.getFullName() : LinphoneUtils.getAddressDisplayName(sipUri);
		String pictureUri = c != null && c.getPhotoUri() != null ? c.getPhotoUri().toString() : null;

		String status;
		if (log.getDirection() == CallDirection.Outgoing) {
			status = getString(R.string.outgoing);
		} else {
			if (log.getStatus() == CallStatus.Missed) {
				status = getString(R.string.missed);
			} else {
				status = getString(R.string.incoming);
			}
		}

		String callTime = secondsToDisplayableString(log.getCallDuration());
		String callDate = String.valueOf(log.getTimestamp());

		Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
		if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.HISTORY_DETAIL) {
			HistoryDetailFragment historyDetailFragment = (HistoryDetailFragment) fragment2;
			historyDetailFragment.changeDisplayedHistory(lAddress.asStringUriOnly(), displayName, pictureUri, status, callTime, callDate);
		} else {
			Bundle extras = new Bundle();
			extras.putString("SipUri", lAddress.asString());
			if (displayName != null) {
				extras.putString("DisplayName", displayName);
				extras.putString("PictureUri", pictureUri);
			}
			extras.putString("CallStatus", status);
			extras.putString("CallTime", callTime);
			extras.putString("CallDate", callDate);

			changeCurrentFragment(FragmentsAvailable.HISTORY_DETAIL, extras);
		}
	}

	public void displayEmptyFragment(){
		changeCurrentFragment(FragmentsAvailable.EMPTY, new Bundle());
	}

	@SuppressLint("SimpleDateFormat")
	private String secondsToDisplayableString(int secs) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		cal.set(0, 0, 0, 0, 0, secs);
		return dateFormat.format(cal.getTime());
	}

	public void displayContact(LinphoneContact contact, boolean chatOnly) {
		Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
		if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.CONTACT_DETAIL) {
			ContactDetailsFragment contactFragment = (ContactDetailsFragment) fragment2;
			contactFragment.changeDisplayedContact(contact);
		} else {
			Bundle extras = new Bundle();
			extras.putSerializable("Contact", contact);
			extras.putBoolean("ChatAddressOnly", chatOnly);
			changeCurrentFragment(FragmentsAvailable.CONTACT_DETAIL, extras);
		}
	}

	public void displayContacts(boolean chatOnly) {
		Bundle extras = new Bundle();
		extras.putBoolean("ChatAddressOnly", chatOnly);
		changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
	}

	public void displayContactsForEdition(String sipAddress) {
		Bundle extras = new Bundle();
		extras.putBoolean("EditOnClick", true);
		extras.putString("SipAddress", sipAddress);
		changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
	}

	public void displayAbout() {
		changeCurrentFragment(FragmentsAvailable.ABOUT, null);
	}

	public void displayContactsForEdition(String sipAddress, String displayName) {
		Bundle extras = new Bundle();
		extras.putBoolean("EditOnClick", true);
		extras.putString("SipAddress", sipAddress);
		extras.putString("DisplayName", displayName);
		changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
	}

	public void displayAssistant() {
		startActivity(new Intent(LinphoneActivity.this, AssistantActivity.class));
	}



	public void displayInapp() {
		startActivity(new Intent(LinphoneActivity.this, InAppPurchaseActivity.class));
	}

	public int getUnreadMessageCount() {
		int count = 0;
		LinphoneChatRoom[] chats = LinphoneManager.getLc().getChatRooms();
		for (LinphoneChatRoom chatroom : chats) {
			count += chatroom.getUnreadMessagesCount();
		}
		return count;
	}

	public void displayChat(String sipUri, String message, String fileUri) {
		if (getResources().getBoolean(R.bool.disable_chat)) {
			return;
		}

		String pictureUri = null;
		String thumbnailUri = null;
		String displayName = null;

		LinphoneAddress lAddress = null;
		if(sipUri != null) {
			try {
				lAddress = LinphoneManager.getLc().interpretUrl(sipUri);
			} catch (LinphoneCoreException e) {
				//TODO display error message
				Log.e("Cannot display chat", e);
				return;
			}
			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(lAddress);
			displayName = contact != null ? contact.getFullName() : null;

			if (contact != null && contact.getPhotoUri() != null) {
				pictureUri = contact.getPhotoUri().toString();
				thumbnailUri = contact.getThumbnailUri().toString();
			}
		}

		if (currentFragment == FragmentsAvailable.CHAT_LIST || currentFragment == FragmentsAvailable.CHAT) {
			Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
			if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.CHAT && !emptyFragment) {
				ChatFragment chatFragment = (ChatFragment) fragment2;
				chatFragment.changeDisplayedChat(sipUri, displayName, pictureUri, message, fileUri);
			} else {
				Bundle extras = new Bundle();
				extras.putString("SipUri", sipUri);
				if(message != null)
					extras.putString("messageDraft", message);
				if(fileUri != null)
					extras.putString("fileSharedUri", fileUri);
				if (sipUri != null && lAddress.getDisplayName() != null) {
					extras.putString("DisplayName", displayName);
					extras.putString("PictureUri", pictureUri);
					extras.putString("ThumbnailUri", thumbnailUri);
				}
				changeCurrentFragment(FragmentsAvailable.CHAT, extras);
			}
		} else {
			if(isTablet()){
				changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
				//displayChat(sipUri, message, fileUri);
			} else {
				Bundle extras = new Bundle();
				if(sipUri != null || sipUri != "")
					extras.putString("SipUri", sipUri);
				if(message != null)
					extras.putString("messageDraft", message);
				if(fileUri != null)
					extras.putString("fileSharedUri", fileUri);
				if (sipUri != null  && lAddress.getDisplayName() != null) {
					extras.putString("DisplayName", displayName);
					extras.putString("PictureUri", pictureUri);
					extras.putString("ThumbnailUri", thumbnailUri);
				}
				changeCurrentFragment(FragmentsAvailable.CHAT, extras);
			}
		}

		LinphoneService.instance().resetMessageNotifCount();
		LinphoneService.instance().removeMessageNotification();
		displayMissedChats(getUnreadMessageCount());
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		resetSelection();

		if (id == R.id.history) {
			changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
			history_selected.setVisibility(View.VISIBLE);
			LinphoneManager.getLc().resetMissedCallsCount();
			displayMissedCalls(0);
		} else if (id == R.id.contacts) {
			changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, null);
			contacts_selected.setVisibility(View.VISIBLE);
		} else if (id == R.id.dialer) {
			changeCurrentFragment(FragmentsAvailable.DIALER, null);
			dialer_selected.setVisibility(View.VISIBLE);
		} else if (id == R.id.chat) {
			changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
			chat_selected.setVisibility(View.VISIBLE);
		} else if (id == R.id.cancel){
			hideTopBar();
			displayDialer();
		}
	}

	private void resetSelection() {
		history_selected.setVisibility(View.GONE);
		contacts_selected.setVisibility(View.GONE);
		dialer_selected.setVisibility(View.GONE);
		chat_selected.setVisibility(View.GONE);
	}

	public void hideTabBar(Boolean hide) {
		if(hide){
			mTabBar.setVisibility(View.GONE);
		} else {
			mTabBar.setVisibility(View.VISIBLE);
		}
	}

	public void hideTopBar() {
		mTopBar.setVisibility(View.GONE);
	}

	@SuppressWarnings("incomplete-switch")
	public void selectMenu(FragmentsAvailable menuToSelect) {
		currentFragment = menuToSelect;
		resetSelection();

		switch (menuToSelect) {
		case HISTORY_LIST:
		case HISTORY_DETAIL:
			history_selected.setVisibility(View.VISIBLE);
			break;
		case CONTACTS_LIST:
			case CONTACT_DETAIL:
			case CONTACT_EDITOR:
			contacts_selected.setVisibility(View.VISIBLE);
			break;
		case DIALER:
			dialer_selected.setVisibility(View.VISIBLE);
			break;
		case SETTINGS:
		case ACCOUNT_SETTINGS:
			hideTabBar(true);
			mTopBar.setVisibility(View.VISIBLE);
			break;
		case ABOUT:
			hideTabBar(true);
			break;
		case CHAT_LIST:
		case CHAT:
			chat_selected.setVisibility(View.VISIBLE);
			break;
		}
	}

	public void updateDialerFragment(DialerFragment fragment) {
		// Hack to maintain soft input flags
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
	}

	public void goToDialerFragment() {
		changeCurrentFragment(FragmentsAvailable.DIALER, null);
		dialer_selected.setVisibility(View.VISIBLE);
	}

	public void onMessageSent(String to, String message) {
		Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
		if (fragment.getClass() == ChatListFragment.class) {
			((ChatListFragment)fragment).refresh();
		}
	}

	public void updateStatusFragment(StatusFragment fragment) {
		statusFragment = fragment;
	}

	public void displaySettings() {
		changeCurrentFragment(FragmentsAvailable.SETTINGS, null);
	}

	public void displayDialer() {
		changeCurrentFragment(FragmentsAvailable.DIALER, null);
	}

	public void displayAccountSettings(int accountNumber) {
		Bundle bundle = new Bundle();
		bundle.putInt("Account", accountNumber);
		changeCurrentFragment(FragmentsAvailable.ACCOUNT_SETTINGS, bundle);
		//settings.setSelected(true);
	}

	public StatusFragment getStatusFragment() {
		return statusFragment;
	}

	static class ChatRoomContainer{
		private LinphoneChatRoom mCr;
		long mTime;
		static public ChatRoomContainer createChatroomContainer(LinphoneChatRoom chatRoom) {
			if (chatRoom.getHistorySize() <= 0) return null;
			return new ChatRoomContainer(chatRoom);
		}
		public ChatRoomContainer(LinphoneChatRoom chatroom){
			mCr = chatroom;
			LinphoneChatMessage[] lastMsg = chatroom.getHistory(1);
			if (lastMsg != null && lastMsg.length > 0 && lastMsg[0] != null) {
				mTime = lastMsg[0].getTime();
			}else mTime = 0;
		}
		LinphoneChatRoom getChatRoom(){
			return mCr;
		}
		long getTime(){
			return mTime;
		}
	};
	public List<String> getChatList() {
		ArrayList<String> chatList = new ArrayList<String>();

		LinphoneChatRoom[] chats = LinphoneManager.getLc().getChatRooms();
		List<ChatRoomContainer> rooms = new ArrayList<ChatRoomContainer>();

		for (LinphoneChatRoom chatroom : chats) {
			ChatRoomContainer crc = createChatroomContainer(chatroom);
			if (crc != null) rooms.add(crc);
		}

		if (rooms.size() > 1) {
			Collections.sort(rooms, new Comparator<ChatRoomContainer>() {
				@Override
				public int compare(ChatRoomContainer a, ChatRoomContainer b) {
					long atime = a.getTime();
					long btime = b.getTime();

					if (atime > btime)
						return -1;
					else if (btime > atime)
						return 1;
					else
						return 0;
				}
			});
		}

		for (ChatRoomContainer chatroomContainer : rooms) {
			chatList.add(chatroomContainer.getChatRoom().getPeerAddress().asStringUriOnly());
		}

		return chatList;
	}

	public void removeFromChatList(String sipUri) {
		LinphoneChatRoom chatroom = LinphoneManager.getLc().getOrCreateChatRoom(sipUri);
		chatroom.deleteHistory();
	}

	public void updateMissedChatCount() {
		displayMissedChats(getUnreadMessageCount());
	}

	public void displayMissedCalls(final int missedCallsCount) {
		if (missedCallsCount > 0) {
			missedCalls.setText(missedCallsCount + "");
			missedCalls.setVisibility(View.VISIBLE);
		} else {
			if (LinphoneManager.isInstanciated()) LinphoneManager.getLc().resetMissedCallsCount();
			missedCalls.clearAnimation();
			missedCalls.setVisibility(View.GONE);
		}
	}

	private void displayMissedChats(final int missedChatCount) {
		if (missedChatCount > 0) {
			missedChats.setText(missedChatCount + "");
			missedChats.setVisibility(View.VISIBLE);
		} else {
			missedChats.clearAnimation();
			missedChats.setVisibility(View.GONE);
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

	public Dialog displayDialog(String text){
		Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
		d.setAlpha(200);
		dialog.setContentView(R.layout.dialog);
		dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		dialog.getWindow().setBackgroundDrawable(d);

		TextView customText = (TextView) dialog.findViewById(R.id.customText);
		customText.setText(text);
		return dialog;
	}

	public Dialog displayWrongPasswordDialog(final String username, final String realm, final String domain){
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
		d.setAlpha(200);
		dialog.setContentView(R.layout.input_dialog);
		dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		dialog.getWindow().setBackgroundDrawable(d);

		TextView customText = (TextView) dialog.findViewById(R.id.customText);
		customText.setText(getString(R.string.error_bad_credentials));

		Button retry = (Button) dialog.findViewById(R.id.retry);
		Button cancel = (Button) dialog.findViewById(R.id.cancel);

		retry.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				String newPassword = ((EditText) dialog.findViewById(R.id.password)).getText().toString();
				LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(username, null, newPassword, null, realm, domain);
				LinphoneManager.getLc().addAuthInfo(authInfo);
				LinphoneManager.getLc().refreshRegisters();
				dialog.dismiss();
			}
		});

		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				dialog.dismiss();
			}
		});

		return dialog;
	}

	@Override
	public void setAddresGoToDialerAndCall(String number, String name, Uri photo) {
//		Bundle extras = new Bundle();
//		extras.putString("SipUri", number);
//		extras.putString("DisplayName", name);
//		extras.putString("Photo", photo == null ? null : photo.toString());
//		changeCurrentFragment(FragmentsAvailable.DIALER, extras);

		AddressType address = new AddressText(this, null);
		address.setDisplayedName(name);
		address.setText(number);
		LinphoneManager.getInstance().newOutgoingCall(address);
	}

	public void startIncallActivity(LinphoneCall currentCall) {
		Intent intent = new Intent(this, CallActivity.class);
		startOrientationSensor();
		startActivityForResult(intent, CALL_ACTIVITY);
	}

	/**
	 * Register a sensor to track phoneOrientation changes
	 */
	private synchronized void startOrientationSensor() {
		if (mOrientationHelper == null) {
			mOrientationHelper = new LocalOrientationEventListener(this);
		}
		mOrientationHelper.enable();
	}

	private int mAlwaysChangingPhoneAngle = -1;

	private class LocalOrientationEventListener extends OrientationEventListener {
		public LocalOrientationEventListener(Context context) {
			super(context);
		}

		@Override
		public void onOrientationChanged(final int o) {
			if (o == OrientationEventListener.ORIENTATION_UNKNOWN) {
				return;
			}

			int degrees = 270;
			if (o < 45 || o > 315)
				degrees = 0;
			else if (o < 135)
				degrees = 90;
			else if (o < 225)
				degrees = 180;

			if (mAlwaysChangingPhoneAngle == degrees) {
				return;
			}
			mAlwaysChangingPhoneAngle = degrees;

			Log.d("Phone orientation changed to ", degrees);
			int rotation = (360 - degrees) % 360;
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (lc != null) {
				lc.setDeviceRotation(rotation);
				LinphoneCall currentCall = lc.getCurrentCall();
				if (currentCall != null && currentCall.cameraEnabled() && currentCall.getCurrentParams().getVideoEnabled()) {
					lc.updateCall(currentCall, null);
				}
			}
		}
	}

	public Boolean isCallTransfer(){
		return callTransfer;
	}

	private void initInCallMenuLayout(final boolean callTransfer) {
		selectMenu(FragmentsAvailable.DIALER);
		DialerFragment dialerFragment = DialerFragment.instance();
		if (dialerFragment != null) {
			((DialerFragment) dialerFragment).resetLayout(callTransfer);
		}
	}

	public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
		DialerFragment dialerFragment = DialerFragment.instance();
		if (dialerFragment != null) {
			((DialerFragment) dialerFragment).resetLayout(true);
		}

		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
			LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
			if (call.getState() == LinphoneCall.State.IncomingReceived) {
				startActivity(new Intent(LinphoneActivity.this, CallIncomingActivity.class));
			} else {
				startIncallActivity(call);
			}
		}
	}

	public FragmentsAvailable getCurrentFragment() {
		return currentFragment;
	}

	public void addContact(String displayName, String sipUri)
	{
		Bundle extras = new Bundle();
		extras.putSerializable("NewSipAdress", sipUri);
		extras.putSerializable("NewDisplayName", displayName);
		changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
	}

	public void editContact(LinphoneContact contact)
	{
		Bundle extras = new Bundle();
		extras.putSerializable("Contact", contact);
		changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
	}

	public void editContact(LinphoneContact contact, String sipAddress)
	{
		Bundle extras = new Bundle();
		extras.putSerializable("Contact", contact);
		extras.putSerializable("NewSipAdress", sipAddress);
		changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
	}

	public void quit() {
		finish();
		stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
		ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
		am.killBackgroundProcesses(getString(R.string.sync_account_type));
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		if (pendingFragmentTransaction != FragmentsAvailable.UNKNOW) {
			changeCurrentFragment(pendingFragmentTransaction, null, true);
			selectMenu(pendingFragmentTransaction);
			pendingFragmentTransaction = FragmentsAvailable.UNKNOW;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
			if (data.getExtras().getBoolean("Exit", false)) {
				quit();
			} else {
				pendingFragmentTransaction = (FragmentsAvailable) data.getExtras().getSerializable("FragmentToDisplay");
			}
		} else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == CALL_ACTIVITY) {
			getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
			callTransfer = data != null && data.getBooleanExtra("Transfer", false);
			boolean chat = data != null && data.getBooleanExtra("chat", false);
			if(chat){
				pendingFragmentTransaction = FragmentsAvailable.CHAT_LIST;
			}
			if (LinphoneManager.getLc().getCallsNb() > 0) {
				initInCallMenuLayout(callTransfer);
			} else {
				resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		} else if (requestCode == PERMISSIONS_REQUEST_OVERLAY) {
			if (Compatibility.canDrawOverlays(this)) {
				LinphonePreferences.instance().enableOverlay(true);
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	protected void onPause() {
		getIntent().putExtra("PreviousActivity", 0);

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		callTransfer = false;
		isOnBackground = true;

		super.onPause();
	}

	public boolean checkAndRequestOverlayPermission() {
		Log.i("[Permission] Draw overlays permission is " + (Compatibility.canDrawOverlays(this) ? "granted" : "denied"));
		if (!Compatibility.canDrawOverlays(this)) {
			Log.i("[Permission] Asking for overlay");
			Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
			startActivityForResult(intent, PERMISSIONS_REQUEST_OVERLAY);
			return false;
		}
		return true;
	}

	public void checkAndRequestReadPhoneStatePermission() {
		checkAndRequestPermission(Manifest.permission.READ_PHONE_STATE, 0);
	}

	public void checkAndRequestReadExternalStoragePermission() {
		checkAndRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, 0);
	}

	public void checkAndRequestExternalStoragePermission() {
		checkAndRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 0);
	}

	public void checkAndRequestCameraPermission() {
		checkAndRequestPermission(Manifest.permission.CAMERA, 0);
	}

	public void checkAndRequestReadContactsPermission() {
		checkAndRequestPermission(Manifest.permission.READ_CONTACTS, PERMISSIONS_REQUEST_CONTACTS);
	}

	public void checkAndRequestInappPermission() {
		checkAndRequestPermission(Manifest.permission.GET_ACCOUNTS, PERMISSIONS_REQUEST_CONTACTS);
	}

	public void checkAndRequestWriteContactsPermission() {
		checkAndRequestPermission(Manifest.permission.WRITE_CONTACTS, 0);
	}

	public void checkAndRequestRecordAudioPermissionForEchoCanceller() {
		checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER);
	}

	public void checkAndRequestRecordAudioPermissionsForEchoTester() {
		checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_TESTER);
	}

	public void checkAndRequestReadExternalStoragePermissionForDeviceRingtone() {
		checkAndRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
	}

	public void checkAndRequestPermissionsToSendImage() {
		ArrayList<String> permissionsList = new ArrayList<String>();

		int readExternalStorage = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
		Log.i("[Permission] Read external storage permission is " + (readExternalStorage == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
		Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (readExternalStorage != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
			Log.i("[Permission] Asking for read external storage");
			permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
		}
		if (camera != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
			Log.i("[Permission] Asking for camera");
			permissionsList.add(Manifest.permission.CAMERA);
		}
		if (permissionsList.size() > 0) {
			String[] permissions = new String[permissionsList.size()];
			permissions = permissionsList.toArray(permissions);
			ActivityCompat.requestPermissions(this, permissions, 0);
		}
	}

	private void checkSyncPermission() {
		checkAndRequestPermission(Manifest.permission.WRITE_SYNC_SETTINGS, PERMISSIONS_REQUEST_SYNC);
	}

	public void checkAndRequestPermission(String permission, int result) {
		int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
		Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
			Log.i("[Permission] Asking for " + permission);
			ActivityCompat.requestPermissions(this, new String[] { permission }, result);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (permissions.length <= 0)
			return;

		int readContactsI = -1;
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
			if (permissions[i].compareTo(Manifest.permission.READ_CONTACTS) == 0)
				readContactsI = i;
		}

		switch (requestCode) {
			case PERMISSIONS_REQUEST_SYNC:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					ContactsManager.getInstance().initializeSyncAccount(getApplicationContext(), getContentResolver());
				} else {
					ContactsManager.getInstance().initializeContactManager(getApplicationContext(), getContentResolver());
				}
				break;
			case PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					((SettingsFragment) fragment).startEchoCancellerCalibration();
				} else {
					((SettingsFragment) fragment).echoCalibrationFail();
				}
				break;
			case PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE:
				if (permissions[0].compareTo(Manifest.permission.READ_EXTERNAL_STORAGE) != 0)
					break;
				boolean enableRingtone = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
				LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
				LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
				break;
			case PERMISSIONS_RECORD_AUDIO_ECHO_TESTER:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
					((SettingsFragment) fragment).startEchoTester();
				break;
		}
		if (readContactsI >= 0 && grantResults[readContactsI] == PackageManager.PERMISSION_GRANTED) {
			ContactsManager.getInstance().enableContactsAccess();
			if (!ContactsManager.getInstance().contactsFetchedOnce()) {
				ContactsManager.getInstance().enableContactsAccess();
				ContactsManager.getInstance().fetchContactsAsync();
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		ArrayList<String> permissionsList = new ArrayList<String>();

		int contacts = getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName());
		Log.i("[Permission] Contacts permission is " + (contacts == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		int readPhone = getPackageManager().checkPermission(Manifest.permission.READ_PHONE_STATE, getPackageName());
		Log.i("[Permission] Read phone state permission is " + (readPhone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		int ringtone = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
		Log.i("[Permission] Read external storage for ring tone permission is " + (ringtone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (ringtone != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				Log.i("[Permission] Asking for read external storage for ring tone");
				permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
			}
		}
		if (readPhone != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_PHONE_STATE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
				Log.i("[Permission] Asking for read phone state");
				permissionsList.add(Manifest.permission.READ_PHONE_STATE);
			}
		}
		if (contacts != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_CONTACTS) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
				Log.i("[Permission] Asking for contacts");
				permissionsList.add(Manifest.permission.READ_CONTACTS);
			}
		} else {
			if (!ContactsManager.getInstance().contactsFetchedOnce()) {
				ContactsManager.getInstance().enableContactsAccess();
				ContactsManager.getInstance().fetchContactsAsync();
			}
		}

		if (permissionsList.size() > 0) {
			String[] permissions = new String[permissionsList.size()];
			permissions = permissionsList.toArray(permissions);
			ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("currentFragment", currentFragment);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!LinphoneService.isReady()) {
			startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
		}

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
			if (!LinphoneService.instance().displayServiceNotification()) {
				lc.refreshRegisters();
			}
		}

		if (isTablet()) {
			// Prevent fragmentContainer2 to be visible when rotating the device
			LinearLayout ll = (LinearLayout) findViewById(R.id.fragmentContainer2);
			if (currentFragment == FragmentsAvailable.DIALER
					|| currentFragment == FragmentsAvailable.ABOUT
					|| currentFragment == FragmentsAvailable.SETTINGS
					|| currentFragment == FragmentsAvailable.ACCOUNT_SETTINGS) {
				ll.setVisibility(View.GONE);
			}
		}

		refreshAccounts();

		if(getResources().getBoolean(R.bool.enable_in_app_purchase)){
			isTrialAccount();
		}

		updateMissedChatCount();

		displayMissedCalls(LinphoneManager.getLc().getMissedCallsCount());

		LinphoneManager.getInstance().changeStatusToOnline();

		if (getIntent().getIntExtra("PreviousActivity", 0) != CALL_ACTIVITY && !doNotGoToCallActivity) {
			if (LinphoneManager.getLc().getCalls().length > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
				LinphoneCall.State callState = call.getState();

				if (callState == State.IncomingReceived) {
					startActivity(new Intent(this, CallIncomingActivity.class));
				} else if (callState == State.OutgoingInit || callState == State.OutgoingProgress || callState == State.OutgoingRinging) {
					startActivity(new Intent(this, CallOutgoingActivity.class));
				} else {
					startIncallActivity(call);
				}
			}
		}

		Intent intent = getIntent();

		if (intent.getStringExtra("msgShared") != null) {
			displayChat(null, intent.getStringExtra("msgShared"), null);
			intent.putExtra("msgShared", "");
		}
		if (intent.getStringExtra("fileShared") != null && intent.getStringExtra("fileShared") != "") {
			displayChat(null, null, intent.getStringExtra("fileShared"));
			intent.putExtra("fileShared", "");
		}
		doNotGoToCallActivity = false;
		isOnBackground = false;

		if (intent != null) {
			Bundle extras = intent.getExtras();
			if (extras != null && extras.containsKey("SipUriOrNumber")) {
				mAddressWaitingToBeCalled = extras.getString("SipUriOrNumber");
				intent.removeExtra("SipUriOrNumber");
				goToDialerFragment();
			}
		}
	}

	@Override
	protected void onDestroy() {
		if (mOrientationHelper != null) {
			mOrientationHelper.disable();
			mOrientationHelper = null;
		}

		instance = null;
		super.onDestroy();

		unbindDrawables(findViewById(R.id.topLayout));
		System.gc();
	}

	private void unbindDrawables(View view) {
		if (view != null && view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			((ViewGroup) view).removeAllViews();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (getCurrentFragment() == FragmentsAvailable.SETTINGS) {
			if (fragment instanceof SettingsFragment) {
				((SettingsFragment) fragment).closePreferenceScreen();
			}
		}
		Bundle extras = intent.getExtras();
		if (extras != null && extras.getBoolean("GoToChat", false)) {
			LinphoneService.instance().removeMessageNotification();
			String sipUri = extras.getString("ChatContactSipUri");
			doNotGoToCallActivity = true;
			displayChat(sipUri, null, null);
		} else if (extras != null && extras.getBoolean("GoToHistory", false)) {
			doNotGoToCallActivity = true;
			changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
		} else if (extras != null && extras.getBoolean("GoToInapp", false)) {
			LinphoneService.instance().removeMessageNotification();
			doNotGoToCallActivity = true;
			displayInapp();
		} else if (extras != null && extras.getBoolean("Notification", false)) {
			if (LinphoneManager.getLc().getCallsNb() > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
				startIncallActivity(call);
			}
		}else if (extras != null && extras.getBoolean("StartCall", false)) {
			boolean extraBool = extras.getBoolean("StartCall", false);
			if (CallActivity.isInstanciated()) {
				CallActivity.instance().startIncomingCallActivity();
			} else {
				mAddressWaitingToBeCalled = extras.getString("NumberToCall");
				goToDialerFragment();
				//startActivity(new Intent(this, CallIncomingActivity.class));
			}
		} else {
			DialerFragment dialerFragment = DialerFragment.instance();
			if (dialerFragment != null) {
				if (extras != null && extras.containsKey("SipUriOrNumber")) {
					if (getResources().getBoolean(R.bool.automatically_start_intercepted_outgoing_gsm_call)) {
						((DialerFragment) dialerFragment).newOutgoingCall(extras.getString("SipUriOrNumber"));
					} else {
						((DialerFragment) dialerFragment).displayTextInAddressBar(extras.getString("SipUriOrNumber"));
					}
				} else {
					((DialerFragment) dialerFragment).newOutgoingCall(intent);
				}
			} else {
				if (extras != null && extras.containsKey("SipUriOrNumber")) {
					mAddressWaitingToBeCalled = extras.getString("SipUriOrNumber");
					goToDialerFragment();
				}
			}
			if (LinphoneManager.getLc().getCalls().length > 0) {
				// If a call is ringing, start incomingcallactivity
				Collection<LinphoneCall.State> incoming = new ArrayList<LinphoneCall.State>();
				incoming.add(LinphoneCall.State.IncomingReceived);
				if (LinphoneUtils.getCallsInState(LinphoneManager.getLc(), incoming).size() > 0) {
					if (CallActivity.isInstanciated()) {
						CallActivity.instance().startIncomingCallActivity();
					} else {
						startActivity(new Intent(this, CallIncomingActivity.class));
					}
				}
			}
		}
	}

	public boolean isOnBackground() {
		return isOnBackground;
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (currentFragment == FragmentsAvailable.DIALER
					|| currentFragment == FragmentsAvailable.CONTACTS_LIST
					|| currentFragment == FragmentsAvailable.HISTORY_LIST
					|| currentFragment == FragmentsAvailable.CHAT_LIST) {
				boolean isBackgroundModeActive = LinphonePreferences.instance().isBackgroundModeEnabled();
				if (!isBackgroundModeActive) {
					stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
					finish();
				} else if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) {
					return true;
				}
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	//SIDE MENU
	public void openOrCloseSideMenu(boolean open) {
		if(open) {
			sideMenu.openDrawer(sideMenuContent);
		} else {
			sideMenu.closeDrawer(sideMenuContent);
		}
	}

	public void initSideMenu() {
		sideMenu = (DrawerLayout) findViewById(R.id.side_menu);
		sideMenuItems = new ArrayList<String>();
		sideMenuItems.add(getResources().getString(R.string.menu_assistant));
		sideMenuItems.add(getResources().getString(R.string.menu_settings));
		if(getResources().getBoolean(R.bool.enable_in_app_purchase)){
			sideMenuItems.add(getResources().getString(R.string.inapp));
		}
		sideMenuItems.add(getResources().getString(R.string.menu_about));
		sideMenuContent = (RelativeLayout) findViewById(R.id.side_menu_content);
		sideMenuItemList = (ListView)findViewById(R.id.item_list);
		menu = (ImageView) findViewById(R.id.side_menu_button);

		sideMenuItemList.setAdapter(new ArrayAdapter<String>(this, R.layout.side_menu_item_cell, sideMenuItems));
		sideMenuItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_settings))) {
					LinphoneActivity.instance().displaySettings();
				}
				if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_about))) {
					LinphoneActivity.instance().displayAbout();
				}
				if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_assistant))) {
					LinphoneActivity.instance().displayAssistant();
				}
				if(getResources().getBoolean(R.bool.enable_in_app_purchase)){
					if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.inapp))) {
						LinphoneActivity.instance().displayInapp();
					}
				}
				openOrCloseSideMenu(false);
			}
		});

		initAccounts();

		menu.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if(sideMenu.isDrawerVisible(Gravity.LEFT)){
					sideMenu.closeDrawer(sideMenuContent);
				} else {
					sideMenu.openDrawer(sideMenuContent);
				}
			}
		});

		quitLayout = (RelativeLayout) findViewById(R.id.side_menu_quit);
		quitLayout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().quit();
			}
		});
	}

	private int getStatusIconResource(LinphoneCore.RegistrationState state) {
		try {
			if (state == RegistrationState.RegistrationOk) {
				return R.drawable.led_connected;
			} else if (state == RegistrationState.RegistrationProgress) {
				return R.drawable.led_inprogress;
			} else if (state == RegistrationState.RegistrationFailed) {
				return R.drawable.led_error;
			} else {
				return R.drawable.led_disconnected;
			}
		} catch (Exception e) {
			Log.e(e);
		}

		return R.drawable.led_disconnected;
	}

	private void displayMainAccount(){
		defaultAccount.setVisibility(View.VISIBLE);
		ImageView status = (ImageView) defaultAccount.findViewById(R.id.main_account_status);
		TextView address = (TextView) defaultAccount.findViewById(R.id.main_account_address);
		TextView displayName = (TextView) defaultAccount.findViewById(R.id.main_account_display_name);


		LinphoneProxyConfig proxy = LinphoneManager.getLc().getDefaultProxyConfig();
		if(proxy == null) {
			displayName.setText(getString(R.string.no_account));
			status.setVisibility(View.GONE);
			address.setText("");
			statusFragment.resetAccountStatus();
			LinphoneManager.getInstance().subscribeFriendList(false);

			defaultAccount.setOnClickListener(null);
		} else {
			address.setText(proxy.getAddress().asStringUriOnly());
			displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getAddress()));
			status.setImageResource(getStatusIconResource(proxy.getState()));
			status.setVisibility(View.VISIBLE);

			defaultAccount.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					LinphoneActivity.instance().displayAccountSettings(LinphonePreferences.instance().getDefaultAccountIndex());
					openOrCloseSideMenu(false);
				}
			});
		}
	}

	public void refreshAccounts(){
		if (LinphoneManager.getLc().getProxyConfigList() != null &&
				LinphoneManager.getLc().getProxyConfigList().length > 1) {
			accountsList.setVisibility(View.VISIBLE);
			accountsList.setAdapter(new AccountsListAdapter());
			accountsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
					if(view != null && view.getTag() != null) {
						int position = Integer.parseInt(view.getTag().toString());
						LinphoneActivity.instance().displayAccountSettings(position);
					}
					openOrCloseSideMenu(false);
				}
			});
		} else {
			accountsList.setVisibility(View.GONE);
		}
		displayMainAccount();
	}

	private void initAccounts() {
		accountsList = (ListView) findViewById(R.id.accounts_list);
		defaultAccount = (RelativeLayout) findViewById(R.id.default_account);
	}

	class AccountsListAdapter extends BaseAdapter {
		List<LinphoneProxyConfig> proxy_list;

		AccountsListAdapter() {
			proxy_list = new ArrayList<LinphoneProxyConfig>();
			refresh();
		}

		public void refresh(){
			proxy_list = new ArrayList<LinphoneProxyConfig>();
			for(LinphoneProxyConfig proxyConfig : LinphoneManager.getLc().getProxyConfigList()){
				if(proxyConfig != LinphoneManager.getLc().getDefaultProxyConfig()){
					proxy_list.add(proxyConfig);
				}
			}
		}

		public int getCount() {
			if (proxy_list != null) {
				return proxy_list.size();
			} else {
				return 0;
			}
		}

		public Object getItem(int position) {
			return proxy_list.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			LinphoneProxyConfig lpc = (LinphoneProxyConfig) getItem(position);
			if (convertView != null) {
				view = convertView;
			} else {
				view = getLayoutInflater().inflate(R.layout.side_menu_account_cell, parent, false);
			}

			ImageView status = (ImageView) view.findViewById(R.id.account_status);
			TextView address = (TextView) view.findViewById(R.id.account_address);
			String sipAddress = lpc.getAddress().asStringUriOnly();

			address.setText(sipAddress);

			int nbAccounts = LinphonePreferences.instance().getAccountCount();
			int accountIndex = 0;

			for (int i = 0; i < nbAccounts; i++) {
				String username = LinphonePreferences.instance().getAccountUsername(i);
				String domain = LinphonePreferences.instance().getAccountDomain(i);
				String id = "sip:" + username + "@" + domain;
				if (id.equals(sipAddress)) {
					accountIndex = i;
					view.setTag(accountIndex);
					break;
				}
			}
			status.setImageResource(getStatusIconResource(lpc.getState()));
			return view;
		}
	}

	//Inapp Purchase
	private void isTrialAccount() {
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
			XmlRpcHelper helper = new XmlRpcHelper();
			helper.isTrialAccountAsync(new XmlRpcListenerBase() {
				@Override
				public void onTrialAccountFetched(boolean isTrial) {
					isTrialAccount = isTrial;
					getExpirationAccount();
				}

				@Override
				public void onError(String error) {
				}
			}, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
		}
	}

	private void getExpirationAccount() {
		if(LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
			XmlRpcHelper helper = new XmlRpcHelper();
			helper.getAccountExpireAsync(new XmlRpcListenerBase() {
				@Override
				public void onAccountExpireFetched(String result) {
					if (result != null) {
						long timestamp = Long.parseLong(result);

						Calendar calresult = Calendar.getInstance();
						calresult.setTimeInMillis(timestamp);

						int diff = getDiffDays(calresult, Calendar.getInstance());
						if (diff != -1 && diff <= getResources().getInteger(R.integer.days_notification_shown)) {
							displayInappNotification(timestampToHumanDate(calresult));
						}
					}
				}

				@Override
				public void onError(String error) {
				}
			}, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
		}
	}

	public void displayInappNotification(String date) {
		Timestamp now = new Timestamp(new Date().getTime());
		if (LinphonePreferences.instance().getInappPopupTime() != null && Long.parseLong(LinphonePreferences.instance().getInappPopupTime()) > now.getTime()) {
			return;
		} else {
			long newDate = now.getTime() + getResources().getInteger(R.integer.time_between_inapp_notification);
			LinphonePreferences.instance().setInappPopupTime(String.valueOf(newDate));
		}
		if(isTrialAccount){
			LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_trial_expire), date));
		} else {
			LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_account_expire), date));
		}

	}

	private String timestampToHumanDate(Calendar cal) {
		SimpleDateFormat dateFormat;
		dateFormat = new SimpleDateFormat(getResources().getString(R.string.inapp_popup_date_format));
		return dateFormat.format(cal.getTime());
	}

	private int getDiffDays(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			return -1;
		}
		if(cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)){
			return cal1.get(Calendar.DAY_OF_YEAR) - cal2.get(Calendar.DAY_OF_YEAR);
		}
		return -1;
	}

	public String getCVSPathFromOtherUri(String path) {
		Uri contactUri = Uri.parse(path);

		ContentResolver cr = getContentResolver();
		InputStream stream = null;
		try {
			stream = cr.openInputStream(contactUri);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		StringBuffer fileContent = new StringBuffer("");
		int ch;
		try {
			while( (ch = stream.read()) != -1)
				fileContent.append((char)ch);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String data = new String(fileContent);
		return data;
	}

}

interface ContactPicked {
	void setAddresGoToDialerAndCall(String number, String name, Uri photo);
}
