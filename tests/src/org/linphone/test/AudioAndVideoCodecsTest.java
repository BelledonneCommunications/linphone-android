package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class AudioAndVideoCodecsTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {
	private static final String sipAdressToCall = "miaou@sip.linphone.org";
	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public AudioAndVideoCodecsTest() {
		super("org.linphone", LinphoneActivity.class);
	}
	
	private void selectItemInListOnUIThread(final int item) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
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
	
	private void goToAudioCodecsSettings() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		solo.sleep(500);
		selectItemInListOnUIThread(11);
		solo.clickOnText(context.getString(R.string.pref_codecs));
		solo.sleep(500);
	}
	
	private void goToVideoCodecsSettings() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		solo.sleep(500);
		selectItemInListOnUIThread(14);
		solo.clickOnText(context.getString(R.string.pref_video_codecs_title), 2); //Hack: since pref_codecs = pref_video_codecs_title, we have to select the 2nd button
		solo.sleep(500);
	}
	
	private void disableAllEnabledAudioCodecs() {
		Context context = getActivity();
		
		goToAudioCodecsSettings();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_codec_speex16_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_speex16));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_speex8_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_speex8));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_ilbc_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_ilbc));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_amr_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_amr));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_gsm_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_gsm));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_g722_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_g722));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_silk24_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_silk24));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_silk16_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_silk16));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_pcmu_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_pcmu));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_codec_pcma_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_codec_pcma));
			solo.sleep(500);
		}
	}
	
	private void disableAllEnabledVideoCodecs() {
		Context context = getActivity();
		
		goToVideoCodecsSettings();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_vp8_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_vp8_title));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_h264_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_h264_title));
			solo.sleep(500);
		}
		
		if (prefs.getBoolean(context.getString(R.string.pref_video_codec_mpeg4_key), false)) {
			solo.clickOnText(context.getString(R.string.pref_video_codec_mpeg4_title));
			solo.sleep(500);
		}
	}
	
	private void goToDialerAndOutgoingCall(String codecTextToAssert) {
		solo.clickOnView(solo.getView(R.id.dialer));
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), sipAdressToCall);
		solo.clickOnView(solo.getView(R.id.Call));
		
		solo.waitForActivity("InCallActivity", 2000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		solo.clickOnView(solo.getView(R.id.status));
		solo.waitForText(codecTextToAssert, 1, 6000);
		Assert.assertTrue(solo.searchText(codecTextToAssert, 1));
		solo.clickOnView(solo.getView(R.id.hangUp));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	public void testADisableVideo() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		solo.sleep(500);
		selectItemInListOnUIThread(4);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(context.getString(R.string.pref_video_enable_key), false)) {
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
	}
	
	public void testCOutgoingAudioCallPCMU() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_pcmu));
		solo.goBack();
		
		goToDialerAndOutgoingCall("PCMU");
	}
	
	public void testDOutgoingAudioCallSilk16() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_silk16));
		solo.goBack();
		
		goToDialerAndOutgoingCall("SILK");
	}
	
	public void testEOutgoingAudioCallSilk24() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_silk24));
		solo.goBack();
		
		goToDialerAndOutgoingCall("SILK");
	}
	
	public void testFOutgoingAudioCallG722() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_g722));
		solo.goBack();
		
		goToDialerAndOutgoingCall("G722");
	}
	
	public void testGOutgoingAudioCallGSM() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_gsm));
		solo.goBack();
		
		goToDialerAndOutgoingCall("GSM");
	}
	
	public void testHOutgoingAudioCallAMR() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_amr));
		solo.goBack();
		
		goToDialerAndOutgoingCall("AMR");
	}
	
	public void testIOutgoingAudioCallILBC() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_ilbc));
		solo.goBack();
		
		goToDialerAndOutgoingCall("iLBC");
	}
	
	public void testJOutgoingAudioCallSpeex8() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_speex8));
		solo.goBack();
		
		goToDialerAndOutgoingCall("speex");
	}
	
	public void testKOutgoingAudioCallSpeex16() {
		Context context = getActivity();
		disableAllEnabledAudioCodecs();
		solo.clickOnText(context.getString(R.string.pref_codec_speex16));
		solo.goBack();
		
		goToDialerAndOutgoingCall("speex");
	}
	
	public void testLEnableVideo() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		solo.sleep(500);
		selectItemInListOnUIThread(4);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean(context.getString(R.string.pref_video_enable_key), true)) {
			solo.clickOnText(context.getString(R.string.pref_video_enable_title));
			solo.sleep(500);
		}
	}
	
	public void testMOutgoingVideoCallVP8() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_vp8_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("VP8");
	}
	
	public void testNOutgoingVideoCallH264() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_h264_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("H264");
	}
	
	public void testOOutgoingVideoCallMPG4() {
		Context context = getActivity();
		disableAllEnabledVideoCodecs();
		solo.clickOnText(context.getString(R.string.pref_video_codec_mpeg4_title));
		solo.goBack();
		
		goToDialerAndOutgoingCall("MP4V-ES");
	}
	
	@Override
	public void tearDown() throws Exception {
		if (solo.getCurrentActivity().getClass() == InCallActivity.class) {
			solo.clickOnView(solo.getView(R.id.hangUp));
		}
        solo.finishOpenedActivities();
	}
}
