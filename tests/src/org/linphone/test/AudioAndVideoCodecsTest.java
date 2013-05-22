package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class AudioAndVideoCodecsTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {
	private static final String sipAdressToCall = "macmini@sip.linphone.org";
	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public AudioAndVideoCodecsTest() {
		super("org.linphone", LinphoneActivity.class);
	}
	
	private void selectItemInListOnUIThread(final int item) {
		solo.sleep(500);
		getActivity().runOnUiThread(new Runnable() {
			public void run() {
				ListView list = (ListView) solo.getView(android.R.id.list);
				list.setSelection(item);
			}
		});
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	private boolean getBoolean(int key) {
		return getActivity().getResources().getBoolean(key);
	}
	
	private void goToAudioCodecsSettings() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		selectItemInListOnUIThread(4);
		solo.clickOnText(context.getString(R.string.pref_audio));
		solo.sleep(500);
	}
	
	private void goToVideoCodecsSettings() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		selectItemInListOnUIThread(6);
		if (solo.searchText(context.getString(R.string.pref_video), 2)) // Needed in case pref_video_enable_title contains pref_video
			solo.clickOnText(context.getString(R.string.pref_video), 2);
		else
			solo.clickOnText(context.getString(R.string.pref_video));
		solo.sleep(500);
	}
	
	private void disableAllEnabledAudioCodecs() {
		Context context = getActivity();
		
		goToAudioCodecsSettings();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_codec_speex16_key), getBoolean(R.bool.pref_codec_speex16_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_speex16));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_speex8_key), getBoolean(R.bool.pref_codec_speex8_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_speex8));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_ilbc_key), getBoolean(R.bool.pref_codec_ilbc_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_ilbc));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_amr_key), getBoolean(R.bool.pref_codec_amr_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_amr));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_amrwb_key), getBoolean(R.bool.pref_codec_amrwb_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_amrwb));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_g729_key), getBoolean(R.bool.pref_codec_g729_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_g729));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_gsm_key), getBoolean(R.bool.pref_codec_gsm_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_gsm));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_g722_key), getBoolean(R.bool.pref_codec_g722_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_g722));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_silk24_key), getBoolean(R.bool.pref_codec_silk24_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_silk24));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_silk16_key), getBoolean(R.bool.pref_codec_silk16_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_silk16));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_pcmu_key), getBoolean(R.bool.pref_codec_pcmu_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_pcmu));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_pcma_key), getBoolean(R.bool.pref_codec_pcma_default))) {
			solo.clickOnText(context.getString(R.string.pref_codec_pcma));
			solo.sleep(500);
		}
	}
	
	private void disableAllEnabledVideoCodecs() {
		Context context = getActivity();
		
		goToVideoCodecsSettings();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_vp8_key), getBoolean(R.bool.pref_video_codec_vp8_default))) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_vp8_title));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_h264_key), getBoolean(R.bool.pref_video_codec_h264_default))) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_h264_title));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_mpeg4_key), getBoolean(R.bool.pref_video_codec_mpeg4_default))) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_mpeg4_title));
			solo.sleep(500);
		}
	}
	
	private void goToDialerAndOutgoingCall(String codecTextToAssert) {
		Context context = getActivity();
		
		solo.clickOnView(solo.getView(R.id.dialer));
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), sipAdressToCall);
		solo.clickOnView(solo.getView(R.id.Call));
		
		boolean incompatibleMediaParams = solo.waitForText(context.getString(R.string.error_incompatible_media), 1, 1500);
		if (!incompatibleMediaParams) { // There is a possiblity the callee doesn't support the codec, in which case we don't have to wait for the incall view
			solo.waitForActivity("InCallActivity", 1000);
			solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
			solo.clickOnView(solo.getView(R.id.status));
			solo.waitForText(codecTextToAssert, 1, 6000);
			Assert.assertTrue(solo.searchText(codecTextToAssert, 1));
			
			View hangUp = solo.getView(R.id.hangUp);
			if (hangUp.getVisibility() == View.VISIBLE)
				solo.clickOnView(hangUp);
			else { // While on video, menu can hide. Click the first time to display it back, then click again to really hang up
				solo.clickOnView(hangUp);
				solo.sleep(1000);
				solo.clickOnView(hangUp);
			}
		} else {
			Log.testFailure("Incompatible media parameters for codec " + codecTextToAssert);
		}
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	public void testADisableVideo() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		selectItemInListOnUIThread(4);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_video_enable_key), getBoolean(R.bool.pref_video_enable_default))) {
			solo.clickOnText(context.getString(R.string.pref_video_enable_title));
			solo.sleep(500);
		}
	}
	
	public void testBOutgoingAudioCallPCMA() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_pcma));
		solo.goBack();
		
		goToDialerAndOutgoingCall("PCMA");
		solo.sleep(1000);
	}
	
	public void testCOutgoingAudioCallPCMU() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_pcmu));
		solo.goBack();
		
		goToDialerAndOutgoingCall("PCMU");
		solo.sleep(1000);
	}
	
	public void testDOutgoingAudioCallSilk16() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_silk16));
		solo.goBack();
		
		goToDialerAndOutgoingCall("SILK");
		solo.sleep(1000);
	}
	
	public void testEOutgoingAudioCallSilk24() {
//		Silk24 no longer available
//		Context context = getActivity();
//		disableAllEnabledAudioCodecs();
//		solo.clickOnText(context.getString(R.string.pref_codec_silk24));
//		solo.goBack();
//		
//		goToDialerAndOutgoingCall("SILK");
//		solo.sleep(1000);
	}
	
	public void testFOutgoingAudioCallG722() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_g722));
		solo.goBack();
		
		goToDialerAndOutgoingCall("G722");
		solo.sleep(1000);
	}
	
	public void testGOutgoingAudioCallGSM() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_gsm));
		solo.goBack();
		
		goToDialerAndOutgoingCall("GSM");
		solo.sleep(1000);
	}
	
	public void testHOutgoingAudioCallAMR() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_amr));
		solo.goBack();
		
		goToDialerAndOutgoingCall("AMR");
		solo.sleep(1000);
	}
	
	public void testIOutgoingAudioCallAMRWB() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_amrwb));
		solo.goBack();
		
		goToDialerAndOutgoingCall("AMRWB");
		solo.sleep(1000);
	}
	
	public void testJOutgoingAudioCallG729() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_g729));
		solo.goBack();
		
		goToDialerAndOutgoingCall("G729");
		solo.sleep(1000);
	}
	
	public void testKOutgoingAudioCallILBC() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_ilbc));
		solo.goBack();
		
		goToDialerAndOutgoingCall("iLBC");
		solo.sleep(1000);
	}
	
	public void testLOutgoingAudioCallSpeex8() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_speex8));
		solo.goBack();
		
		goToDialerAndOutgoingCall("speex");
		solo.sleep(1000);
	}
	
	public void testMOutgoingAudioCallSpeex16() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_speex16));
		solo.goBack();
		
		goToDialerAndOutgoingCall("speex");
		solo.sleep(1000);
	}
	
	public void testNEnableVideo() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		solo.sleep(500);
		selectItemInListOnUIThread(4);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean(context.getString(R.string.pref_video_enable_key), getBoolean(R.bool.pref_video_enable_default))) {
			solo.clickOnText(context.getString(R.string.pref_video_enable_title));
			solo.sleep(500);
		}
	}
	
	public void testOOutgoingVideoCallVP8() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_vp8_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("VP8");
		solo.sleep(1000);
	}
	
	public void testPOutgoingVideoCallH264() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_h264_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("H264");
		solo.sleep(1000);
	}
	
	public void testQOutgoingVideoCallMPG4() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_mpeg4_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("MP4V-ES");
		solo.sleep(1000);
	}
	
	@Override
	public void tearDown() throws Exception {
		if (solo.getCurrentActivity().getClass() == InCallActivity.class) {
			solo.clickOnView(solo.getView(R.id.hangUp));
		}
        solo.finishOpenedActivities();
	}
}
