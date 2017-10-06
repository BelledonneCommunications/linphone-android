package org.linphone;

/*
SampleTest.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.ListView;

import com.robotium.solo.Condition;
import com.robotium.solo.Solo;

public abstract class SampleTest extends ActivityInstrumentationTestCase2<LinphoneLauncherActivity>{

	protected final int STRING_LENGTH_MAX = 20;

	protected Solo solo;
	protected Context aContext, iContext;

	public SampleTest() {
		super(LinphoneLauncherActivity.class);
	}

	@Override
	public void setUp() throws Exception {
		solo = new Solo(getInstrumentation());
		aContext = getActivity();
		iContext = aContext;
	}

	@Override
	public void tearDown() throws Exception {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.terminateAllCalls();
		}
		solo.finishOpenedActivities();
	}

	protected void selectItemInListOnUIThread(final int item) {
		solo.sleep(500);
		getActivity().runOnUiThread(new Runnable() {
			public void run() {
				ListView list = (ListView) solo.getView(android.R.id.list);
				list.setSelection(item);
			}
		});
	}

	protected void waitForRegistration(final LinphoneProxyConfig lpc) {
		if(lpc == null) return;
		solo.waitForCondition(new Condition() {
			@Override
			public boolean isSatisfied() {
				return RegistrationState.RegistrationOk == lpc.getState();
			}
		}, 30000);
	}

	protected void waitForCallPaused(final LinphoneCall call) {
		if(call == null) return;
			solo.waitForCondition(new Condition() {
				@Override
				public boolean isSatisfied() {
					return call.getState().equals(State.Paused) || call.getState().equals(State.Pausing);
				}
			}, 30000);
	}

	protected void waitForCallResumed(final LinphoneCall call) {
		if(call == null) return;
			solo.waitForCondition(new Condition() {
				@Override
				public boolean isSatisfied() {
					return call.getState().equals(State.Resuming) || call.getState().equals(State.StreamsRunning);
				}
			}, 30000);
	}

	protected void waitForCallState(final LinphoneCall call, final State state) {
		if(call == null) return;
			solo.waitForCondition(new Condition() {
				@Override
				public boolean isSatisfied() {
					return state.equals(call.getState());
				}
			}, 30000);
	}
}