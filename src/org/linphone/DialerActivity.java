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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class DialerActivity extends Activity implements LinphoneCoreListener {
	private LinphoneCore mLinphoneCore; 
	private TextView mAddress;
	private TextView mStatus;
	private ImageButton mCall;
	private ImageButton mHangup;
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
			theDialer = this;
			mLinphoneCore = Linphone.getLinphone().getLinphoneCore();
			mAddress = (TextView) findViewById(R.id.SipUri);

			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (mLinphoneCore.isInComingInvitePending()) {
						mLinphoneCore.acceptCall();
						return;
					}
					if (mLinphoneCore.isIncall()) {
						Toast toast = Toast.makeText(DialerActivity.this, getString(R.string.warning_already_incall), Toast.LENGTH_LONG);
						toast.show();
						return;
					}
					String lRawAddress = mAddress.getText().toString();
					String lCallingUri=null;
					if (lRawAddress.startsWith("sip:")) {
						lCallingUri=lRawAddress;
					} else {
					LinphoneProxyConfig lProxy = mLinphoneCore.getDefaultProxyConfig();
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
					mLinphoneCore.invite(lCallingUri);
				}
				
			}); 
			mHangup = (ImageButton) findViewById(R.id.HangUp); 
			mHangup.setEnabled(false);  
			mHangup.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					mLinphoneCore.terminateCall();
				}
				
			});
			
			class DialKeyListener implements  OnClickListener {
				final String mKeyCode;
				final TextView mAddressView;
				DialKeyListener(TextView anAddress, char aKeyCode) {
					mKeyCode = String.valueOf(aKeyCode);
					mAddressView = anAddress;
				}
				public void onClick(View v) {
					mAddressView.append(mKeyCode);
				}
				
			};

			mZero = (Button) findViewById(R.id.Button00) ;
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
			
			mStatus =  (TextView) findViewById(R.id.status_label);
		
		} catch (Exception e) {
			Log.e(Linphone.TAG,"Cannot start linphone",e);
		}

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
		case GSTATE_CALL_ERROR: {

			Toast toast = Toast.makeText(this
					,String.format(getString(R.string.call_error),lc.getRemoteAddress())
					, Toast.LENGTH_LONG);
			toast.show();
			break;
		}
		case GSTATE_REG_OK: {
			break; 
		}
		case GSTATE_CALL_OUT_INVITE: {
			//de-activate green button
			mCall.setEnabled(false);
		}
		case GSTATE_CALL_IN_INVITE: { 
			// activate red button 
			mHangup.setEnabled(true);
			mAudioManager.setSpeakerphoneOn(true); 
			mAudioManager.setMode(AudioManager.MODE_NORMAL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
			break;
		}
		case GSTATE_CALL_IN_CONNECTED:
		case GSTATE_CALL_OUT_CONNECTED: {
			mAudioManager.setSpeakerphoneOn(false); 
			mAudioManager.setMode(AudioManager.MODE_IN_CALL); 
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
			break;
		}
		case GSTATE_CALL_END: {
			mCall.setEnabled(true);
			mHangup.setEnabled(false);
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
	
}
