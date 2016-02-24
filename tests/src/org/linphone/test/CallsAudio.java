package org.linphone.test;

import junit.framework.Assert;

import org.linphone.CallActivity;
import org.linphone.CallIncomingActivity;
import org.linphone.CallOutgoingActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.PayloadType;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * @author Sylvain Berfini
 */
public class CallsAudio extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testAInit() {
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
	public void testBOutgoingCallWithDefaultConfig() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));

		assertOutgoingCallIsCorrectlyRunning();
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testCDTMFRFC2833InPCMUCall() {
		disableAllEnabledAudioCodecs();
		solo.clickOnText("PCMU");
		goBackToDialerAfterCodecChanges();
		solo.sleep(1000);
		
		LinphoneManager.getLc().setUseRfc2833ForDtmfs(true);
		LinphoneManager.getLc().setUseSipInfoForDtmfs(false);
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		solo.clickOnView(solo.getView(org.linphone.R.id.Digit3));
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		
		//To enable when issue http://git.linphone.org/mantis/view.php?id=750 will be fixed
		//Assert.assertTrue(LinphoneTestManager.getInstance().isDTMFReceived);
		LinphoneTestManager.getInstance().isDTMFReceived = false;
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testDDTMFSIPINFO() {
		LinphoneManager.getLc().setUseRfc2833ForDtmfs(false);
		LinphoneManager.getLc().setUseSipInfoForDtmfs(true);
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		solo.clickOnView(solo.getView(org.linphone.R.id.Digit3));
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		
		//To enable when issue http://git.linphone.org/mantis/view.php?id=751 will be fixed
		//Assert.assertTrue(LinphoneTestManager.getInstance().isDTMFReceived);
		LinphoneTestManager.getInstance().isDTMFReceived = false;
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testEOutgoingCallToAudioClient() {
		LinphoneTestManager.getLc().enableVideo(false, false);
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));

		solo.waitForActivity("CallOutgoingActivity", 2000);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testFOutgoingCallToVideoClient() {
		LinphoneTestManager.getLc().enableVideo(true, true);
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));

		solo.waitForActivity("CallOutgoingActivity", 5000);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testGOutgoingCallCancelled() {
		LinphoneTestManager.getInstance().autoAnswer = false;
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));

		solo.waitForActivity("CallOutgoingActivity", 200);
		solo.assertCurrentActivity("Expected InCall Activity", CallOutgoingActivity.class);

		solo.sleep(2000);
		waitForCallState(LinphoneManager.getLc().getCalls()[0],LinphoneCall.State.OutgoingRinging);
		
		LinphoneTestManager.getInstance().autoAnswer = true;
		
		solo.clickOnView(solo.getView(org.linphone.R.id.outgoing_hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testHOutgoingCallDeclined() {
		LinphoneTestManager.getInstance().autoAnswer = true; // Just in case
		LinphoneTestManager.getInstance().declineCall = true;
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		solo.sleep(1500);
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.error_call_declined)));
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		LinphoneTestManager.getInstance().declineCall = false;
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testIIncomingAudioCall() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		LinphoneTestManager.getLc().enableVideo(false, false);
		
		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc().invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("CallIncomingActivity", 2000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", CallIncomingActivity.class);

		solo.sleep(1000);
		/*View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(10, topLayout.getMeasuredWidth() - 10, slidersTop, slidersTop, 10);*/

		solo.clickOnView(solo.getView(org.linphone.R.id.accept));
		
		assertCallIsCorrectlyRunning();
	}

	@LargeTest
	public void testJIncomingVideoCall() {
		LinphoneTestManager.getLc().enableVideo(true, true);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc().invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("CallIncomingActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", CallIncomingActivity.class);

		/*solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(10, topLayout.getMeasuredWidth() - 10, slidersTop, slidersTop, 10);*/

		solo.clickOnView(solo.getView(org.linphone.R.id.accept));
		
		assertCallIsCorrectlyRunning();
	}

	@MediumTest
	@LargeTest
	public void testKSelfPauseResumeCall() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.pause));
		solo.sleep(1000);

		waitForCallPaused(LinphoneManager.getLc().getCalls()[0]);

		solo.clickOnView(solo.getView(org.linphone.R.id.call_pause));
		solo.sleep(1000);

		waitForCallResumed(LinphoneManager.getLc().getCalls()[0]);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testLRemotePauseResumeCall() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning();
		
		LinphoneTestManager.getLc().pauseAllCalls();
		solo.sleep(1000);

		waitForCallState(LinphoneManager.getLc().getCalls()[0], LinphoneCall.State.PausedByRemote);

		LinphoneTestManager.getLc().resumeCall(LinphoneTestManager.getLc().getCalls()[0]);
		solo.sleep(1000);

		waitForCallResumed(LinphoneManager.getLc().getCalls()[0]);
		
		solo.clickLongOnScreen(200, 200); //To ensure controls are shown
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testMSwitchOnVideoInCallIsNotAllowed() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.call));
		
		assertCallIsCorrectlyRunning();
		
		Assert.assertFalse(solo.getView(org.linphone.R.id.video).isEnabled());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testNDeclineIncomingCall() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		LinphoneTestManager.getLc().enableVideo(false, false);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc().invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("CallIncomingActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", CallIncomingActivity.class);
		
		solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(topLayout.getMeasuredWidth() - 10, 10, slidersTop, slidersTop, 10);
	}

	@MediumTest
	@LargeTest
	public void testOCancelledIncomingCall() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		LinphoneTestManager.getLc().enableVideo(false, false);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc().invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("CallIncomingActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", CallIncomingActivity.class);
		
		LinphoneTestManager.getLc().terminateAllCalls();
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testPDisplayMissedCallsNumber() {
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		Assert.assertTrue(solo.searchText("1"));
	}
	
	//TODO: Test each audio codec

	private void assertOutgoingCallIsCorrectlyRunning() {
		solo.waitForActivity("CallOutgoingActivity", 2000);
		solo.assertCurrentActivity("Expected OutgoingCall Activity", CallOutgoingActivity.class);

		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		waitForCallState(call, LinphoneCall.State.OutgoingProgress);
	}
	
	private void assertCallIsCorrectlyRunning() {
		solo.waitForActivity("CallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", CallActivity.class);

		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		waitForCallState(call, LinphoneCall.State.StreamsRunning);
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.side_menu_button));
		solo.clickOnText(aContext.getString(org.linphone.R.string.menu_settings));
	}
	
	private void goToAudioCodecsSettings() {
		goToSettings();
		
		selectItemInListOnUIThread(4);
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_audio_title));
		solo.sleep(500);
	}
	
	private boolean isAudioCodecEnabled(String mime, int rate) {
		LinphoneCore lc = LinphoneTestManager.getLc();
		for (final PayloadType pt : lc.getAudioCodecs()) {
			if (pt.getMime().equals(mime) && pt.getRate() == rate)
				return lc.isPayloadTypeEnabled(pt);
		}
		return false;
	}
	
	private void disableAllEnabledAudioCodecs() {
		goToAudioCodecsSettings();
			
		if (isAudioCodecEnabled("opus", 48000)) {
			solo.clickOnText("opus");
			solo.sleep(500);
		}
		
		if (isAudioCodecEnabled("speex", 16000)) {
			solo.clickOnText("speex");
			solo.sleep(500);
		}
			
		if (isAudioCodecEnabled("speex", 8000)) {
			solo.clickOnText("speex", 1);
			solo.sleep(500);
		}
		
		if (isAudioCodecEnabled("iLBC", 8000)) {
			solo.clickOnText("iLBC");
			solo.sleep(500);
		}
			
		if (isAudioCodecEnabled("AMR", 8000)) {
			solo.clickOnText("AMR");
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("AMRWB", 8000)) {
			solo.clickOnText("AMRWB");
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("G729", 8000)) {
			solo.clickOnText("G729");
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("GSM", 8000)) {
			solo.clickOnText("GSM");
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("G722", 8000)) {
			solo.clickOnText("G722");
			solo.sleep(500);
		}
			
		if (isAudioCodecEnabled("SILK", 24000)) {
			solo.clickOnText("SILK");
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("SILK", 16000)) {
			solo.clickOnText("SILK", 1);
			solo.sleep(500);
		}
		
		if (isAudioCodecEnabled("SILK", 8000)) {
			solo.clickOnText("SILK", 2);
			solo.sleep(500);
		}
				
		if (isAudioCodecEnabled("PCMU", 8000)) {
			solo.clickOnText("PCMU");
			solo.sleep(500);
		}
			
		if (isAudioCodecEnabled("PCMA", 8000)) {
			solo.clickOnText("PCMA");
			solo.sleep(500);
		}
	}
	
	private void goBackToDialerAfterCodecChanges() {
		solo.goBack();
		solo.goBack();
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
}
