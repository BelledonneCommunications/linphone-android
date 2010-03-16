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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCore.GeneralState;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class DialerActivity extends Activity implements LinphoneCoreListener {
	
	private TextView mAddress;
	private TextView mDisplayNameView;
	
	private TextView mStatus;
	private ImageButton mCall;
	private ImageButton mDecline;
	private ImageButton mHangup;
	private Button mErase;
	
	private Button mZero;
	private Button mOne;
	private Button mTwo;
	private Button mThree ;
	private Button mFour;
	private Button mFive;
	private Button mSix;
	private Button mSeven;
	private Button mEight;
	private Button mNine;
	private Button mStar;
	private Button mHash;
	
	private ToggleButton mMute;
	private ToggleButton mSpeaker;
	
	private LinearLayout mCallControlRow;
	private LinearLayout mInCallControlRow;
	private LinearLayout mAddressLayout;
	private LinearLayout mInCallAddressLayout;
	
	private static DialerActivity theDialer;
	
	private String mDisplayName;
	private AudioManager mAudioManager;
	
	/**
	 * 
	 * @return nul if not ready yet
	 */
	public static DialerActivity getDialer() {
		if (theDialer == null) {
			return null;
		} else {
			return theDialer;
		}
	}
	public void setContactAddress(String aContact,String aDisplayName) {
		mAddress.setText(aContact);
		mDisplayName = aDisplayName;
	}
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		try {
			
			
			mAddress = (TextView) findViewById(R.id.SipUri);
			mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
			mErase = (Button)findViewById(R.id.Erase); 
			
			mErase.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					mAddress.getEditableText().delete(mAddress.getEditableText().length()-1, mAddress.getEditableText().length());
				}
			});
			
			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					if (lLinphoneCore.isInComingInvitePending()) {
						lLinphoneCore.acceptCall();
						return;
					}
					if (lLinphoneCore.isIncall()) {
						Toast toast = Toast.makeText(DialerActivity.this, getString(R.string.warning_already_incall), Toast.LENGTH_LONG);
						toast.show();
						return;
					}
					String lRawAddress = mAddress.getText().toString();
					String lCallingUri=null;
					if (lRawAddress.startsWith("sip:")) {
						lCallingUri=lRawAddress;
					} else {
					LinphoneProxyConfig lProxy = lLinphoneCore.getDefaultProxyConfig();
					String lDomain=null;
					String lNormalizedNumber=lRawAddress;
						if (lProxy!=null) {
							lNormalizedNumber = lProxy.normalizePhoneNumber(lNormalizedNumber);
							lDomain = lProxy.getDomain();
						}
					LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(lNormalizedNumber 
																									, lDomain
																									, mDisplayName);	
					lCallingUri = lAddress.toUri(); 
					}
					lLinphoneCore.invite(lCallingUri);
				}
				
			}); 
			mDecline= (ImageButton) findViewById(R.id.Decline);
			mHangup = (ImageButton) findViewById(R.id.HangUp); 
			
			OnClickListener lHangupListener = new OnClickListener() {
				
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					lLinphoneCore.terminateCall();
				}
				
			};
			mHangup.setOnClickListener(lHangupListener); 
			mDecline.setOnClickListener(lHangupListener);
			
			class DialKeyListener implements  OnClickListener {
				final String mKeyCode;
				final TextView mAddressView;
				DialKeyListener(TextView anAddress, char aKeyCode) {
					mKeyCode = String.valueOf(aKeyCode);
					mAddressView = anAddress;
				}
				public void onClick(View v) {
					mAddressView.append(mKeyCode);
					mDisplayName="";
				}
				
			};
			mCallControlRow = (LinearLayout) findViewById(R.id.CallControlRow);
			mInCallControlRow = (LinearLayout) findViewById(R.id.IncallControlRow);
			mAddressLayout = (LinearLayout) findViewById(R.id.Addresslayout);
			mInCallAddressLayout = (LinearLayout) findViewById(R.id.IncallAddressLayout);
			
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			
			if (LinphoneService.isready()) {
				if (LinphoneService.instance().getLinphoneCore().isIncall()) {
					mCall.setEnabled(false);
					mHangup.setEnabled(!mCall.isEnabled());
					mCallControlRow.setVisibility(View.GONE);
					mInCallControlRow.setVisibility(View.VISIBLE);
					mAddressLayout.setVisibility(View.GONE);
					mInCallAddressLayout.setVisibility(View.VISIBLE);
				}
			}
			
			
			mMute = (ToggleButton)findViewById(R.id.mic_mute_button);
			mMute.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				public void onCheckedChanged(CompoundButton buttonView,	boolean isChecked) {
					LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
					if (isChecked) {
						lc.muteMic(false);
						mMute.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.mic_active, 0, 0);
					} else {
						lc.muteMic(true);
						mMute.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.mic_muted, 0, 0);
					}
					
				}
				
			});
			
			mSpeaker = (ToggleButton)findViewById(R.id.speaker_button);
			mSpeaker.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				public void onCheckedChanged(CompoundButton buttonView,	boolean isChecked) {
					if (isChecked) {
						routeAudioToSpeaker();
					} else {
						routeAudioToReceiver();
					}
					
				}
				
			});
			
			mZero = (Button) findViewById(R.id.Button00) ;
			if (mZero != null) {
				
				mZero.setOnClickListener(new DialKeyListener(mAddress,'0'));
				mOne = (Button) findViewById(R.id.Button01) ;
				mOne.setOnClickListener(new DialKeyListener(mAddress,'1'));
				mTwo = (Button) findViewById(R.id.Button02);
				mTwo.setOnClickListener(new DialKeyListener(mAddress,'2'));
				mThree = (Button) findViewById(R.id.Button03);
				mThree.setOnClickListener(new DialKeyListener(mAddress,'3'));
				mFour = (Button) findViewById(R.id.Button04);
				mFour.setOnClickListener(new DialKeyListener(mAddress,'4'));
				mFive = (Button) findViewById(R.id.Button05);
				mFive.setOnClickListener(new DialKeyListener(mAddress,'5'));
				mSix = (Button) findViewById(R.id.Button06);
				mSix.setOnClickListener(new DialKeyListener(mAddress,'6'));
				mSeven = (Button) findViewById(R.id.Button07);
				mSeven.setOnClickListener(new DialKeyListener(mAddress,'7'));
				mEight = (Button) findViewById(R.id.Button08);
				mEight.setOnClickListener(new DialKeyListener(mAddress,'8'));
				mNine = (Button) findViewById(R.id.Button09);
				mNine.setOnClickListener(new DialKeyListener(mAddress,'9'));
				mStar = (Button) findViewById(R.id.ButtonStar);
				mStar.setOnClickListener(new DialKeyListener(mAddress,'*'));
				mHash = (Button) findViewById(R.id.ButtonHash);
				mHash.setOnClickListener(new DialKeyListener(mAddress,'#'));
			}

			mStatus =  (TextView) findViewById(R.id.status_label);
			theDialer = this;
		
		} catch (Exception e) {
			Log.e(LinphoneService.TAG,"Cannot start linphone",e);
			finish();
		}

	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		// TODO Auto-generated method stub
		
	}
	public void byeReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub
		
	}
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void displayStatus(LinphoneCore lc, String message) {
		mStatus.setText(message);
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void generalState(LinphoneCore lc, GeneralState state) {
		switch(state) {

		case GSTATE_POWER_ON:
			mCall.setEnabled(!lc.isIncall());
			mHangup.setEnabled(!mCall.isEnabled());  
			break;
		case GSTATE_REG_OK: {
			break; 
		}
		case GSTATE_CALL_OUT_INVITE: {
		}
		case GSTATE_CALL_IN_INVITE: { 
			enterIncalMode(lc);
			routeAudioToSpeaker();
			break;
		}
		case GSTATE_CALL_IN_CONNECTED:
		case GSTATE_CALL_OUT_CONNECTED: {
			routeAudioToReceiver();
			setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
			break;
		}
		case GSTATE_CALL_ERROR: {

			Toast toast = Toast.makeText(this
					,String.format(getString(R.string.call_error),lc.getRemoteAddress())
					, Toast.LENGTH_LONG);
			toast.show();
		}
		case GSTATE_CALL_END: {
			exitCallMode();
			break;
		}
		}
	}
	public void inviteReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub
		
	}
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}
	
	private void enterIncalMode(LinphoneCore lc) {
		mCallControlRow.setVisibility(View.GONE);
		mInCallControlRow.setVisibility(View.VISIBLE);
		mAddressLayout.setVisibility(View.GONE);
		mInCallAddressLayout.setVisibility(View.VISIBLE);
		mCall.setEnabled(false);
		mHangup.setEnabled(true);
		String DisplayName = lc.getRemoteAddress().getDisplayName();
		if (DisplayName!=null) {
			mDisplayNameView.setText(DisplayName);
		} else {
			mDisplayNameView.setText(lc.getRemoteAddress().getUserName());
		}
	}
	private void exitCallMode() {
		mCallControlRow.setVisibility(View.VISIBLE);
		mInCallControlRow.setVisibility(View.GONE);
		mAddressLayout.setVisibility(View.VISIBLE);
		mInCallAddressLayout.setVisibility(View.GONE);
		mCall.setEnabled(true);
		mHangup.setEnabled(false);
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
	}
	private void routeAudioToSpeaker() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT) {
			mAudioManager.setMode(AudioManager.MODE_NORMAL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(true); 
		}
		
	}
	private void routeAudioToReceiver() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT) {
			mAudioManager.setMode(AudioManager.MODE_IN_CALL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(false); 
		}		
	}
	
}
