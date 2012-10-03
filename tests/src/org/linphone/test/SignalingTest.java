package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class SignalingTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {
	private static final String sipAdressToCall = "cotcot@sip.linphone.org";
	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public SignalingTest() {
		super("org.linphone", LinphoneActivity.class);
	}
	
	private void selectItemInListOnUIThread(final int item) {
		solo.sleep(500);
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ListView list = (ListView) solo.getView(android.R.id.list);
				list.setSelection(item);
			}
		});
	}
	
	private void goToNetworkSettings() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));

		selectItemInListOnUIThread(19);
		solo.clickOnText(context.getString(R.string.pref_network_title));
		solo.sleep(500);
	}
	
	private void goToDialerAndOutgoingCall() {
		solo.clickOnView(solo.getView(R.id.dialer));
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), sipAdressToCall);
		solo.clickOnView(solo.getView(R.id.Call));
		
		solo.waitForActivity("InCallActivity", 2000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		solo.waitForText("03", 1, 5000);
		solo.clickOnView(solo.getView(R.id.hangUp));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	public void testARegistrationUDP() {
		Context context = getActivity();
		goToNetworkSettings();
		
		solo.clickOnText(context.getString(R.string.pref_transport_udp));
		solo.goBack();
		solo.goBack();
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.status_connected, 2000)));
	}
	
	public void testBOutgoingCallUDP() {
		goToDialerAndOutgoingCall();
	}
	
	public void testCRegistrationTCP() {
		Context context = getActivity();
		goToNetworkSettings();
		
		solo.clickOnText(context.getString(R.string.pref_transport_tcp));
		solo.goBack();
		solo.goBack();
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.status_connected, 2000)));
	}
	
	public void testDOutgoingCallTCP() {
		goToDialerAndOutgoingCall();
	}
	
	public void testERegistrationTLS() {
		Context context = getActivity();
		goToNetworkSettings();
		
		solo.clickOnText(context.getString(R.string.pref_transport_tls));
		solo.goBack();
		solo.goBack();
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.status_connected, 2000)));
	}
	
	public void testFOutgoingCallTLS() {
		goToDialerAndOutgoingCall();
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
