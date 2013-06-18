package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class Conference extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAInit() {
		LinphoneTestManager.createAndStart(aContext, iContext, 2);
		
		solo.sleep(2000);
		Assert.assertEquals(RegistrationState.RegistrationOk, LinphoneTestManager.getLc(2).getProxyConfigList()[0].getState());
		
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
		startConference();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testCRemoveOneFromConference() {
		startConference();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.callStatus));
		
		Assert.assertEquals(1, LinphoneTestManager.getLc(1).getCallsNb());
		Assert.assertEquals(1, LinphoneTestManager.getLc(2).getCallsNb());
		Assert.assertEquals(2, LinphoneManager.getLc().getCallsNb());
		Assert.assertFalse(LinphoneManager.getLc().isInConference());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.settings));
	}
	
	private void startConference() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning(1);
		solo.clickOnView(solo.getView(org.linphone.R.id.options));
		solo.clickOnView(solo.getView(org.linphone.R.id.addCall));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.conference_account_login) + "@" + iContext.getString(org.linphone.test.R.string.conference_account_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning(2);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.conference));
		solo.sleep(1000);
		
		assertCallIsCorrectlyRunning(1);
		assertCallIsCorrectlyRunning(2);
		Assert.assertTrue(LinphoneManager.getLc().isInConference());
	}
	
	private void assertCallIsCorrectlyRunning(int lcId) {
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		LinphoneCall call = LinphoneTestManager.getLc(lcId).getCalls()[0];
		
		if (call.getState() == LinphoneCall.State.OutgoingProgress) {
			solo.sleep(3000);
		}
		
		Assert.assertEquals(LinphoneCall.State.StreamsRunning, call.getState());
	}
}
