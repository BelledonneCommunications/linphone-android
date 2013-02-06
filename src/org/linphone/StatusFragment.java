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
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.ui.SlidingDrawer;
import org.linphone.ui.SlidingDrawer.OnDrawerOpenListener;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class StatusFragment extends Fragment {
	private Handler mHandler = new Handler();
	private Handler refreshHandler = new Handler();
	private TextView statusText;
	private ImageView statusLed, callQuality, encryption, background;
	private ListView sliderContentAccounts;
	private TableLayout callStats;
	private SlidingDrawer drawer;
//	private LinearLayout allAccountsLed;
	private Runnable mCallQualityUpdater;
	private boolean isInCall, isAttached = false;
	
	private Toast zrtpToast;
	private CountDownTimer zrtpHack;
	private boolean hideZrtpToast = false;
	private Timer mTimer;
	private TimerTask mTask;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.status, container, false);
		
		statusText = (TextView) view.findViewById(R.id.statusText);
		statusLed = (ImageView) view.findViewById(R.id.statusLed);
		callQuality = (ImageView) view.findViewById(R.id.callQuality);
		encryption = (ImageView) view.findViewById(R.id.encryption);
		background = (ImageView) view.findViewById(R.id.background);
//		allAccountsLed = (LinearLayout) view.findViewById(R.id.moreStatusLed);
		callStats = (TableLayout) view.findViewById(R.id.callStats);
		
		drawer = (SlidingDrawer) view.findViewById(R.id.statusBar);
		drawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {
			@Override
			public void onDrawerOpened() {
				populateSliderContent();
			}
		});
		
		sliderContentAccounts = (ListView) view.findViewById(R.id.accounts);

		// We create it once to not delay the first display
		populateSliderContent();

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
		openOrCloseStatusBar(false);
	}
	
	public void openOrCloseStatusBar(boolean force) {
		if (getResources().getBoolean(R.bool.lock_statusbar) && !force) {
			return;
		}
		
		if (getResources().getBoolean(R.bool.disable_animations)) {
			drawer.toggle();
		} else {
			drawer.animateToggle();
		}
	}
	
	public void closeStatusBar() {
		if (getResources().getBoolean(R.bool.lock_statusbar)) {
			return;
		}

		if (getResources().getBoolean(R.bool.disable_animations)) {
			drawer.close();
		} else {
			drawer.animateClose();
		}
	}
	
	private void populateSliderContent() {
		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null) {
			sliderContentAccounts.setVisibility(View.GONE);
			callStats.setVisibility(View.GONE);
			
			if (isInCall && isAttached && getResources().getBoolean(R.bool.display_call_stats)) {
				callStats.setVisibility(View.VISIBLE);
				LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
				initCallStatsRefresher(call, callStats);
			} else if (!isInCall) {
				sliderContentAccounts.setVisibility(View.VISIBLE);
				AccountsListAdapter adapter = new AccountsListAdapter();
				sliderContentAccounts.setAdapter(adapter);
			}
		}
	}
	
	public void registrationStateChanged(final RegistrationState state) {
		if (!isAttached || !LinphoneService.isReady()) {
			return;
		}
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				statusLed.setImageResource(getStatusIconResource(state, true));
				statusText.setText(getStatusIconText(state));
				try {
					if (getResources().getBoolean(R.bool.lock_statusbar)) {
						statusText.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								if (LinphoneManager.isInstanciated()) {
									LinphoneManager.getLc().refreshRegisters();
								}
							}
						});
					}
	//				setMiniLedsForEachAccount();
					populateSliderContent();
					sliderContentAccounts.invalidate();
				} catch (IllegalStateException ise) {}
			}
		});
	}
	
//	private void setMiniLedsForEachAccount() {
//		if (allAccountsLed == null)
//			return;
//		
//		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null) {
//			allAccountsLed.removeAllViews();
//			for (LinphoneProxyConfig lpc : LinphoneManager.getLc().getProxyConfigList()) {
//				ImageView led = new ImageView(getActivity());
//				LinearLayout.LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
//				led.setLayoutParams(params);
//				led.setAdjustViewBounds(true);
//				led.setImageResource(getStatusIconResource(lpc.getState(), false));
//				allAccountsLed.addView(led);
//			}
//		}
//	}
	
	private int getStatusIconResource(LinphoneCore.RegistrationState state, boolean isDefaultAccount) {
		try {
			boolean defaultAccountConnected = (isDefaultAccount && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().isRegistered()) || !isDefaultAccount;
			if (state == RegistrationState.RegistrationOk && defaultAccountConnected) {
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
		Context context = getActivity();
		if (!isAttached)
			context = LinphoneActivity.instance();
		
		try {
			if (state == RegistrationState.RegistrationOk && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().isRegistered()) {
				return context.getString(R.string.status_connected);
			} else if (state == RegistrationState.RegistrationProgress) {
				return context.getString(R.string.status_in_progress);
			} else if (state == RegistrationState.RegistrationFailed) {
				return context.getString(R.string.status_error);
			} else {
				return context.getString(R.string.status_not_connected);
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
		LinphoneCore lc = LinphoneManager.getLc();

		LinphoneCall call = lc.getCurrentCall();
		if (isInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
			if (call != null) {
				startCallQuality();
				refreshStatusItems(call, call.getCurrentParamsCopy().getVideoEnabled());
			}
			
			statusText.setVisibility(View.GONE);
			encryption.setVisibility(View.VISIBLE);
			
			// We are obviously connected
			statusLed.setImageResource(R.drawable.led_connected);
			statusText.setText(getString(R.string.status_connected));
		} else {
			statusText.setVisibility(View.VISIBLE);
			background.setVisibility(View.VISIBLE);
			encryption.setVisibility(View.GONE);
			
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
		
		if (zrtpToast != null) {
			hideZRTPDialog();
		}
		
		if (mCallQualityUpdater != null) {
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater = null;
		}
	}
	
	public void refreshStatusItems(final LinphoneCall call, boolean isVideoEnabled) {
		if (call != null) {
			MediaEncryption mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();

			if (isVideoEnabled) {
				background.setVisibility(View.GONE);
			} else {
				background.setVisibility(View.VISIBLE);
			}
			
			if (mediaEncryption == MediaEncryption.SRTP || (mediaEncryption == MediaEncryption.ZRTP && call.isAuthenticationTokenVerified())) {
				encryption.setImageResource(R.drawable.security_ok);
			} else if (mediaEncryption == MediaEncryption.ZRTP && !call.isAuthenticationTokenVerified()) {
				encryption.setImageResource(R.drawable.security_pending);
			} else {
				encryption.setImageResource(R.drawable.security_ko);
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
	
	private void hideZRTPDialog() {
		hideZrtpToast = true;
		
		if (zrtpToast != null) {
			zrtpToast.cancel();
		}
		if (zrtpHack != null) {
			zrtpHack.cancel();
		}
	}
	
	private void showZRTPDialog(final LinphoneCall call) {
        boolean authVerified = call.isAuthenticationTokenVerified();
        String format = getString(authVerified ? R.string.reset_sas_fmt : R.string.verify_sas_fmt);
        
		LayoutInflater inflater = LayoutInflater.from(getActivity());
		View layout = inflater.inflate(R.layout.zrtp_dialog, (ViewGroup) getActivity().findViewById(R.id.toastRoot));
		
		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(String.format(format, call.getAuthenticationToken()));
		
		zrtpToast = new Toast(getActivity());
		zrtpToast.setGravity(Gravity.TOP | Gravity.RIGHT, 0, LinphoneUtils.pixelsToDpi(getResources(), 40));
		zrtpToast.setDuration(Toast.LENGTH_LONG);
		
		ImageView ok = (ImageView) layout.findViewById(R.id.toastOK);
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (call != null) {
					call.setAuthenticationTokenVerified(true);
				}
				if (encryption != null) {
					encryption.setImageResource(R.drawable.security_ok);
				}
				hideZRTPDialog();
			}
		});
		
		ImageView notOk = (ImageView) layout.findViewById(R.id.toastNotOK);
		notOk.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (call != null) {
					call.setAuthenticationTokenVerified(false);
				}
				if (encryption != null) {
					encryption.setImageResource(R.drawable.security_pending);
				}
				hideZRTPDialog();
			}
		});
		
		zrtpHack = new CountDownTimer(3000, 1000)
		{
		    public void onTick(long millisUntilFinished) 
		    { 
		    	if (!hideZrtpToast) {
		    		zrtpToast.show(); 
		    	}
		    }
		    public void onFinish() { 
		    	if (!hideZrtpToast) { 
		    		zrtpToast.show();
		    		zrtpHack.start();
		    	}
		    }

		};

		zrtpToast.setView(layout);
		hideZrtpToast = false;
		zrtpToast.show();
		zrtpHack.start();
	}
	
	private void initCallStatsRefresher(final LinphoneCall call, final View view) {
		if (mTimer != null && mTask != null) {
			return;
		}
		
	    mTimer = new Timer();
		mTask = new TimerTask() {
			@Override
			public void run() {
				if (call == null) {
					mTimer.cancel();
					return;
				}

				final TextView title = (TextView) view.findViewById(R.id.call_stats_title);
				final TextView codec = (TextView) view.findViewById(R.id.codec);
				final TextView dl = (TextView) view.findViewById(R.id.downloadBandwith);
				final TextView ul = (TextView) view.findViewById(R.id.uploadBandwith);
				final TextView ice = (TextView) view.findViewById(R.id.ice);
				if (codec == null || dl == null || ul == null || ice == null) {
					mTimer.cancel();
					return;
				}
				
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						synchronized(LinphoneManager.getLc()) {
							final LinphoneCallParams params = call.getCurrentParamsCopy();
							if (params.getVideoEnabled()) {
								final LinphoneCallStats videoStats = call.getVideoStats();
								final LinphoneCallStats audioStats = call.getAudioStats();
								if (videoStats != null && audioStats != null) {
									title.setText("Video");
									PayloadType payloadAudio = params.getUsedAudioCodec();
									PayloadType payloadVideo = params.getUsedVideoCodec();
									if (payloadVideo != null && payloadAudio != null) {
										codec.setText(payloadVideo.getMime() + " / " + payloadAudio.getMime() + (payloadAudio.getRate() / 1000));
									}
									dl.setText(String.valueOf((int) videoStats.getDownloadBandwidth()) + " / " + (int) audioStats.getDownloadBandwidth() + " kbits/s");
									ul.setText(String.valueOf((int) videoStats.getUploadBandwidth()) +  " / " + (int) audioStats.getUploadBandwidth() + " kbits/s");
									ice.setText(videoStats.getIceState().toString());
								}
							} else {
								final LinphoneCallStats audioStats = call.getAudioStats();
								if (audioStats != null) {
									title.setText("Audio");
									PayloadType payload = params.getUsedAudioCodec();
									if (payload != null) {
										codec.setText(payload.getMime() + (payload.getRate() / 1000));
									}
									dl.setText(String.valueOf((int) audioStats.getDownloadBandwidth()) + " kbits/s");
									ul.setText(String.valueOf((int) audioStats.getUploadBandwidth()) + " kbits/s");
									ice.setText(audioStats.getIceState().toString());
								}
							}
						}
					}
				});
			}
		};
		mTimer.scheduleAtFixedRate(mTask, 0, 1500);
	}
	
	class AccountsListAdapter extends BaseAdapter {
		private SharedPreferences prefs;
		private List<CheckBox> checkboxes;
		
		AccountsListAdapter() {
			if (LinphoneActivity.isInstanciated()) {
				prefs = PreferenceManager.getDefaultSharedPreferences(LinphoneActivity.instance());
			}
			checkboxes = new ArrayList<CheckBox>();
		}
		
		private OnClickListener defaultListener = new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				CheckBox checkBox = (CheckBox) v;
				if (checkBox.isChecked()) {
					SharedPreferences.Editor editor = prefs.edit();
					String tag = (String) checkBox.getTag();
					String sipAddress = tag.split(":")[0];
					int accountPosition = Integer.parseInt(tag.split(":")[1]);
					
					int nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
					int accountIndex = 0;
					for (int i = 0; i < nbAccounts; i++)
					{
						String keyUsername = getString(R.string.pref_username_key);
						String keyDomain = getString(R.string.pref_domain_key);
						if (i > 0) {
							keyUsername += i + "";
							keyDomain += i + "";
						}
						String username = prefs.getString(keyUsername, "");
						String domain = prefs.getString(keyDomain, "");
						String identity = username + "@" + domain;
						if (identity.equals(sipAddress)) {
							accountIndex = i;
							break;
						}
					}
					
					editor.putInt(getString(R.string.pref_default_account_key), accountIndex);
					editor.commit();

					for (CheckBox cb : checkboxes) {
						cb.setChecked(false);
						cb.setEnabled(true);
					}
					checkBox.setChecked(true);
					checkBox.setEnabled(false);
					
					LinphoneCore lc = LinphoneManager.getLc();
					lc.setDefaultProxyConfig((LinphoneProxyConfig) getItem(accountPosition));
					if (lc.isNetworkReachable()) {
						lc.refreshRegisters();
					}
				}
			}
		};
		
		public int getCount() {
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (lc != null) {
				return lc.getProxyConfigList().length;
			} else {
				return 0;
			}
		}

		public Object getItem(int position) {
			return LinphoneManager.getLc().getProxyConfigList()[position];
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
			
			TextView identity = (TextView) view.findViewById(R.id.Identity);
			String sipAddress = (lpc.getIdentity() != null && lpc.getIdentity().startsWith("sip:")) ? lpc.getIdentity().split("sip:")[1] : lpc.getIdentity();
			identity.setText(sipAddress);
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					LinphoneManager.getLc().refreshRegisters();
				}
			});
			
			CheckBox isDefault = (CheckBox) view.findViewById(R.id.Default);
			checkboxes.add(isDefault);
			
			isDefault.setTag(sipAddress + ":" + position);
			isDefault.setChecked(false);
			isDefault.setEnabled(true);
			
			if (prefs != null) {
				int nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
				int accountIndex = 0;
				for (int i = 0; i < nbAccounts; i++)
				{
					String keyUsername = getString(R.string.pref_username_key);
					String keyDomain = getString(R.string.pref_domain_key);
					if (i > 0) {
						keyUsername += i + "";
						keyDomain += i + "";
					}
					String username = prefs.getString(keyUsername, "");
					String domain = prefs.getString(keyDomain, "");
					String id = username + "@" + domain;
					if (id.equals(sipAddress)) {
						accountIndex = i;
						break;
					}
				}
				if (prefs.getInt(getString(R.string.pref_default_account_key), 0) == accountIndex) {
					isDefault.setChecked(true);
					isDefault.setEnabled(false);
					status.setImageResource(getStatusIconResource(lpc.getState(), true));
				} else {
					status.setImageResource(getStatusIconResource(lpc.getState(), false));
				}
			} else {
				status.setImageResource(getStatusIconResource(lpc.getState(), false));
			}
			
			isDefault.setOnClickListener(defaultListener);
			
			return view;
		}
	}
}
