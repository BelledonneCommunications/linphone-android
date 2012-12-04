package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneCall;
import org.linphone.setup.SetupActivity;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class AccountzFreephonieTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {
	private static final String numberToCallToTestPSTNGateway = "0952636505";
	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public AccountzFreephonieTest() {
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
	
	private void configureFreephonieAccount() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));
		
		selectItemInListOnUIThread(6);
		solo.clickOnText(context.getString(R.string.pref_network_title));
		solo.clickOnText(context.getString(R.string.pref_transport));
		solo.clickOnText(context.getString(R.string.pref_transport_udp));
		solo.goBack();
		selectItemInListOnUIThread(0);
		
		solo.clickOnText(context.getString(R.string.setup_title));
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
		solo.clickOnView(solo.getView(R.id.setup_next));
		solo.clickOnText(context.getString(R.string.setup_login_generic));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "0953335419");
		solo.enterText((EditText) solo.getView(R.id.setup_password), "jodeeeeeer");
		solo.enterText((EditText) solo.getView(R.id.setup_domain), "freephonie.net");
		solo.clickOnText(context.getString(R.string.setup_apply));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));
		
		solo.clickOnText("0953335419@freephonie.net");
		selectItemInListOnUIThread(6);
		solo.clickOnText(context.getString(R.string.pref_default_account));
		
//		solo.clickOnText(context.getString(R.string.pref_proxy));
//		solo.enterText(0, "sip.linphone.org");
//		solo.clickOnText("OK");
//		solo.clickOnText(context.getString(R.string.pref_enable_outbound_proxy));
		
		solo.goBack();		
		solo.goBack();
	}
	
	private void deleteFreephonieAccount() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));
		
		selectItemInListOnUIThread(0);
		solo.clickOnText("junit@test.linphone.org");
		selectItemInListOnUIThread(6);
		solo.clickOnText(context.getString(R.string.pref_default_account));
		solo.goBack();

		solo.clickOnText("0953335419@freephonie.net");
		selectItemInListOnUIThread(7);
		solo.clickOnText(context.getString(R.string.pref_delete_account));
		
		selectItemInListOnUIThread(6);
		solo.clickOnText(context.getString(R.string.pref_network_title));
		solo.clickOnText(context.getString(R.string.pref_transport));
		solo.clickOnText(context.getString(R.string.pref_transport_tls));
		solo.goBack();
		
		solo.goBack();
	}
	
	private void goToDialerAndOutgoingCall() {
		solo.clickOnView(solo.getView(R.id.dialer));
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), numberToCallToTestPSTNGateway);
		solo.clickOnView(solo.getView(R.id.Call));
		
		solo.waitForActivity("InCallActivity", 2000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		solo.sleep(2000);
		try {
			Assert.assertEquals(LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCalls()[0].getState(), LinphoneCall.State.OutgoingEarlyMedia);
		} catch (AssertionError ae) {
		} finally {
			solo.clickOnView(solo.getView(R.id.hangUp));
		}
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@Override
	protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	public void testAOutgoingCallUsingPSTNGatewayAndFreephonieNetwork() {
		configureFreephonieAccount();
		goToDialerAndOutgoingCall();
		deleteFreephonieAccount();
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
