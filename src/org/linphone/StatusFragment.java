package org.linphone;
/*
StatusFragment.java
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
import java.util.ArrayList;
import java.util.List;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.ui.SlidingDrawer;
import org.linphone.ui.SlidingDrawer.OnDrawerOpenListener;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class StatusFragment extends Fragment {
	private Handler mHandler = new Handler();
	private Handler refreshHandler = new Handler();
	private TextView statusText, exit;
	private ImageView statusLed, callQuality, encryption;
	private ListView sliderContent;
	private SlidingDrawer drawer;
	private Runnable mCallQualityUpdater;
	private boolean isInCall, isAttached = false;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.status, container, false);
		
		statusText = (TextView) view.findViewById(R.id.statusText);
		statusLed = (ImageView) view.findViewById(R.id.statusLed);
		callQuality = (ImageView) view.findViewById(R.id.callQuality);
		encryption = (ImageView) view.findViewById(R.id.encryption);
		
		drawer = (SlidingDrawer) view.findViewById(R.id.statusBar);
		drawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {
			@Override
			public void onDrawerOpened() {
				populateSliderContent();
			}
		});
		sliderContent = (ListView) view.findViewById(R.id.content);
		exit = (TextView) view.findViewById(R.id.exit);
		exit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().exit();
			}
		});
		
        return view;
    }
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		isAttached = true;
		
		if (activity instanceof LinphoneActivity) {
			((LinphoneActivity) activity).updateStatusFragment(this);
			isInCall = false;
		} else if (activity instanceof InCallActivity) {
			((InCallActivity) activity).updateStatusFragment(this);
			isInCall = true;
		}
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		isAttached = false;
	}
	
	public void openOrCloseStatusBar() {
		if (getResources().getBoolean(R.bool.lock_statusbar)) {
			return;
		}

		populateSliderContent();
		if (getResources().getBoolean(R.bool.disable_animations)) {
			drawer.toggle();
		} else {
			drawer.animateToggle();
		}
	}
	
	private void populateSliderContent() {
		sliderContent.setDividerHeight(0);
		AccountsListAdapter adapter = new AccountsListAdapter(LinphoneManager.getLc().getProxyConfigList());
		sliderContent.setAdapter(adapter);
	}
	
	public void registrationStateChanged(final RegistrationState state) {
		if (!isAttached)
			return;
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				statusLed.setImageResource(getStatusIconResource(state));
				statusText.setText(getStatusIconText(state));
			}
		});
	}
	
	private int getStatusIconResource(LinphoneCore.RegistrationState state) {
		try {
			if (state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
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
		
		return -1;
	}
	
	private String getStatusIconText(LinphoneCore.RegistrationState state) {
		try {
			if (state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
				return getString(R.string.status_connected);
			} else if (state == RegistrationState.RegistrationProgress) {
				return getString(R.string.status_in_progress);
			} else if (state == RegistrationState.RegistrationFailed) {
				return getString(R.string.status_error);
			} else {
				return getString(R.string.status_not_connected);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private void startCallQuality() {
		callQuality.setVisibility(View.VISIBLE);
		refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
			LinphoneCall mCurrentCall = LinphoneManager.getLc()
					.getCurrentCall();

			public void run() {
				if (mCurrentCall == null) {
					mCallQualityUpdater = null;
					return;
				}
				
				int oldQuality = 0;
				float newQuality = mCurrentCall.getCurrentQuality();
				if ((int) newQuality != oldQuality) {
					updateQualityOfSignalIcon(newQuality);
				}
				
				if (isInCall) {
					refreshHandler.postDelayed(this, 1000);
				} else
					mCallQualityUpdater = null;
			}
		}, 1000);
	}
	
	void updateQualityOfSignalIcon(float quality) {
		if (quality >= 4) // Good Quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_3);
		} else if (quality >= 3) // Average quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_2);
		} else if (quality >= 2) // Low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_1);
		} else if (quality >= 1) // Very low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_1);
		} else // Worst quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_0);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (isInCall) {
			startCallQuality();
			refreshStatusItems();
			
			// We are obviously connected
			statusLed.setImageResource(R.drawable.led_connected);
			statusText.setText(getString(R.string.status_connected));
			
			if (drawer != null) {
				drawer.lock();
			}
		} else {
			if (drawer != null && getResources().getBoolean(R.bool.lock_statusbar)) {
				drawer.lock();
			} else if (drawer != null) {
				drawer.unlock();
			}
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mCallQualityUpdater != null) {
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater = null;
		}
	}
	
	public void refreshStatusItems() {
		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call != null && encryption != null) {
			MediaEncryption mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();

			exit.setVisibility(View.GONE);
			encryption.setVisibility(View.VISIBLE);
			
			if (mediaEncryption == MediaEncryption.SRTP || (mediaEncryption == MediaEncryption.ZRTP && call.isAuthenticationTokenVerified())) {
				encryption.setImageResource(R.drawable.security_ok);
			} else if (mediaEncryption == MediaEncryption.ZRTP && !call.isAuthenticationTokenVerified()) {
				encryption.setImageResource(R.drawable.security_pending);
			} else {
				encryption.setImageResource(R.drawable.security_ko);
			}
		} else {
			exit.setVisibility(View.VISIBLE);
		}
	}
	
	class AccountsListAdapter extends BaseAdapter {
		private LinphoneProxyConfig[] accounts;
		private SharedPreferences prefs;
		private List<CheckBox> checkboxes;
		
		AccountsListAdapter(LinphoneProxyConfig[] lpcs) {
			accounts = lpcs;
			prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			checkboxes = new ArrayList<CheckBox>();
		}
		
		private OnCheckedChangeListener defaultListener = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					SharedPreferences.Editor editor = prefs.edit();
					editor.putInt(getString(R.string.pref_default_account), (Integer) buttonView.getTag());
					editor.commit();
					sliderContent.invalidate();

					for (CheckBox cb : checkboxes) {
						cb.setChecked(false);
						cb.setEnabled(true);
					}
					buttonView.setChecked(true);
					buttonView.setEnabled(false);
				}
			}
		};
		
		public int getCount() {
			return accounts.length;
		}

		public Object getItem(int position) {
			return accounts[position];
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;			
			if (convertView != null) {
				view = convertView;
			} else {
				view = LayoutInflater.from(getActivity()).inflate(R.layout.accounts, parent, false);
			}

			LinphoneProxyConfig lpc = (LinphoneProxyConfig) getItem(position);
			
			ImageView status = (ImageView) view.findViewById(R.id.State);
			status.setImageResource(getStatusIconResource(lpc.getState()));
			
			TextView identity = (TextView) view.findViewById(R.id.Identity);
			identity.setText(lpc.getIdentity().split("sip:")[1]);
			
			CheckBox isDefault = (CheckBox) view.findViewById(R.id.Default);
			checkboxes.add(isDefault);
			isDefault.setTag(position);
			isDefault.setChecked(false);
			isDefault.setEnabled(true);
			
			if (prefs.getInt(getString(R.string.pref_default_account), 0) == position) {
				isDefault.setChecked(true);
				isDefault.setEnabled(false);
			}
			isDefault.setOnCheckedChangeListener(defaultListener);
			
			return view;
		}
	}
}
