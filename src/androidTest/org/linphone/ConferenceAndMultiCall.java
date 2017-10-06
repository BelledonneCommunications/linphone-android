package org.linphone;

/*
ConferenceAndMultiCall.java
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

import junit.framework.Assert;


import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.linphone.mediastream.Log;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.Gravity;

import static android.test.TouchUtils.dragViewToX;

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
		solo.clickOnText(aContext.getString(R.string.pref_video_title));
		solo.clickOnText(aContext.getString(R.string.pref_video_enable_title));
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

		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testCRemoveOneFromConference() {
		startConference();

		solo.clickOnView(solo.getView(R.id.conference_pause));

		Assert.assertEquals(1, LinphoneTestManager.getLc(1).getCallsNb());
		Assert.assertEquals(1, LinphoneTestManager.getLc(2).getCallsNb());
		solo.sleep(1000);
		Assert.assertFalse(LinphoneManager.getLc().isInConference());

		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.sleep(1000);
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

		solo.clickOnView(solo.getView(R.id.call_pause));
		solo.sleep(2000);
		waitForCallState(call1,LinphoneCall.State.StreamsRunning);
		waitForCallState(call2,LinphoneCall.State.PausedByRemote);

		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.hang_up));
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

		solo.clickOnView(solo.getView(R.id.pause));
		solo.sleep(2000);
		waitForCallState(call2,LinphoneCall.State.PausedByRemote);
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);

		// All calls are paused, one click on hang_up terminates them all
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testFAddNewCallAndCancelIt() {
		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(R.id.call));

		assertCallIsCorrectlyRunning(1);
		LinphoneTestManager.getInstance().autoAnswer = false;

		solo.clickOnView(solo.getView(R.id.options));
		solo.clickOnView(solo.getView(R.id.add_call));

		solo.enterText(0, iContext.getString(R.string.conference_account_login) + "@" + iContext.getString(R.string.conference_account_domain));
		solo.clickOnView(solo.getView(R.id.call));

		solo.sleep(2000);
		solo.clickOnView(solo.getView(R.id.outgoing_hang_up));

		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.PausedByRemote);
		solo.clickOnView(solo.getView(R.id.pause));
		solo.sleep(1000);
		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.StreamsRunning);

		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		LinphoneTestManager.getInstance().autoAnswer = true;
	}

	@LargeTest
	public void testGAddNewCallDeclined() {
		LinphoneTestManager.getInstance().autoAnswer = true; // Just in case

		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(R.id.call));

		assertCallIsCorrectlyRunning(1);
		LinphoneTestManager.getInstance().declineCall = true;

		solo.clickOnView(solo.getView(R.id.options));
		solo.clickOnView(solo.getView(R.id.add_call));

		solo.enterText(0, iContext.getString(R.string.conference_account_login) + "@" + iContext.getString(R.string.conference_account_domain));
		solo.clickOnView(solo.getView(R.id.call));

		solo.sleep(2000);
		waitForCallState(LinphoneTestManager.getLc(1).getCalls()[0],LinphoneCall.State.PausedByRemote);

		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		LinphoneTestManager.getInstance().declineCall = false;
	}

	@LargeTest
	public void testHIncomingCallWhileInCallAndDecline() {
		LinphoneTestManager.getInstance().declineCall = false; //Just in case

		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(R.id.call));

		assertCallIsCorrectlyRunning(1);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc(2).invite("sip:" + iContext.getString(R.string.account_linphone_login) + "@" + iContext.getString(R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}

		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming call Activity", CallIncomingActivity.class);

		solo.sleep(1000);
		dragViewToX(this, solo.getView(R.id.accept), Gravity.CENTER_HORIZONTAL, 0);
		getInstrumentation().waitForIdleSync();

		assertCallIsCorrectlyRunning(1);

		solo.sleep(2000);
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testIIncomingCallWhileInCallAndAccept() {
		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(R.id.call));

		assertCallIsCorrectlyRunning(1);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc(2).invite("sip:" + iContext.getString(R.string.account_linphone_login) + "@" + iContext.getString(R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}

		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming call Activity", CallIncomingActivity.class);

		solo.sleep(1000);
		dragViewToX(this, solo.getView(R.id.accept), Gravity.CENTER_HORIZONTAL, 0);
		getInstrumentation().waitForIdleSync();

		solo.sleep(1000);
		LinphoneCall call1 = LinphoneTestManager.getLc(1).getCalls()[0];
		waitForCallState(call1,LinphoneCall.State.PausedByRemote);
		assertCallIsCorrectlyRunning(2);

		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.side_menu_button));
		solo.clickOnText(aContext.getString(R.string.menu_settings));
	}

	private void startTwoCalls() {
		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(R.id.call));
		assertCallIsCorrectlyRunning(1);

		solo.clickOnView(solo.getView(R.id.options));
		solo.clickOnView(solo.getView(R.id.add_call));

		solo.enterText(0, iContext.getString(R.string.conference_account_login) + "@" + iContext.getString(R.string.conference_account_domain));
		solo.clickOnView(solo.getView(R.id.call));
		assertCallIsCorrectlyRunning(2);
	}

	private void startConference() {
		startTwoCalls();

		solo.clickOnView(solo.getView(R.id.options));
		solo.clickOnView(solo.getView(R.id.conference));
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
