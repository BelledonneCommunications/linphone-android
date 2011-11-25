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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;

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
	
	@Override
	/**
	 * Called by the child classes AFTER their own onCreate.
	 */
	protected void onCreate(Bundle savedInstanceState) {
		setListAdapter(mListAdapter = createCalleeListAdapter());

		View muteMic = findViewById(R.id.toggleMuteMic);
		muteMic.setOnClickListener(this);
		mMuteMicButton = (Checkable) muteMic;

		View speaker =  findViewById(R.id.toggleSpeaker);
		speaker.setOnClickListener(this);
		mSpeakerButton = (Checkable) speaker;
		super.onCreate(savedInstanceState);
	}

	protected abstract CalleeListAdapter createCalleeListAdapter();

	protected final boolean finishIfAutoRestartAfterACrash() {
		if (!LinphoneManager.isInstanciated() || LinphoneManager.getLc().getCallsNb() == 0) {
			Log.e("No service running: avoid crash by finishing ", this.getClass().getName());
			finish();
			return true;
		}
		return false;
	}

	@Override
	protected void onResume() {
		mSpecificCalls = updateSpecificCallsList();
		if (!finishOnEmptySpecificCallsWhileResuming() || mSpecificCalls.size() != 0) {
			setActive(true);
			updateUI();
			mSpeakerButton.setChecked(LinphoneManager.getInstance().isSpeakerOn());
			mMuteMicButton.setChecked(LinphoneManager.getLc().isMicMuted());
			LinphoneManager.addListener(this);
			LinphoneManager.startProximitySensorForActivity(this);
		} else {
			finish();
		}
		super.onResume();
	}

	protected boolean finishOnEmptySpecificCallsWhileResuming() {
		return false;
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
				updateUI();
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
			Chronometer timer = (Chronometer) v.findViewById(R.id.callee_duration);
			if (timer == null) throw new IllegalArgumentException("no callee_duration view found");
			timer.setBase(SystemClock.elapsedRealtime() - 1000 * call.getDuration());
			timer.start();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.toggleMuteMic:
			lc().muteMic(((Checkable) v).isChecked());
			break;
		case R.id.toggleSpeaker:
			if (((Checkable) v).isChecked()) {
				LinphoneManager.getInstance().routeAudioToSpeaker();
			} else {
				LinphoneManager.getInstance().routeAudioToReceiver();
			}
			break;
		default:
			break;
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
}