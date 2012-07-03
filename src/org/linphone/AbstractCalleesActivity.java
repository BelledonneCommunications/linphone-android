/*
ConferenceActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.ui.ToggleImageButton;

import android.app.ListActivity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.Chronometer;
import android.widget.ImageView;

/**
 * @author Guillaume Beraudo
 *
 */
public abstract class AbstractCalleesActivity extends ListActivity implements LinphoneOnCallStateChangedListener, OnClickListener, LinphoneOnAudioChangedListener {

	private CalleeListAdapter mListAdapter;
	private List<LinphoneCall> mSpecificCalls = Collections.emptyList();
	private Handler mHandler = new Handler();

	private Checkable mMuteMicButton;
	private Checkable mSpeakerButton;

	protected abstract boolean isActive();
	protected abstract void setActive(boolean active);

	protected abstract List<LinphoneCall> updateSpecificCallsList();

	private Set<Chronometer> mChronometers = new HashSet<Chronometer>();
	
	private Handler callqualityHandler;
	private List<View> viewsToUpdateCallQuality;
	private boolean shouldDisplayWhoIsTalking = false;
	@Override
	/**
	 * Called by the child classes AFTER their own onCreate.
	 */
	protected void onCreate(Bundle savedInstanceState) {
		if (finishIfAutoRestartAfterACrash(savedInstanceState)) {
			return;
		}
		setListAdapter(mListAdapter = createCalleeListAdapter());

		View muteMic = findViewById(R.id.toggleMuteMic);
		muteMic.setOnClickListener(this);
		mMuteMicButton = (Checkable) muteMic;

		View speaker =  findViewById(R.id.toggleSpeaker);
		speaker.setOnClickListener(this);
		mSpeakerButton = (Checkable) speaker;
		if (LinphoneManager.getInstance().isSpeakerOn()) {
			((ToggleImageButton) speaker).setChecked(true);
		}
		super.onCreate(savedInstanceState);
	}

	protected abstract CalleeListAdapter createCalleeListAdapter();

	protected final boolean finishIfAutoRestartAfterACrash(Bundle savedInstanceState) {
		if (!LinphoneManager.isInstanciated() || LinphoneManager.getLc().getCallsNb() == 0) {
			if (!LinphoneManager.isInstanciated()) {
				Log.e("No service running: avoid crash by finishing ", this.getClass().getName());
			}
			super.onCreate(savedInstanceState);
			finish();
			return true;
		}
		return false;
	}

	@Override
	protected void onResume() {
		mSpecificCalls = updateSpecificCallsList();
		if (shouldFinishCalleeActivity()) {
			finish();
		} else {
			setActive(true);
			updateUI();
			mSpeakerButton.setChecked(LinphoneManager.getInstance().isSpeakerOn());
			mMuteMicButton.setChecked(LinphoneManager.getLc().isMicMuted());
			LinphoneManager.addListener(this);
			LinphoneManager.startProximitySensorForActivity(this);
		}
		super.onResume();
	}

	// Hook
	protected boolean shouldFinishCalleeActivity() {
		return mSpecificCalls.size() == 0;
	}

	@Override
	protected void onPause() {
		LinphoneManager.removeListener(this);
		LinphoneManager.stopProximitySensorForActivity(this);
		setActive(false);
		if (isFinishing()) {
			stopChronometers();
		}
		super.onPause();
	}

	@Override
	public void onCallStateChanged(LinphoneCall c, State s, String m) {
		mHandler.post(new Runnable() {
			public void run() {
				mSpecificCalls = updateSpecificCallsList();
				if (shouldFinishCalleeActivity()) {
					finish();
				} else {
					updateUI();
				}
			}
		});
	}

	protected LinphoneCore lc() {
		return LinphoneManager.getLc();
	}

	private void stopChronometers() {
		for (Chronometer chrono : mChronometers) {
			chrono.stop();
		}
		mChronometers.clear();
	}

	protected void updateUI() {
		stopChronometers();
		mListAdapter.notifyDataSetChanged();
	}


	protected void enableView(View root, int id, OnClickListener l, boolean enable) {
		LinphoneUtils.enableView(root, id, l, enable);
	}

	protected String getCalleeDisplayOrUserName(LinphoneAddress address) {
		if (!TextUtils.isEmpty(address.getDisplayName())) {
			return address.getDisplayName();
		} else {
			return address.getUserName();
		}
	}
	
	protected void setCalleePicture(ImageView pictureView, LinphoneAddress address) {
		// May be greatly sped up using a drawable cache
		Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getContentResolver());
		LinphoneUtils.setImagePictureFromUri(AbstractCalleesActivity.this, pictureView, uri, R.drawable.unknown_person);
	}

	protected void setVisibility(View v, int id, boolean visible) {
		LinphoneUtils.setVisibility(v, id, visible);
	}
	protected void setVisibility(View v, boolean visible) {
		LinphoneUtils.setVisibility(v, visible);
	}

	protected abstract class CalleeListAdapter extends BaseAdapter {
		protected final List<LinphoneCall> getSpecificCalls() {
			return mSpecificCalls;
		}
		public int getCount() {
			return mSpecificCalls.size();
		}

		public LinphoneCall getItem(int position) {
			return mSpecificCalls.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		protected final void registerCallDurationTimer(View v, LinphoneCall call) {
			int callDuration = call.getDuration();
			if (callDuration == 0 && call.getState() != State.StreamsRunning) return;
			Chronometer timer = (Chronometer) v.findViewById(R.id.callee_duration);
			if (timer == null) throw new IllegalArgumentException("no callee_duration view found");
			timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
			timer.start();
		}
		
		protected final void initCallQualityListener() {
			final int timeToRefresh;
			if (shouldDisplayWhoIsTalking)
				timeToRefresh = 100;
			else
				timeToRefresh = 1000;
			
			callqualityHandler = new Handler();
			viewsToUpdateCallQuality = new ArrayList<View>();
			callqualityHandler.postDelayed(new Runnable() {
				public void run() {
					if (viewsToUpdateCallQuality == null) {
						return;
					}

					for (View v : viewsToUpdateCallQuality) {
						LinphoneCall call = (LinphoneCall) v.getTag();
						float newQuality = call.getCurrentQuality();
							updateQualityOfSignalIcon(v, newQuality);
						
						// We also use this handler to display the ones who speaks
						ImageView speaking = (ImageView) v.findViewById(R.id.callee_status_speeking);
						if (shouldDisplayWhoIsTalking && call.getPlayVolume() >= -20) {
							speaking.setVisibility(View.VISIBLE);
						} else if (speaking.getVisibility() != View.GONE) {
							speaking.setVisibility(View.GONE);
						}
					}
					
					callqualityHandler.postDelayed(this, timeToRefresh);
				}
			},timeToRefresh);
		}
		
		protected final void registerCallQualityListener(final View v, final LinphoneCall call) {
			if (viewsToUpdateCallQuality == null && callqualityHandler == null) {
				initCallQualityListener();
			}
			v.setTag(call);
			viewsToUpdateCallQuality.add(v);
		}
		
		protected final void registerCallSpeakerListener() {
			shouldDisplayWhoIsTalking = true;
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.toggleMuteMic) {
			lc().muteMic(((Checkable) v).isChecked());
		}
		else if (id == R.id.toggleSpeaker) {
			if (((Checkable) v).isChecked()) {
				LinphoneManager.getInstance().routeAudioToSpeaker();
			} else {
				LinphoneManager.getInstance().routeAudioToReceiver();
			}
		}
	}

	@Override
	public void onAudioStateChanged(final AudioState state) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				switch (state) {
				case SPEAKER:
					mSpeakerButton.setChecked(true);
					break;
				case EARPIECE:
					mSpeakerButton.setChecked(false);
					break;
				default:
					throw new RuntimeException("Unknown audio state " + state);
				}
			}
		});
	}
	
	void updateQualityOfSignalIcon(View v, float quality)
	{
		ImageView qos = (ImageView) v.findViewById(R.id.callee_status_qos);
		if (!(qos.getVisibility() == View.VISIBLE)) {
			qos.setVisibility(View.VISIBLE);
		}
		if (quality >= 4) // Good Quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_4));
		}
		else if (quality >= 3) // Average quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_3));
		}
		else if (quality >= 2) // Low quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_2));
		}
		else if (quality >= 1) // Very low quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_1));
		}
		else // Worst quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_0));
		}
	}
}