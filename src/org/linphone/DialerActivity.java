package org.linphone;

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class DialerActivity extends Activity {
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
	public void displayStatus(String status) {
		mStatus.setText(status);
	}
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);
		try {
			theDialer = this;
			mLinphoneCore = Linphone.getLinphone().getLinphoneCore();
			mAddress = (TextView) findViewById(R.id.SipUri);

			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneProxyConfig lProxy = mLinphoneCore.getDefaultProxyConfig();
					String lNormalizedNumber = mAddress.getText().toString(); 
					if (lProxy!=null) {
						lNormalizedNumber = lProxy.normalizePhoneNumber(lNormalizedNumber);
					}
					mLinphoneCore.invite(lNormalizedNumber);
				}
				
			}); 
			mHangup = (ImageButton) findViewById(R.id.HangUp);
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
	
}
