package org.linphone.test;

import java.util.ArrayList;

import junit.framework.Assert;

import org.linphone.CallActivity;
import org.linphone.CallIncomingActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.linphone.mediastream.Log;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.DisplayMetrics;
import android.view.View;

public class ConferenceAndMultiCall extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAInit() {
		LinphoneTestManager.createAndStart(aContext, iContext, 2);

		solo.sleep(2000);
		waitForRegistration(LinphoneTestManager.getLc(2).getProxyConfigList()[0]);

		//Disable video
		goToSettings();

		selectItemInListOnUIThread(3);
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_enable_title));
		solo.sleep(500);

		solo.goBack();
		solo.sleep(1000);
		Assert.assertFalse(LinphoneManager.getLc().isVideoEnabled());
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBSimpleConference() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		startConference();

		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testCRemoveOneFromConference() {
		startConference();

		solo.clickOnView(solo.getView(org.linphone.R.id.conference_pause));

		Assert.assertEquals(1, LinphoneTestManager.getLc(1).getCallsNb());
		Assert.assertEquals(1, LinphoneTestManager.getLc(2).getCallsNb());
		solo.sleep(1000);
		Assert.assertFalse(LinphoneManager.getLc().isInConference());

		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testDChangePausedCall() {
		startTwoCalls();

		solo.sleep(2000);
		LinphoneCall call1 = LinphoneTestManager.getLc(1).getCalls()[0];
		LinphoneCall call2 = LinphoneTestManager.getLc(2).getCalls()[0];
		waitForCallState(call2,LinphoneCall.State.StreamsRunning);
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);

		solo.clickOnView(solo.getView(org.linphone.R.id.call_pause));
		solo.sleep(2000);
		waitForCallState(call1,LinphoneCall.State.StreamsRunning);
		waitForCallState(call2,LinphoneCall.State.PausedByRemote);

		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testEPauseAllCalls() {
		startTwoCalls();
		
		solo.sleep(2000);
		LinphoneCall call1 = LinphoneTestManager.getLc(1).getCalls()[0];
		LinphoneCall call2 = LinphoneTestManager.getLc(2).getCalls()[0];
		waitForCallState(call2,LinphoneCall.State.StreamsRunning);
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);

		solo.clickOnView(solo.getView(org.linphone.R.id.pause));
		solo.sleep(2000);
		waitForCallState(call2,LinphoneCall.State.PausedByRemote);
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);
		
		// All calls are paused, one click on hang_up terminates them all
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testFAddNewCallAndCancelIt() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning(1);
		LinphoneTestManager.getInstance().autoAnswer = false;
		
		solo.clickOnView(solo.getView(org.linphone.R.id.options));
		solo.clickOnView(solo.getView(org.linphone.R.id.add_call));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.conference_account_login) + "@" + iContext.getString(org.linphone.test.R.string.conference_account_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		solo.sleep(2000);
		solo.clickOnView(solo.getView(org.linphone.R.id.outgoing_hang_up));

		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.PausedByRemote);
		solo.clickOnView(solo.getView(org.linphone.R.id.pause));
		solo.sleep(1000);
		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.StreamsRunning);
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		LinphoneTestManager.getInstance().autoAnswer = true;
	}

	@LargeTest
	public void testGAddNewCallDeclined() {
		LinphoneTestManager.getInstance().autoAnswer = true; // Just in case
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning(1);
		LinphoneTestManager.getInstance().declineCall = true;
		
		solo.clickOnView(solo.getView(org.linphone.R.id.options));
		solo.clickOnView(solo.getView(org.linphone.R.id.add_call));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.conference_account_login) + "@" + iContext.getString(org.linphone.test.R.string.conference_account_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		solo.sleep(2000);
		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.PausedByRemote);
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		LinphoneTestManager.getInstance().declineCall = false;
	}

	@LargeTest
	public void testHIncomingCallWhileInCallAndDecline() {
		LinphoneTestManager.getInstance().declineCall = false; //Just in case
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning(1);
		
		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc(2).invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming call Activity", CallIncomingActivity.class);

		solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(topLayout.getMeasuredWidth() - 10, 10, slidersTop, slidersTop, 10);
		
		assertCallIsCorrectlyRunning(1);
		
		solo.sleep(2000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testIIncomingCallWhileInCallAndAccept() {		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning(1);
		
		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc(2).invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming call Activity", CallIncomingActivity.class);

		solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(10, topLayout.getMeasuredWidth() - 10, slidersTop, slidersTop, 10);

		solo.sleep(1000);
		LinphoneCall call1 = LinphoneTestManager.getLc(1).getCalls()[0];
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);
		assertCallIsCorrectlyRunning(2);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(org.linphone.R.id.side_menu_button));
		solo.clickOnText("Settings");
	}
	
	private void startTwoCalls() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		assertCallIsCorrectlyRunning(1);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.options));
		solo.clickOnView(solo.getView(org.linphone.R.id.add_call));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.conference_account_login) + "@" + iContext.getString(org.linphone.test.R.string.conference_account_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		assertCallIsCorrectlyRunning(2);
	}
	
	private void startConference() {
		startTwoCalls();

		solo.clickOnView(solo.getView(org.linphone.R.id.options));
		solo.clickOnView(solo.getView(org.linphone.R.id.conference));
		solo.sleep(1000);
		
		assertCallIsCorrectlyRunning(1);
		assertCallIsCorrectlyRunning(2);
		Assert.assertTrue(LinphoneManager.getLc().isInConference());
	}
	
	private void assertCallIsCorrectlyRunning(int lcId) {
		solo.waitForActivity("CallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", CallActivity.class);

		solo.sleep(2000);
		Assert.assertEquals(1, LinphoneTestManager.getLc(lcId).getCallsNb());
		LinphoneCall call = LinphoneTestManager.getLc(lcId).getCalls()[0];
		
		int retry = 0;
		while ((call.getState() == LinphoneCall.State.OutgoingProgress || call.getState() == LinphoneCall.State.IncomingReceived) && retry < 5) {
			solo.sleep(1000);
			retry++;
			Log.w("call in progress but not running, retry = " + retry);
		}

		waitForCallState(call, LinphoneCall.State.StreamsRunning);
	}
}
