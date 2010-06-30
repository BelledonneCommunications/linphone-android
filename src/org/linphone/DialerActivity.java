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
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneCore.GeneralState;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableRow;
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
	private TableRow mInCallControlRow;
	private LinearLayout mAddressLayout;
	private LinearLayout mInCallAddressLayout;
	
	private static DialerActivity theDialer;
	
	private String mDisplayName;
	private AudioManager mAudioManager;
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mPref;
	
	String PREF_CHECK_CONFIG = "pref_check_config";

	
	/**
	 * 
	 * @return null if not ready yet
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
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");
		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		try {

			
			mAddress = (TextView) findViewById(R.id.SipUri);
			mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
			mErase = (Button)findViewById(R.id.Erase); 
			
			mErase.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (mAddress.length() >0) {
						int lBegin = mAddress.getSelectionStart();
						if (lBegin == -1) {
							lBegin = mAddress.getEditableText().length()-1;
						}
						if (lBegin >0) {
							mAddress.getEditableText().delete(lBegin-1,lBegin);
						}
					}
				}
			});
			mErase.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View arg0) {
					mAddress.getEditableText().clear();
					return true;
				}
				
			});
			
			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					if (lLinphoneCore.isInComingInvitePending()) {
						try {
							lLinphoneCore.acceptCall();
						} catch (LinphoneCoreException e) {
							lLinphoneCore.terminateCall();
							Toast toast = Toast.makeText(DialerActivity.this
									,String.format(getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
									, Toast.LENGTH_LONG);
							toast.show();
						}
						return;
					}
					newOutgoingCall(mAddress.getText().toString(),mDisplayName);
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
					LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
					if (lc.isIncall()) {
						lc.sendDtmf(mKeyCode.charAt(0));
					} else {
						int lBegin = mAddressView.getSelectionStart();
						if (lBegin == -1) {
							lBegin = mAddressView.getEditableText().length();
						}
						if (lBegin >=0) {
							mAddressView.getEditableText().insert(lBegin,mKeyCode);
						}
						mDisplayName="";
					}
				}
				
			};
			mCallControlRow = (LinearLayout) findViewById(R.id.CallControlRow);
			mInCallControlRow = (TableRow) findViewById(R.id.IncallControlRow);
			mAddressLayout = (LinearLayout) findViewById(R.id.Addresslayout);
			mInCallAddressLayout = (LinearLayout) findViewById(R.id.IncallAddressLayout);
			mMute = (ToggleButton)findViewById(R.id.mic_mute_button);
			mSpeaker = (ToggleButton)findViewById(R.id.speaker_button);
			
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			mDecline.setEnabled(false);
			if (LinphoneService.isready() && getIntent().getData() != null) {
		    	newOutgoingCall(getIntent().getData().toString().substring("tel://".length()));
		    	getIntent().setData(null);
		    }
			if (LinphoneService.isready()) {
				LinphoneCore lLinphoenCore = LinphoneService.instance().getLinphoneCore();
				if (lLinphoenCore.isIncall()) {
					if(lLinphoenCore.isInComingInvitePending()) {
						callPending();
					} else {
						mCall.setEnabled(false);
						mHangup.setEnabled(!mCall.isEnabled());
						mCallControlRow.setVisibility(View.GONE);
						mInCallControlRow.setVisibility(View.VISIBLE);
						mAddressLayout.setVisibility(View.GONE);
						mInCallAddressLayout.setVisibility(View.VISIBLE);
						mMute.setChecked(!lLinphoenCore.isMicMuted()); 
						mMute.setCompoundDrawablesWithIntrinsicBounds(0
								, mMute.isChecked()?R.drawable.mic_active:R.drawable.mic_muted
										, 0
										, 0);
						
						String DisplayName = lLinphoenCore.getRemoteAddress().getDisplayName();
						if (DisplayName!=null) {
							mDisplayNameView.setText(DisplayName);
						} else {
							mDisplayNameView.setText(lLinphoenCore.getRemoteAddress().getUserName());
						}
						if ((Integer.parseInt(Build.VERSION.SDK) <=4 && mAudioManager.getMode() == AudioManager.MODE_NORMAL) 
								|| Integer.parseInt(Build.VERSION.SDK) >4 &&mAudioManager.isSpeakerphoneOn()) {
							mSpeaker.setChecked(true);
						}
						mWakeLock.acquire();
					} 
				}
			}
			
			

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
			
			
			mSpeaker.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				public void onCheckedChanged(CompoundButton buttonView,	boolean isChecked) {
					if (isChecked) {
						routeAudioToSpeaker();
						mSpeaker.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.speaker_32_on, 0, 0);
					} else {
						routeAudioToReceiver();
						mSpeaker.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.speaker_32_off, 0, 0);
					}
					
				}
				
			});
			
			mZero = (Button) findViewById(R.id.Button00) ;
			if (mZero != null) {
				
				mZero.setOnClickListener(new DialKeyListener(mAddress,'0'));
				mZero.setOnLongClickListener(new OnLongClickListener() {
					public boolean onLongClick(View arg0) {
						int lBegin = mAddress.getSelectionStart();
						if (lBegin == -1) {
							lBegin = mAddress.getEditableText().length();
						}
						if (lBegin >=0) {
						mAddress.getEditableText().insert(lBegin,"+");
						}
						return true;
					}
					
				});
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
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if (mWakeLock.isHeld()) mWakeLock.release();
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

		if (state == GeneralState.GSTATE_POWER_ON) {
			mCall.setEnabled(!lc.isIncall());
			mHangup.setEnabled(!mCall.isEnabled());  
			try{
				LinphoneService.instance().initFromConf();
			} catch (LinphoneConfigException ec) {
				Log.w(LinphoneService.TAG,"no valid settings found",ec);
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getString(R.string.initial_config_error))
				.setCancelable(false)
				.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						LinphoneActivity.instance().startprefActivity();
					}
				}).setNeutralButton(getString(R.string.no), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();

					}
				}).setNegativeButton(getString(R.string.never_remind), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						mPref.edit().putBoolean(PREF_CHECK_CONFIG, true).commit();
						dialog.cancel();
					}
				});
				if (mPref.getBoolean(PREF_CHECK_CONFIG, false) == false) {
					builder.create().show();
				}

			} catch (Exception e ) {
				Log.e(LinphoneService.TAG,"Cannot get initial config", e);
			}
			if (getIntent().getData() != null) {
		    	newOutgoingCall(getIntent().getData().toString().substring("tel://".length()));
		    	getIntent().setData(null);
		    }
		} else if (state == GeneralState.GSTATE_REG_OK) {
			//nop 
		} else if (state == GeneralState.GSTATE_CALL_OUT_INVITE) {
			mWakeLock.acquire();
			enterIncalMode(lc);
			routeAudioToReceiver();
		} else if (state == GeneralState.GSTATE_CALL_IN_INVITE) { 
			callPending();
		} else if (state == GeneralState.GSTATE_CALL_IN_CONNECTED 
				|| state == GeneralState.GSTATE_CALL_OUT_CONNECTED) {
			enterIncalMode(lc);
		} else if (state == GeneralState.GSTATE_CALL_ERROR) {
			if (mWakeLock.isHeld()) mWakeLock.release();
			Toast toast = Toast.makeText(this
					,String.format(getString(R.string.call_error),lc.getRemoteAddress())
					, Toast.LENGTH_LONG);
			toast.show();
			exitCallMode();
		} else if (state == GeneralState.GSTATE_CALL_END) {
			exitCallMode();
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
		LinphoneAddress remote=lc.getRemoteAddress();
		if (remote!=null){
			String DisplayName = remote.getDisplayName();
			if (DisplayName!=null) {
				mDisplayNameView.setText(DisplayName);
			} else {
				mDisplayNameView.setText(lc.getRemoteAddress().getUserName());
			}
		}
		if (mSpeaker.isChecked()) {
			 routeAudioToSpeaker();
		} else {
			 routeAudioToReceiver();
		}
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
	}
	private void exitCallMode() {
		mCallControlRow.setVisibility(View.VISIBLE);
		mInCallControlRow.setVisibility(View.GONE);
		mAddressLayout.setVisibility(View.VISIBLE);
		mInCallAddressLayout.setVisibility(View.GONE);
		mCall.setEnabled(true);
		mHangup.setEnabled(false);
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
		mMute.setChecked(true);
		mSpeaker.setChecked(false);
		mDecline.setEnabled(false);
		if (mWakeLock.isHeld())mWakeLock.release();
	}
	private void routeAudioToSpeaker() {
		if (Integer.parseInt(Build.VERSION.SDK) <= 4 /*<donut*/) {
			mAudioManager.setMode(AudioManager.MODE_NORMAL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(true); 
		}
		
	}
	private void routeAudioToReceiver() {
		if (Integer.parseInt(Build.VERSION.SDK) <=4 /*<donut*/) {
			mAudioManager.setMode(AudioManager.MODE_IN_CALL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(false); 
		}		
	}
	private void callPending() {
		mDecline.setEnabled(true);
		routeAudioToSpeaker();
	}
	public void newOutgoingCall(String aTo) {
		newOutgoingCall(aTo,null);
	}
	public void newOutgoingCall(String aTo, String displayName) {
		String lto = aTo;
		if (aTo.contains(OutgoingCallReceiver.TAG)) {
			lto = aTo.replace(OutgoingCallReceiver.TAG, "");
		}
		mAddress.setText(lto);
		mDisplayName = displayName;
		LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore();
		if (lLinphoneCore.isIncall()) {
			Toast toast = Toast.makeText(DialerActivity.this, getString(R.string.warning_already_incall), Toast.LENGTH_LONG);
			toast.show();
			return;
		}
		LinphoneAddress lAddress;
		try {
			lAddress = lLinphoneCore.interpretUrl(lto);
		} catch (LinphoneCoreException e) {
			Toast toast = Toast.makeText(DialerActivity.this
										,String.format(getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
										, Toast.LENGTH_LONG);
			toast.show();
			return;
		}
		lAddress.setDisplayName(mDisplayName);
		try {
			lLinphoneCore.invite(lAddress);
		} catch (LinphoneCoreException e) {
			Toast toast = Toast.makeText(DialerActivity.this
					,String.format(getString(R.string.error_cannot_invite_address),mAddress.getText().toString())
					, Toast.LENGTH_LONG);
			toast.show();
			return;
		}
	}
}
