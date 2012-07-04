/*
DialerActivity.java
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

import java.util.ArrayList;
import java.util.HashMap;

import org.linphone.LinphoneService.LinphoneGuiListener;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.CameraView;
import org.linphone.ui.EraseButton;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Adapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SlidingDrawer;
import android.widget.SlidingDrawer.OnDrawerScrollListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * Dialer and main activity of Linphone Android.
 * 
 * Roles are:<ul>
 * <li>Display the numpad, call/accept, address buttons</li>
 * <li>Define preferences through the menu</li>
 * <li>React to bad preference / no account set</li>
 * <li>Manage first launch</li>
 * </ul>
 *
 */
public class DialerActivity extends Activity implements LinphoneGuiListener {
	
	private TextView mStatus;

	private AddressText mAddress;
	private CallButton mCall;
	private Button mBack, mAddCall, mSwitchCamera;
	private LinearLayout mInCallControls;

	private static DialerActivity instance;
	public boolean mVisible;
	private boolean mPreventDoubleCallOnRotation;

	private AlertDialog wizardDialog;
	protected String username;
	private String key;

	private CameraView mVideoCaptureView;
	private int mCurrentCameraId = 0;

	private Camera mCamera;

	
	private static final String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static final String CURRENT_DISPLAYNAME = "org.linphone.current-displayname";
	private static final String PREVENT_DOUBLE_CALL = "prevent_call_on_phone_rotation";
	
	private static final int CONFIRM_ID = 0x668;

	/**
	 * @return null if not ready yet
	 */
	public static DialerActivity instance() { 
		return instance;
	}
	
	private String getStatusIcon(RegistrationState state) {
		if (state == RegistrationState.RegistrationOk)
			return Integer.toString(R.drawable.status_green);
		
		else if (state == RegistrationState.RegistrationNone)
			return Integer.toString(R.drawable.status_red);
		
		else if (state == RegistrationState.RegistrationProgress)
			return Integer.toString(R.drawable.status_orange);
		
		else 
			return Integer.toString(R.drawable.status_offline);
	}
	
	private void displayRegisterStatus() {
		ListView accounts = (ListView) findViewById(R.id.accounts);
		if (accounts != null) {
			accounts.setDividerHeight(0);
			ArrayList<HashMap<String,String>> hashMapAccountsStateList = new ArrayList<HashMap<String,String>>();
			for (LinphoneProxyConfig lpc : LinphoneManager.getLc().getProxyConfigList()) {
				HashMap<String, String> entitiesHashMap = new HashMap<String, String>();
				entitiesHashMap.put("Identity", lpc.getIdentity().split("sip:")[1]);
				entitiesHashMap.put("State", getStatusIcon(lpc.getState()));
				hashMapAccountsStateList.add(entitiesHashMap);
			}
			Adapter adapterForList = new SimpleAdapter(this, hashMapAccountsStateList, R.layout.accounts,
	                new String[] {"Identity", "State"},
	                new int[] { R.id.Identity, R.id.State });
			accounts.setAdapter((ListAdapter) adapterForList);
			accounts.invalidate();
		}
	}
	
	protected Dialog onCreateDialog (int id) {
		if (id == CONFIRM_ID) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.wizard_confirmation);
			
			final LayoutInflater inflater = LayoutInflater.from(this);
	    	View v = inflater.inflate(R.layout.wizard_confirm, null);
	    	builder.setView(v);
	    	
	    	Button check = (Button) v.findViewById(R.id.wizardCheckAccount);
	    	check.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					wizardDialog.dismiss();
					if (LinphonePreferencesActivity.isAccountVerified(username)) {
						SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(DialerActivity.this);
						SharedPreferences.Editor editor = prefs.edit();
						editor.putBoolean(getString(R.string.pref_activated_key) + key, true);
						editor.commit();
					} else {
						showDialog(CONFIRM_ID);
					}
				}
			});
	    	
	    	Button cancel = (Button) v.findViewById(R.id.wizardCancel);
	    	cancel.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					wizardDialog.dismiss();
				}
			});
	    	
			wizardDialog = builder.create();
			return wizardDialog;
		}
		return null;
	}
	
	private void verifiyAccountsActivated() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
		
		for (int i = 0; i < nbAccounts; i++) {
			String key = (i == 0 ? "" : Integer.toString(i));
			boolean createdByWizard = prefs.getBoolean(getString(R.string.pref_wizard_key) + key, false);
			boolean activated = prefs.getBoolean(getString(R.string.pref_activated_key) + key, true);
			if (createdByWizard && !activated) {
				//Check if account has been activated since
				String username = prefs.getString(getString(R.string.pref_username_key) + key, "");
				activated = LinphonePreferencesActivity.isAccountVerified(username);
				if (activated) {
					SharedPreferences.Editor editor = prefs.edit();
					editor.putBoolean(getString(R.string.pref_activated_key) + key, true);
					editor.commit();
				} else {
					this.username = username;
					this.key = key;
					showDialog(CONFIRM_ID);
				}
			}
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		if (Version.isXLargeScreen(this)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		setContentView(R.layout.dialer);

		mAddress = (AddressText) findViewById(R.id.SipUri); 
		EraseButton erase = (EraseButton) findViewById(R.id.Erase);
		erase.setAddressWidget(mAddress);
		erase.requestFocus();

		mCall = (CallButton) findViewById(R.id.Call);
		mCall.setAddressWidget(mAddress);
		mBack = (Button) findViewById(R.id.Back);
		mAddCall = (Button) findViewById(R.id.AddCall);
		mInCallControls = (LinearLayout) findViewById(R.id.InCallControls);
		mStatus =  (TextView) findViewById(R.id.status_label);
        
		if (mBack != null) {
			mBack.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
					if (call != null && call.getCurrentParamsCopy().getVideoEnabled())
						LinphoneActivity.instance().startVideoActivity(call, 0);
					else
						LinphoneActivity.instance().startIncallActivity();
				}
			});
		}
		
		if (mAddCall != null) {
			mAddCall.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
					if (call != null && !call.isInConference()) {
						LinphoneManager.getLc().pauseCall(call);
					} else {
						LinphoneManager.getLc().leaveConference();
					}
						
					try {
						LinphoneManager.getLc().invite(mAddress.getText().toString());
					} catch (LinphoneCoreException e) {
						Log.e(e);
						Toast.makeText(DialerActivity.this, R.string.error_adding_new_call, Toast.LENGTH_LONG).show();
					}
				}
			});
		}
		
		if (Version.isXLargeScreen(this)) {
			tryToInitTabletUI();
		}
		
		SlidingDrawer drawer = (SlidingDrawer) findViewById(R.id.drawer);
		if (drawer != null) {
			boolean disable_sliding_drawer = getResources().getBoolean(R.bool.disable_dialer_sliding_drawer);
			
			if (disable_sliding_drawer) {
				drawer.close();
				drawer.lock();
			} else {
				drawer.setOnDrawerScrollListener(new OnDrawerScrollListener() {
					public void onScrollEnded() {
						
					}
		
					public void onScrollStarted() {
						displayRegisterStatus();
					}
				});
			}
		}

		AddressAware numpad = (AddressAware) findViewById(R.id.Dialer);
		if (numpad != null)
			numpad.setAddressWidget(mAddress);

		// call to super must be done after all fields are initialized
		// because it may call this.enterIncallMode
		super.onCreate(savedInstanceState);

		mPreventDoubleCallOnRotation=savedInstanceState != null
				&& savedInstanceState.getBoolean(PREVENT_DOUBLE_CALL, false);
		if (mPreventDoubleCallOnRotation) {
			Log.i("Prevent launching a new call on rotation");
		} else {
			checkIfOutgoingCallIntentReceived();
		}

		instance = this;
		super.onCreate(savedInstanceState);
		
		verifiyAccountsActivated();
		
		displayRegisterStatus();
		mCall.requestFocus();
	}


    private synchronized void tryToInitTabletUI() {
		final int numberOfCameras = Camera.getNumberOfCameras();
		
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                mCurrentCameraId = i;
            }
        }
    	
    	mVideoCaptureView = (CameraView) findViewById(R.id.video_background);
		if (mVideoCaptureView != null)
		{
			if (mCamera == null) {
				mCamera = Camera.open(mCurrentCameraId);
			}
			mVideoCaptureView.setCamera(mCamera, mCurrentCameraId);
			mCamera.startPreview();
		}
		
		mSwitchCamera = (Button) findViewById(R.id.switch_camera);
		if (mSwitchCamera != null)
		{
			if (AndroidCameraConfiguration.hasSeveralCameras())
				mSwitchCamera.setVisibility(View.VISIBLE);
			
			mSwitchCamera.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					mCurrentCameraId = (mCurrentCameraId + 1) % numberOfCameras;
					mCamera.release();
					mVideoCaptureView.setCamera(null, -1);
					
					mCamera = Camera.open(mCurrentCameraId);
					mVideoCaptureView.switchCamera(mCamera, mCurrentCameraId);
					mCamera.startPreview();
				}
			});
		}
	}

	private void checkIfOutgoingCallIntentReceived() {
    	if (getIntent().getData() == null) return;

    	if (!LinphoneService.isReady() || LinphoneManager.getLc().isIncall()) {
    		Log.w("Outgoing call aborted as LinphoneService"
    				+ " is not ready or we are already in call");
    		return;
    	}
    	
    	// Fix call from contact issue
    	if (getIntent().getData().getSchemeSpecificPart() != null)
    		getIntent().setAction(Intent.ACTION_CALL);
    	
    	newOutgoingCall(getIntent());
	}



    @Override
    protected void onPause() {
    	super.onPause();
    	mVisible = false;

    	if (mCamera != null) {
            mCamera.release();
			mVideoCaptureView.setCamera(null, -1);
            mCamera = null;
        }
    }


	

	


	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putCharSequence(CURRENT_ADDRESS, mAddress.getText());
		if (mAddress.getDisplayedName() != null)
			savedInstanceState.putString(CURRENT_DISPLAYNAME,mAddress.getDisplayedName());
		savedInstanceState.putBoolean(PREVENT_DOUBLE_CALL, mPreventDoubleCallOnRotation);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedState) {
		super.onRestoreInstanceState(savedState);
		CharSequence addr = savedState.getCharSequence(CURRENT_ADDRESS);
		if (addr != null && mAddress != null) {
			mAddress.setText(addr); 
		}
		mAddress.setDisplayedName(savedState.getString(CURRENT_DISPLAYNAME));
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		instance=null;
	}





	public void newOutgoingCall(Intent intent) {
		String scheme = intent.getData().getScheme();
		if (scheme.startsWith("imto")) {
			mAddress.setText("sip:" + intent.getData().getLastPathSegment());
		} else if (scheme.startsWith("call") || scheme.startsWith("sip")) {
			mAddress.setText(intent.getData().getSchemeSpecificPart());
		} else {
			Log.e("Unknown scheme: ",scheme);
			mAddress.setText(intent.getData().getSchemeSpecificPart());
		}

		mAddress.clearDisplayedName();
		intent.setData(null);
		// Setting data to null is no use when the activity is recreated
		// as the intent is immutable.
		// https://groups.google.com/forum/#!topic/android-developers/vrLdM5mKeoY
		mPreventDoubleCallOnRotation=true;
		setIntent(intent);

		LinphoneManager.getInstance().newOutgoingCall(mAddress);
	}

	
	public void setContactAddress(String aContact,String aDisplayName, Uri photo) {
		mAddress.setText(aContact);
		mAddress.setDisplayedName(aDisplayName);
		mAddress.setPictureUri(photo);
	}

	


	
	
	/***** GUI delegates for listener LinphoneServiceListener *************/
	@Override
	public void onDisplayStatus(String message) {
		mStatus.setText(message);
	}

	@Override
	public void onAlreadyInCall() {
		showToast(R.string.warning_already_incall);
	}

	@Override
	public void onCannotGetCallParameters() { 
		showToast(R.string.error_cannot_get_call_parameters,mAddress.getText());
	}

	@Override
	public void onWrongDestinationAddress() {
		showToast(R.string.warning_wrong_destination_address, mAddress.getText());
	}

	private void showToast(int id, String txt) {
		final String msg = String.format(getString(id), txt);
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}
	private void showToast(int id, CharSequence txt) {
		showToast(id, txt.toString());
	}
	private void showToast(int id) {
		Toast.makeText(this, getString(id), Toast.LENGTH_LONG).show();
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, State s, String m) {
		if (mVideoCaptureView != null && mCamera == null && !LinphoneManager.getLc().isIncall())
		{
			if (mInCallControls != null)
				mInCallControls.setVisibility(View.GONE);
			mCall.setVisibility(View.VISIBLE);
			
			if (AndroidCameraConfiguration.hasSeveralCameras() && mSwitchCamera != null)
				mSwitchCamera.setVisibility(View.VISIBLE);
			
			mCamera = Camera.open(mCurrentCameraId);
			mVideoCaptureView.switchCamera(mCamera, mCurrentCameraId);
			mCamera.startPreview();
		}
	}
	
	public void onGlobalStateChangedToOn(String message) {
		mCall.setEnabled(!LinphoneManager.getLc().isIncall());

		if (getIntent().getData() != null) {
			checkIfOutgoingCallIntentReceived();
		}
	}

	@Override
	protected void onResume() {
		// When coming back from a video call, if the phone orientation is different
		// Android will destroy the previous Dialer and create a new one.
		// Unfortunately the "call end" status event is received in the meanwhile
		// and set to the to be destroyed Dialer.
		// Note1: We wait as long as possible before setting the last message.
		// Note2: Linphone service is in charge of instantiating LinphoneManager
		mVisible = true;
		mStatus.setText(LinphoneManager.getInstance().getLastLcStatusMessage());
        
		super.onResume();
		
		if (mInCallControls != null)
			mInCallControls.setVisibility(View.GONE);
		mCall.setVisibility(View.VISIBLE);
		
		if (AndroidCameraConfiguration.hasSeveralCameras() && mSwitchCamera != null)
			mSwitchCamera.setVisibility(View.VISIBLE);
		
		boolean callEstablished = isCallEstablished();
		boolean isInCall = LinphoneManager.getLc().getCallsNb() > 1 || (LinphoneManager.getLc().getCallsNb() == 1 && callEstablished);
		
		if (mVideoCaptureView != null && mCamera == null && !LinphoneManager.getLc().isIncall()) {
			mCamera = Camera.open(mCurrentCameraId);
			mVideoCaptureView.switchCamera(mCamera, mCurrentCameraId);
			mCamera.startPreview();
		} else if (isInCall) {
			if (mInCallControls != null) {
				mInCallControls.setVisibility(View.VISIBLE);
				mCall.setVisibility(View.GONE);
			}
			
			if (mSwitchCamera != null)
				mSwitchCamera.setVisibility(View.INVISIBLE);
		}
	}

	private boolean isCallEstablished()
	{
		if (LinphoneManager.getLc().getCalls().length == 0)
			return false;
		
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		LinphoneCall.State state = call.getState();
		
		return state == LinphoneCall.State.Connected ||
				state == LinphoneCall.State.CallUpdated ||
				state == LinphoneCall.State.CallUpdatedByRemote ||
				state == LinphoneCall.State.Paused ||
				state == LinphoneCall.State.PausedByRemote ||
				state == LinphoneCall.State.StreamsRunning ||
				state == LinphoneCall.State.Pausing ||
				state == LinphoneCall.State.Resuming;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
		boolean isInCall = LinphoneManager.getLc().isIncall();
		isInCall = isInCall || LinphoneManager.getLc().getCallsNb() > 0;
		if (keyCode == KeyEvent.KEYCODE_BACK && isInCall) {
			// If we are in call on dialer, we go back to the incall view
			LinphoneActivity.instance().startIncallActivity();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
