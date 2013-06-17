package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * @author Sylvain Berfini
 */
public class CallsVideo extends SampleTest {
	
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
		Assert.assertTrue(LinphoneManager.getLc().isVideoEnabled());
	}
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testBOutgoingCallWithDefaultConfig() {
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		assertCallIsCorrectlyRunning();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	@MediumTest
	@LargeTest
	public void testCDTMFRFC2833InPCMUCall() {
		disableAllEnabledAudioCodecs();
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_pcmu));
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

	@MediumTest // TODO: Remove
	@LargeTest
	public void testIIncomingAudioCall() {
		LinphoneTestManager.getInstance().declineCall = false; // Just in case
		LinphoneTestManager.getLc().enableVideo(false, false);
		
	}

	@MediumTest // TODO: Remove
	@LargeTest
	public void testJIncomingVideoCall() {
		LinphoneTestManager.getLc().enableVideo(true, true);
		
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
		
		Assert.assertTrue(solo.getView(org.linphone.R.id.video).isEnabled());
		solo.clickOnView(solo.getView(org.linphone.R.id.video));
		Assert.assertFalse(LinphoneManager.getLc().getCurrentCall().cameraEnabled());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	private void assertCallIsCorrectlyRunning() {
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
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
	
	private void disableAllEnabledAudioCodecs() {
		goToAudioCodecsSettings();
			
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(aContext);
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_speex16_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_speex16_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_speex16));
			solo.sleep(500);
		}
			
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_speex8_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_speex8_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_speex8));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_ilbc_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_ilbc_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_ilbc));
			solo.sleep(500);
		}
			
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_amr_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_amr_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_amr));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_amrwb_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_amrwb_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_amrwb));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_g729_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_g729_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_g729));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_gsm_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_gsm_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_gsm));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_g722_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_g722_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_g722));
			solo.sleep(500);
		}
			
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_silk24_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_silk24_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_silk24));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_silk16_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_silk16_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_silk16));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_pcmu_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_pcmu_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_pcmu));
			solo.sleep(500);
		}
			
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_codec_pcma_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_codec_pcma_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_codec_pcma));
			solo.sleep(500);
		}
	}	
	
	private void disableAllEnabledVideoCodecs() {
		goToVideoCodecsSettings();
			
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(aContext);
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_video_codec_vp8_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_video_codec_vp8_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_codec_vp8_title));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_video_codec_h264_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_video_codec_h264_default))) {
			solo.clickOnText(aContext.getString(org.linphone.R.string.pref_video_codec_h264_title));
			solo.sleep(500);
		}
				
		if (prefs.getBoolean(aContext.getString(org.linphone.R.string.pref_video_codec_mpeg4_key), aContext.getResources().getBoolean(org.linphone.R.bool.pref_video_codec_mpeg4_default))) {
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
