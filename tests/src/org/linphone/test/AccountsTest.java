package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.setup.SetupActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.jayway.android.robotium.solo.Solo;

public class AccountsTest extends
		ActivityInstrumentationTestCase2<LinphoneActivity> {

	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public AccountsTest() {
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
	
	public void testAConfigureExistingLinphoneAccount() {
		Context context = getActivity();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int nbAccountsBefore = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		
		solo.waitForActivity("SetupActivity", 2000);
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
		Log.testSuccess("Wizard launching at first startup");
		
		solo.clickOnView(solo.getView(R.id.setup_next));
		solo.clickOnText(context.getString(R.string.setup_login_linphone));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "wizard15");
		solo.enterText((EditText) solo.getView(R.id.setup_password), "wizard15");
		solo.clickOnText(context.getString(R.string.setup_apply));
		
		if (!Hacks.hasBuiltInEchoCanceller())
			solo.waitForActivity("LinphoneActivity", 8000);
		else
			solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText("wizard15@sip.linphone.org"));
		
		int nbAccountsAfter = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		Assert.assertEquals(nbAccountsBefore + 1, nbAccountsAfter);
		Log.testSuccess("Configure existing sip.linphone.org account");
		
		String stunServer = prefs.getString(context.getString(R.string.pref_stun_server_key), "");
		Assert.assertEquals(stunServer, context.getString(R.string.default_stun));
		Log.testSuccess("Default stun server is configured");
		
		String transport = prefs.getString(context.getString(R.string.pref_transport_key), context.getString(R.string.pref_transport_udp_key));
		Assert.assertEquals(transport, context.getString(R.string.pref_transport_tls_key));
		Log.testSuccess("TLS is set by default");
		
		String proxy = prefs.getString(context.getString(R.string.pref_proxy_key), "");
		Assert.assertEquals(proxy, context.getString(R.string.default_domain) + ":5223");
		boolean outboundproxy = prefs.getBoolean(context.getString(R.string.pref_enable_outbound_proxy_key), false);
		Assert.assertEquals(outboundproxy, true);
		Log.testSuccess("Outbound proxy is configured");
		
		boolean ice = prefs.getBoolean(context.getString(R.string.pref_ice_enable_key), false);
		Assert.assertEquals(ice, true);
		Log.testSuccess("ICE is enabled");
	}
	
	public void testBConfigureExternalSIPAccount() {
		Context context = getActivity();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int nbAccountsBefore = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		Log.testSuccess("Wizard not launching at startup if account is configured");
		
		solo.clickOnView(solo.getView(R.id.settings));
		solo.clickOnText(context.getString(R.string.setup_title));
		
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
		solo.clickOnView(solo.getView(R.id.setup_next));
		solo.clickOnText(context.getString(R.string.setup_login_generic));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "junit");
		solo.enterText((EditText) solo.getView(R.id.setup_password), "junit");
		solo.enterText((EditText) solo.getView(R.id.setup_domain), "test.linphone.org");
		solo.clickOnText(context.getString(R.string.setup_apply));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText("junit@test.linphone.org"));
		
		int nbAccountsAfter = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		Assert.assertEquals(nbAccountsBefore + 1, nbAccountsAfter);
		Log.testSuccess("Configure existing generic account");
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));
		Assert.assertTrue(solo.searchText("junit@test.linphone.org"));
		
		solo.clickOnText("junit@test.linphone.org");
		selectItemInListOnUIThread(6);
		solo.clickOnText(context.getString(R.string.pref_default_account));
		solo.goBack();
		int defaultAccount = prefs.getInt(context.getString(R.string.pref_default_account_key), 0);
		solo.sleep(1000);
		Assert.assertEquals(1, defaultAccount);
		Log.testSuccess("Select another account as default");
	}
	
	public void testCDeleteConfiguredAccount() {
		Context context = getActivity();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int nbAccountsBefore = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.settings));
		Assert.assertTrue(solo.searchText("wizard15@sip.linphone.org"));

		solo.clickOnText("wizard15@sip.linphone.org");
		selectItemInListOnUIThread(7);
		solo.clickOnText(context.getString(R.string.pref_delete_account));
		
		solo.goBack();
		
		int nbAccountsAfter = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		Assert.assertEquals(nbAccountsBefore - 1, nbAccountsAfter);
		Log.testSuccess("Deleting existing SIP account");
	}
	
	public void testDTryCreatingExistingAccount() {
		Context context = getActivity();
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.settings));
		solo.clickOnText(context.getString(R.string.setup_title));
		
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
		
		solo.clickOnView(solo.getView(R.id.setup_next));
		solo.clickOnText(context.getString(R.string.setup_create_account));
		
		TextView error = (TextView) solo.getView(R.id.setup_error);
		ImageView createAccount = (ImageView) solo.getView(R.id.setup_create);
		int sleepingTime = 1500;
		
		solo.enterText((EditText) solo.getView(R.id.setup_username), "wi");
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), context.getString(R.string.wizard_username_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
		Log.testSuccess("Username incorrect error message");
		
		solo.clearEditText((EditText) solo.getView(R.id.setup_username));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "wizard15");
		solo.sleep(sleepingTime*2);
		Assert.assertEquals(error.getText(), context.getString(R.string.wizard_username_unavailable));
		Assert.assertFalse(createAccount.isEnabled());
		Log.testSuccess("Username already in use error message");
		
		solo.enterText((EditText) solo.getView(R.id.setup_password), "wi");
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), context.getString(R.string.wizard_password_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
		Log.testSuccess("Password incorrect error message");
		
		solo.clearEditText((EditText) solo.getView(R.id.setup_password));
		solo.enterText((EditText) solo.getView(R.id.setup_password), "wizard15");
		solo.enterText((EditText) solo.getView(R.id.setup_password_confirm), "wizard14");
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), context.getString(R.string.wizard_passwords_unmatched));
		Assert.assertFalse(createAccount.isEnabled());
		Log.testSuccess("Passwords doesn't match error message");
		
		solo.enterText((EditText) solo.getView(R.id.setup_email), "wizard15@lin");
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), context.getString(R.string.wizard_email_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
		Log.testSuccess("Email incorrect error message");

		solo.clearEditText((EditText) solo.getView(R.id.setup_username));
		solo.clearEditText((EditText) solo.getView(R.id.setup_password));
		solo.clearEditText((EditText) solo.getView(R.id.setup_password_confirm));
		solo.clearEditText((EditText) solo.getView(R.id.setup_email));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "wizard42");
		solo.enterText((EditText) solo.getView(R.id.setup_password), "wizard42");
		solo.enterText((EditText) solo.getView(R.id.setup_password_confirm), "wizard42");
		solo.enterText((EditText) solo.getView(R.id.setup_email), "wizard42@linphone.org");
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), "");
		Assert.assertTrue(createAccount.isEnabled());
		Log.testSuccess("All wizard fields correctly filled");
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
