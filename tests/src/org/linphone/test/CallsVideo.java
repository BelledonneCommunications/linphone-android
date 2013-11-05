package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.IncomingCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Log;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * @author Sylvain Berfini
 */
public class CallsVideo extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testAInit() {
		LinphoneTestManager.getLc().enableVideo(true, true); // Just in case
		
		//Disable video
		goToSettings();

		selectItemInListOnUIThread(3);
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_enable_title));
		solo.sleep(500);
		
		solo.goBack();
		solo.sleep(1000);
		Assert.assertTrue(LinphoneManager.getLc().isVideoEnabled());
	}
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testBOutgoingCallWithDefaultConfig() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		assertCallIsRunningWithVideo();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
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
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		solo.clickOnView(solo.getView(org.linphone.R.id.Digit3));
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		
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
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		solo.clickOnView(solo.getView(org.linphone.R.id.Digit3));
		solo.clickOnView(solo.getView(org.linphone.R.id.dialer));
		
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		
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
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testFOutgoingCallToVideoClient() {
		LinphoneTestManager.getLc().enableVideo(true, true);
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		assertCallIsRunningWithVideo();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testGOutgoingCallCancelled() {
		LinphoneTestManager.getInstance().autoAnswer = false;
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		Assert.assertEquals(LinphoneCall.State.OutgoingRinging, LinphoneManager.getLc().getCalls()[0].getState());
		
		LinphoneTestManager.getInstance().autoAnswer = true;
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testHOutgoingCallDeclined() {
		LinphoneTestManager.getInstance().autoAnswer = true; // Just in case
		LinphoneTestManager.getInstance().declineCall = true;
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		solo.sleep(500);
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.error_call_declined)));
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		LinphoneTestManager.getInstance().declineCall = false;
	}

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
		
		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", IncomingCallActivity.class);

		solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(10, topLayout.getMeasuredWidth() - 10, slidersTop, slidersTop, 10);
		
		assertCallIsCorrectlyRunning();
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testJIncomingVideoCall() {
		LinphoneTestManager.getLc().enableVideo(true, true);

		solo.sleep(2000);
		try {
			LinphoneTestManager.getLc().invite("sip:" + iContext.getString(org.linphone.test.R.string.account_linphone_login) + "@" + iContext.getString(org.linphone.test.R.string.account_linphone_domain));
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		solo.waitForActivity("IncomingCallActivity", 5000);
		solo.assertCurrentActivity("Expected Incoming Call Activity", IncomingCallActivity.class);

		solo.sleep(1000);
		View topLayout = solo.getView(org.linphone.R.id.topLayout);
		int topLayoutHeigh = topLayout.getMeasuredHeight();
		DisplayMetrics dm = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int topOffset = dm.heightPixels - topLayoutHeigh;
		int slidersTop = topLayoutHeigh - 80 - topOffset; // 80 is the bottom margin set in incoming.xml
		solo.drag(10, topLayout.getMeasuredWidth() - 10, slidersTop, slidersTop, 10);
		
		assertCallIsCorrectlyRunning();
		assertCallIsRunningWithVideo();
	}
	
	//TODO: Test each video codec

	@MediumTest
	@LargeTest
	public void testKSelfPauseResumeCall() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.pause));
		LinphoneCall.State state = LinphoneManager.getLc().getCalls()[0].getState();
		solo.sleep(1000);
		
		Assert.assertTrue(LinphoneCall.State.Paused == state || LinphoneCall.State.Pausing == state);
		solo.clickOnView(solo.getView(org.linphone.R.id.pause));
		solo.sleep(1000);
		
		state = LinphoneManager.getLc().getCalls()[0].getState();
		Assert.assertTrue(LinphoneCall.State.Resuming == state || LinphoneCall.State.StreamsRunning == state);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testLRemotePauseResumeCall() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		LinphoneTestManager.getLc().pauseAllCalls();
		solo.sleep(1000);
		
		Assert.assertEquals(LinphoneCall.State.PausedByRemote, LinphoneManager.getLc().getCalls()[0].getState());
		LinphoneTestManager.getLc().resumeCall(LinphoneTestManager.getLc().getCalls()[0]);
		solo.sleep(1000);
		
		LinphoneCall.State state = LinphoneManager.getLc().getCalls()[0].getState();
		Assert.assertTrue(LinphoneCall.State.Resuming == state || LinphoneCall.State.StreamsRunning == state);
		
		solo.clickLongOnScreen(200, 200); //To ensure controls are shown
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testMSwitchOffVideoInCall() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		assertCallIsRunningWithVideo();
		
		Assert.assertTrue(solo.getView(org.linphone.R.id.video).isEnabled());
		solo.clickOnView(solo.getView(org.linphone.R.id.video));
		Assert.assertFalse(LinphoneManager.getLc().getCurrentCall().cameraEnabled());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	private void assertCallIsRunningWithVideo() {
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		Assert.assertTrue(call.getCurrentParamsCopy().getVideoEnabled());
	}
	
	private void assertCallIsCorrectlyRunning() {
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
		
		int retry = 0;
		while ((call.getState() == LinphoneCall.State.OutgoingProgress || call.getState() == LinphoneCall.State.IncomingReceived) && retry < 5) {
			solo.sleep(1000);
			retry++;
			Log.w("Call in progress but not running, retry = " + retry);
		}
		
		Assert.assertEquals(LinphoneCall.State.StreamsRunning, call.getState());
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.settings));
	}
	
	private void goToAudioCodecsSettings() {
		goToSettings();
		
		selectItemInListOnUIThread(4);
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_audio));
		solo.sleep(500);
	}
		
	private void goToVideoCodecsSettings() {
		goToSettings();
		
		selectItemInListOnUIThread(6);
		if (solo.searchText(aContext.getString(org.linphone.R.string.pref_video), 2)) // Needed in case pref_video_enable_title contains pref_video
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video), 2);
		else
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video));
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
	
	private boolean isVideoCodecEnabled(String mime) {
		LinphoneCore lc = LinphoneTestManager.getLc();
		for (final PayloadType pt : lc.getVideoCodecs()) {
			if (pt.getMime().equals(mime))
				return lc.isPayloadTypeEnabled(pt);
		}
		return false;
	}
	
	private void disableAllEnabledVideoCodecs() {
		goToVideoCodecsSettings();
			
		if (isVideoCodecEnabled("VP8")) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_codec_vp8_title));
			solo.sleep(500);
		}
				
		if (isVideoCodecEnabled("H264")) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_codec_h264_title));
			solo.sleep(500);
		}
				
		if (isVideoCodecEnabled("MP4V-ES")) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_codec_mpeg4_title));
			solo.sleep(500);
		}
	}
	
	private void goBackToDialerAfterCodecChanges()
	{
		solo.goBack();
		solo.goBack();
		
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
}
