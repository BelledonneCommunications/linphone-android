package org.linphone.test;

import org.linphone.LinphoneLauncherActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.ListView;
import java.util.List;

import com.robotium.solo.Condition;
import com.robotium.solo.Solo;

/**
 * @author Sylvain Berfini
 */
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
		iContext = getInstrumentation().getContext();
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