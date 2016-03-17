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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.linphone.LinphoneManager.AddressType;
import org.linphone.assistant.AssistantActivity;
import org.linphone.assistant.RemoteProvisioningLoginActivity;
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
import org.linphone.ui.AddressText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
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
import android.view.animation.AnimationUtils;
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

/**
 * @author Sylvain Berfini
 */
public class LinphoneActivity extends Activity implements OnClickListener, ContactPicked, ActivityCompat.OnRequestPermissionsResultCallback {
	public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static final int SETTINGS_ACTIVITY = 123;
	private static final int FIRST_LOGIN_ACTIVITY = 101;
	private static final int REMOTE_PROVISIONING_LOGIN_ACTIVITY = 102;
	private static final int CALL_ACTIVITY = 19;
	private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 200;
	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 201;
	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO_INCOMING_CALL = 203;

	private static LinphoneActivity instance;

	private StatusFragment statusFragment;
	private TextView missedCalls, missedChats;
	private RelativeLayout contacts, history, dialer, chat;
	private View contacts_selected, history_selected, dialer_selected, chat_selected;
	private RelativeLayout mTopBar;
	private ImageView cancel;
	private FragmentsAvailable currentFragment, nextFragment;
	private List<FragmentsAvailable> fragmentsHistory;
	private Fragment dialerFragment, chatListFragment, historyListFragment, contactListFragment;
	private ChatFragment chatFragment;
	private Fragment.SavedState dialerSavedState;
	private boolean newProxyConfig;
	private boolean isAnimationDisabled = true, emptyFragment = false, permissionAsked = false;
	private OrientationEventListener mOrientationHelper;
	private LinphoneCoreListenerBase mListener;
	private LinearLayout mTabBar;

	private DrawerLayout sideMenu;
	private String[] sideMenuItems;
	private RelativeLayout sideMenuContent, quitLayout, defaultAccount;
	private ListView accountsList, sideMenuItemList;
	private ImageView menu;
	private boolean fetchedContactsOnce = false;

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
		super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

		if (!LinphoneManager.isInstanciated()) {
			Log.e("No service running: avoid crash by starting the launch", this.getClass().getName());
			finish();
			startActivity(getIntent().setClass(this, LinphoneLauncherActivity.class));
			return;
		}

		boolean useFirstLoginActivity = getResources().getBoolean(R.bool.display_account_wizard_at_first_start);
		if (LinphonePreferences.instance().isProvisioningLoginViewEnabled()) {
			Intent wizard = new Intent();
			wizard.setClass(this, RemoteProvisioningLoginActivity.class);
			wizard.putExtra("Domain", LinphoneManager.getInstance().wizardLoginViewDomain);
			startActivityForResult(wizard, REMOTE_PROVISIONING_LOGIN_ACTIVITY);
		} else if (useFirstLoginActivity && LinphonePreferences.instance().isFirstLaunch() || LinphoneManager.getLc().getProxyConfigList().length == 0) {
			if (LinphonePreferences.instance().getAccountCount() > 0) {
				LinphonePreferences.instance().firstLaunchSuccessful();
			} else {
				startActivityForResult(new Intent().setClass(this, AssistantActivity.class), FIRST_LOGIN_ACTIVITY);
			}
		}

		//TODO rework
		if (getResources().getBoolean(R.bool.use_linphone_tag) && getPackageManager().checkPermission(Manifest.permission.WRITE_SYNC_SETTINGS, getPackageName()) == PackageManager.PERMISSION_GRANTED) {
			ContactsManager.getInstance().initializeSyncAccount(getApplicationContext(), getContentResolver());
		} else {
			ContactsManager.getInstance().initializeContactManager(getApplicationContext(), getContentResolver());
		}

		setContentView(R.layout.main);
		instance = this;
		fragmentsHistory = new ArrayList<FragmentsAvailable>();

		initButtons();
		initSideMenu();

		currentFragment = nextFragment = FragmentsAvailable.DIALER;
		fragmentsHistory.add(currentFragment);
		if (savedInstanceState == null) {
			if (findViewById(R.id.fragmentContainer) != null) {
				dialerFragment = new DialerFragment();
				dialerFragment.setArguments(getIntent().getExtras());
				getFragmentManager().beginTransaction().add(R.id.fragmentContainer, dialerFragment, currentFragment.toString()).commit();
				selectMenu(FragmentsAvailable.DIALER);
			}
		}

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
				if(!displayChatMessageNotification(message.getFrom().asStringUriOnly())) {
					cr.markAsRead();
				}
		        displayMissedChats(getUnreadMessageCount());
		        if (chatListFragment != null && chatListFragment.isVisible()) {
		            ((ChatListFragment) chatListFragment).refresh();
		        }
			}

			@Override
			public void authInfoRequested(LinphoneCore lc, String realm, String username, String domain) {
				//authInfoPassword = displayWrongPasswordDialog(username, realm, domain);
				//authInfoPassword.show();
			}

			@Override
			public void registrationState(LinphoneCore lc, LinphoneProxyConfig proxy, LinphoneCore.RegistrationState state, String smessage) {
				if (state.equals(RegistrationState.RegistrationCleared)) {
					if (lc != null) {
						LinphoneAuthInfo authInfo = lc.findAuthInfo(proxy.getIdentity(), proxy.getRealm(), proxy.getDomain());
						if (authInfo != null)
							lc.removeAuthInfo(authInfo);
					}
				}

				refreshAccounts();

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
					if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED || LinphonePreferences.instance().audioPermAsked()) {
						startActivity(new Intent(LinphoneActivity.instance(), CallIncomingActivity.class));
					} else {
						checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO_INCOMING_CALL);
					}
				} else if (state == State.OutgoingInit || state == State.OutgoingProgress) {
					if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED || LinphonePreferences.instance().audioPermAsked()) {
						startActivity(new Intent(LinphoneActivity.instance(), CallOutgoingActivity.class));
					} else {
						checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO);
					}
				} else if (state == State.CallEnd || state == State.Error || state == State.CallReleased) {
					// Convert LinphoneCore message for internalization
					if (message != null && call.getErrorInfo().getReason() == Reason.Declined) {
						displayCustomToast(getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
					} else if (message != null && call.getErrorInfo().getReason() == Reason.NotFound) {
						displayCustomToast(getString(R.string.error_user_not_found), Toast.LENGTH_SHORT);
					} else if (message != null && call.getErrorInfo().getReason() == Reason.Media) {
						displayCustomToast(getString(R.string.error_incompatible_media), Toast.LENGTH_SHORT);
					} else if (message != null && state == State.Error) {
						displayCustomToast(getString(R.string.error_unknown) + " - " + message, Toast.LENGTH_SHORT);
					}
					resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
				}

				int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
				displayMissedCalls(missedCalls);
			}
		};

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
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

		LinphoneManager.getLc().setDeviceRotation(rotation);
		mAlwaysChangingPhoneAngle = rotation;

		updateAnimationsState();
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
		nextFragment = newFragmentType;

		if (currentFragment == FragmentsAvailable.DIALER) {
			try {
				dialerSavedState = getFragmentManager().saveFragmentInstanceState(dialerFragment);
			} catch (Exception e) {
			}
		}

		Fragment newFragment = null;

		switch (newFragmentType) {
		case HISTORY_LIST:
			newFragment = new HistoryListFragment();
			historyListFragment = newFragment;
			break;
		case HISTORY_DETAIL:
			newFragment = new HistoryDetailFragment();
			break;
		case CONTACTS_LIST:
			newFragment = new ContactsListFragment();
			contactListFragment = newFragment;
			break;
		case CONTACT_DETAIL:
			newFragment = new ContactDetailsFragment();
			break;
			case CONTACT_EDITOR:
			newFragment = new ContactEditorFragment();
			break;
		case DIALER:
			newFragment = new DialerFragment();
			if (extras == null) {
				newFragment.setInitialSavedState(dialerSavedState);
			}
			dialerFragment = newFragment;
			break;
		case SETTINGS:
			newFragment = new SettingsFragment();
			break;
		case ACCOUNT_SETTINGS:
			newFragment = new AccountPreferencesFragment();
			break;
		case ABOUT:
			newFragment = new AboutFragment();
			break;
		case EMPTY:
			newFragment = new EmptyFragment();
			break;
		case CHAT_LIST:
			newFragment = new ChatListFragment();
			chatListFragment = newFragment;
			break;
		case CHAT:
			newFragment = new ChatFragment();
			break;
		default:
			break;
		}

		if (newFragment != null) {
			newFragment.setArguments(extras);
			if (isTablet()) {
				changeFragmentForTablets(newFragment, newFragmentType, withoutAnimation);
			} else {
				changeFragment(newFragment, newFragmentType, withoutAnimation);
			}
		}
	}

	private void updateAnimationsState() {
		isAnimationDisabled = getResources().getBoolean(R.bool.disable_animations) || !LinphonePreferences.instance().areAnimationsEnabled();
	}

	public boolean isAnimationDisabled() {
		return isAnimationDisabled;
	}

	private void changeFragment(Fragment newFragment, FragmentsAvailable newFragmentType, boolean withoutAnimation) {
		FragmentTransaction transaction = getFragmentManager().beginTransaction();

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
				|| newFragmentType != FragmentsAvailable.CONTACTS_LIST
				|| newFragmentType != FragmentsAvailable.CHAT_LIST
				|| newFragmentType != FragmentsAvailable.HISTORY_LIST) {
			transaction.addToBackStack(newFragmentType.toString());
		}
		transaction.replace(R.id.fragmentContainer, newFragment, newFragmentType.toString());
		transaction.commitAllowingStateLoss();
		getFragmentManager().executePendingTransactions();

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

	public void displayChatList() {
		Bundle extras = new Bundle();
		changeCurrentFragment(FragmentsAvailable.CHAT_LIST, extras);
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

	public void displayAssistant() {
		startActivity(new Intent(LinphoneActivity.this, AssistantActivity.class));
	}

	public boolean displayChatMessageNotification(String address){
		if(chatFragment != null) {
			if(chatFragment.getSipUri().equals(address)) {
				return false;
			}
		}
		return true;
	}

	public int getUnreadMessageCount() {
		int count = 0;
		LinphoneChatRoom[] chats = LinphoneManager.getLc().getChatRooms();
		for (LinphoneChatRoom chatroom : chats) {
			count += chatroom.getUnreadMessagesCount();
		}
		return count;
	}

	public void displayChat(String sipUri) {
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
				chatFragment.changeDisplayedChat(sipUri, displayName, pictureUri);
			} else {
				Bundle extras = new Bundle();
				extras.putString("SipUri", sipUri);
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
				displayChat(sipUri);
			} else {
				Bundle extras = new Bundle();
				extras.putString("SipUri", sipUri);
				if (sipUri != null  && lAddress.getDisplayName() != null) {
					extras.putString("DisplayName", displayName);
					extras.putString("PictureUri", pictureUri);
					extras.putString("ThumbnailUri", thumbnailUri);
				}
				changeCurrentFragment(FragmentsAvailable.CHAT, extras);
			}
		}

		if (chatListFragment != null && chatListFragment.isVisible()) {
			((ChatListFragment) chatListFragment).refresh();
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
			if(isTablet()) {
				if (historyListFragment != null && historyListFragment.isVisible()) {
					((HistoryListFragment) historyListFragment).displayFirstLog();
				}
			}
		} else if (id == R.id.contacts) {
			changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, null);
			contacts_selected.setVisibility(View.VISIBLE);
			if(isTablet()) {
				if (contactListFragment != null && contactListFragment.isVisible()) {
					((ContactsListFragment) contactListFragment).displayFirstContact();
				}
			}
		} else if (id == R.id.dialer) {
			changeCurrentFragment(FragmentsAvailable.DIALER, null);
			dialer_selected.setVisibility(View.VISIBLE);
		} else if (id == R.id.chat) {
			changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
			chat_selected.setVisibility(View.VISIBLE);
			if(isTablet()) {
				if (chatListFragment != null && chatListFragment.isVisible()) {
					((ChatListFragment) chatListFragment).displayFirstChat();
				}
			}
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
		dialerFragment = fragment;
		// Hack to maintain soft input flags
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
	}

	public void updateChatFragment(ChatFragment fragment) {
		chatFragment = fragment;
	}

	public void updateChatListFragment(ChatListFragment fragment) {
		chatListFragment = fragment;
	}

	public void updateStatusFragment(StatusFragment fragment) {
		statusFragment = fragment;
	}

	public void displaySettings() {
		changeCurrentFragment(FragmentsAvailable.SETTINGS, null);
	}

	public void applyConfigChangesIfNeeded() {
		if (nextFragment != FragmentsAvailable.SETTINGS && nextFragment != FragmentsAvailable.ACCOUNT_SETTINGS) {
			updateAnimationsState();
		}
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

	public List<String> getChatList() {
		return getChatStorage().getChatList();
	}

	public List<String> getDraftChatList() {
		return getChatStorage().getDrafts();
	}

	public List<ChatMessage> getChatMessages(String correspondent) {
		return getChatStorage().getMessages(correspondent);
	}

	public void removeFromChatList(String sipUri) {
		getChatStorage().removeDiscussion(sipUri);
	}

	public void removeFromDrafts(String sipUri) {
		getChatStorage().deleteDraft(sipUri);
	}



	public void updateMissedChatCount() {
		displayMissedChats(getUnreadMessageCount());
	}

	public int onMessageSent(String to, String message) {
		getChatStorage().deleteDraft(to);
		if (chatListFragment != null && chatListFragment.isVisible()) {
			((ChatListFragment) chatListFragment).refresh();
		}

		return getChatStorage().saveTextMessage("", to, message, System.currentTimeMillis());
	}

	public int onMessageSent(String to, Bitmap image, String imageURL) {
		getChatStorage().deleteDraft(to);
		return getChatStorage().saveImageMessage("", to, image, imageURL, System.currentTimeMillis());
	}

	public void onMessageStateChanged(String to, String message, int newState) {
		getChatStorage().updateMessageStatus(to, message, newState);
	}

	public void onImageMessageStateChanged(String to, int id, int newState) {
		getChatStorage().updateMessageStatus(to, id, newState);
	}

	public void displayMissedCalls(final int missedCallsCount) {
		if (missedCallsCount > 0) {
			missedCalls.setText(missedCallsCount + "");
			missedCalls.setVisibility(View.VISIBLE);
			if (!isAnimationDisabled) {
				missedCalls.startAnimation(AnimationUtils.loadAnimation(LinphoneActivity.this, R.anim.bounce));
			}
		} else {
			LinphoneManager.getLc().resetMissedCallsCount();
			missedCalls.clearAnimation();
			missedCalls.setVisibility(View.GONE);
		}
	}

	private void displayMissedChats(final int missedChatCount) {
		if (missedChatCount > 0) {
			missedChats.setText(missedChatCount + "");
			missedChats.setVisibility(View.VISIBLE);
			if (!isAnimationDisabled) {
				missedChats.startAnimation(AnimationUtils.loadAnimation(LinphoneActivity.this, R.anim.bounce));
			}
			if(missedChatCount > 99){
				//TODO
			}
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
		Drawable d = new ColorDrawable(getResources().getColor(R.color.colorC));
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
		Drawable d = new ColorDrawable(getResources().getColor(R.color.colorC));
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

	public void setAddressAndGoToDialer(String number) {
		Bundle extras = new Bundle();
		extras.putString("SipUri", number);
		changeCurrentFragment(FragmentsAvailable.DIALER, extras);
	}

	@Override
	public void goToDialer() {
		changeCurrentFragment(FragmentsAvailable.DIALER, null);
	}

	public void startVideoActivity(LinphoneCall currentCall) {
		Intent intent = new Intent(this, CallActivity.class);
		intent.putExtra("VideoEnabled", true);
		startOrientationSensor();
		startActivityForResult(intent, CALL_ACTIVITY);
	}

	public void startIncallActivity(LinphoneCall currentCall) {
		Intent intent = new Intent(this, CallActivity.class);
		intent.putExtra("VideoEnabled", false);
		startOrientationSensor();
		startActivityForResult(intent, CALL_ACTIVITY);
	}

	public void sendLogs(Context context, String info){
		final String appName = context.getString(R.string.app_name);

		Intent i = new Intent(Intent.ACTION_SEND);
		i.putExtra(Intent.EXTRA_EMAIL, new String[]{ context.getString(R.string.about_bugreport_email) });
		i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
		i.putExtra(Intent.EXTRA_TEXT, info);
		i.setType("application/zip");

		try {
			startActivity(Intent.createChooser(i, "Send mail..."));
		} catch (android.content.ActivityNotFoundException ex) {
			Log.e(ex);
		}
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
				if (currentCall != null && currentCall.cameraEnabled() && currentCall.getCurrentParamsCopy().getVideoEnabled()) {
					lc.updateCall(currentCall, null);
				}
			}
		}
	}

	private void initInCallMenuLayout(boolean callTransfer) {
		selectMenu(FragmentsAvailable.DIALER);
		if (dialerFragment != null) {
			((DialerFragment) dialerFragment).resetLayout(callTransfer);
		}
	}

	public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
		if (dialerFragment != null) {
			((DialerFragment) dialerFragment).resetLayout(false);
		}

		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
			LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
			if (call.getState() == LinphoneCall.State.IncomingReceived) {
				startActivity(new Intent(LinphoneActivity.this, CallIncomingActivity.class));
			} else if (call.getCurrentParamsCopy().getVideoEnabled()) {
				startVideoActivity(call);
			} else {
				startIncallActivity(call);
			}
		}
	}

	public FragmentsAvailable getCurrentFragment() {
		return currentFragment;
	}

	public ChatStorage getChatStorage() {
		return ChatStorage.getInstance();
	}

	public void addContact(String displayName, String sipUri)
	{
		Bundle extras = new Bundle();
		extras.putSerializable("NewSipAdress", sipUri);
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
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
			if (data.getExtras().getBoolean("Exit", false)) {
				quit();
			} else {
				FragmentsAvailable newFragment = (FragmentsAvailable) data.getExtras().getSerializable("FragmentToDisplay");
				changeCurrentFragment(newFragment, null, true);
				selectMenu(newFragment);
			}
		} else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == CALL_ACTIVITY) {
			getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
			boolean callTransfer = data == null ? false : data.getBooleanExtra("Transfer", false);
			boolean chat = data == null ? false : data.getBooleanExtra("chat", false);
			if(chat){
				displayChatList();
			}
			if (LinphoneManager.getLc().getCallsNb() > 0) {
				initInCallMenuLayout(callTransfer);
			} else {
				resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	protected void onPause() {
		getIntent().putExtra("PreviousActivity", 0);
		super.onPause();
	}

	public void checkAndRequestPermission(String permission, int result) {
		if (getPackageManager().checkPermission(permission, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			if (!ActivityCompat.shouldShowRequestPermissionRationale(this,permission) && !permissionAsked) {
				permissionAsked = true;
				if(LinphonePreferences.instance().shouldInitiateVideoCall() ||
						LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
					ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, permission}, result);
				} else {
					ActivityCompat.requestPermissions(this, new String[]{permission}, result);
				}
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_RECORD_AUDIO:
				startActivity(new Intent(this, CallOutgoingActivity.class));
				LinphonePreferences.instance().neverAskAudioPerm();
				break;
			case PERMISSIONS_REQUEST_RECORD_AUDIO_INCOMING_CALL:
				startActivity(new Intent(this, CallIncomingActivity.class));
				LinphonePreferences.instance().neverAskAudioPerm();
				break;
		}
		permissionAsked = false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!LinphoneService.isReady())  {
			startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
		}

		if (getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName()) == PackageManager.PERMISSION_GRANTED && !fetchedContactsOnce) {
			ContactsManager.getInstance().enableContactsAccess();
			ContactsManager.getInstance().fetchContacts();
			fetchedContactsOnce = true;
		} else {
			checkAndRequestPermission(Manifest.permission.READ_CONTACTS, PERMISSIONS_REQUEST_READ_CONTACTS);
		}

		updateMissedChatCount();

		displayMissedCalls(LinphoneManager.getLc().getMissedCallsCount());

		LinphoneManager.getInstance().changeStatusToOnline();

		if (getIntent().getIntExtra("PreviousActivity", 0) != CALL_ACTIVITY){
			if (LinphoneManager.getLc().getCalls().length > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
				LinphoneCall.State callState = call.getState();
				if (callState == State.IncomingReceived) {
					if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED || LinphonePreferences.instance().audioPermAsked()) {
						startActivity(new Intent(this, CallIncomingActivity.class));
					} else {
						checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO_INCOMING_CALL);
					}
				} else if (callState == State.OutgoingInit || callState == State.OutgoingProgress || callState == State.OutgoingRinging) {
					if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED || LinphonePreferences.instance().audioPermAsked()) {
						startActivity(new Intent(this, CallOutgoingActivity.class));
					} else {
						checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO);
					}
				} else {
					if (call.getCurrentParamsCopy().getVideoEnabled()) {
						startVideoActivity(call);
					} else {
						startIncallActivity(call);
					}
				}
			}
		}
	}

	@Override
	protected void onDestroy() {
		if (mOrientationHelper != null) {
			mOrientationHelper.disable();
			mOrientationHelper = null;
		}

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
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

		Bundle extras = intent.getExtras();
		if (extras != null && extras.getBoolean("GoToChat", false)) {
			LinphoneService.instance().removeMessageNotification();
			String sipUri = extras.getString("ChatContactSipUri");
			displayChat(sipUri);
		} else if (extras != null && extras.getBoolean("Notification", false)) {
			if (LinphoneManager.getLc().getCallsNb() > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
				if (call.getCurrentParamsCopy().getVideoEnabled()) {
					startVideoActivity(call);
				} else {
					startIncallActivity(call);
				}
			}
		} else {
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
			}
			if (LinphoneManager.getLc().getCalls().length > 0) {
				LinphoneCall calls[] = LinphoneManager.getLc().getCalls();
				if (calls.length > 0) {
					LinphoneCall call = calls[0];

					if (call != null && call.getState() != LinphoneCall.State.IncomingReceived) {
						if (call.getCurrentParamsCopy().getVideoEnabled()) {
							//startVideoActivity(call);
						} else {
							//startIncallActivity(call);
						}
					}
				}

				// If a call is ringing, start incomingcallactivity
				Collection<LinphoneCall.State> incoming = new ArrayList<LinphoneCall.State>();
				incoming.add(LinphoneCall.State.IncomingReceived);
				if (LinphoneUtils.getCallsInState(LinphoneManager.getLc(), incoming).size() > 0) {
					if (CallActivity.isInstanciated()) {
						CallActivity.instance().startIncomingCallActivity();
					} else {
						if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED || LinphonePreferences.instance().audioPermAsked()) {
							startActivity(new Intent(this, CallIncomingActivity.class));
						} else {
							checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO_INCOMING_CALL);
						}
					}
				}
			}
		}
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
			} else {
				if (isTablet()) {
					if (currentFragment == FragmentsAvailable.SETTINGS) {
						updateAnimationsState();
					}
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
		sideMenuItems = new String[]{getResources().getString(R.string.menu_assistant),getResources().getString(R.string.menu_settings),getResources().getString(R.string.menu_about)};
		sideMenuContent = (RelativeLayout) findViewById(R.id.side_menu_content);
		sideMenuItemList = (ListView)findViewById(R.id.item_list);
		menu = (ImageView) findViewById(R.id.side_menu_button);

		sideMenuItemList.setAdapter(new ArrayAdapter<String>(this, R.layout.side_menu_item_cell, sideMenuItems));
		sideMenuItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				if(sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_settings))){
					LinphoneActivity.instance().displaySettings();
				}
				if(sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_about))){
					LinphoneActivity.instance().displayAbout();
				}
				if(sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_assistant))){
					LinphoneActivity.instance().displayAssistant();
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
			e.printStackTrace();
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

			defaultAccount.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					LinphoneActivity.instance().displayAccountSettings(0);
					openOrCloseSideMenu(false);
				}
			});
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
		if(LinphoneManager.getLc().getProxyConfigList().length > 1) {
			accountsList.setVisibility(View.VISIBLE);
			accountsList.setAdapter(new AccountsListAdapter());
			accountsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
					if(view != null) {
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

		refreshAccounts();
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
}

interface ContactPicked {
	void setAddresGoToDialerAndCall(String number, String name, Uri photo);
	void goToDialer();
}
