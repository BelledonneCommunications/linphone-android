package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.setup.SetupActivity;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class AccountAssistant extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testAWizardDisplayedAfterInstall() {
		solo.waitForActivity("SetupActivity", 3000);
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBLoginWithLinphoneAccount() {
		solo.waitForActivity("SetupActivity", 1000);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.setup_next));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_login_linphone));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_username), iContext.getString(R.string.account_linphone_login));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password), iContext.getString(R.string.account_linphone_pwd));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_apply));
				
		if (!Hacks.hasBuiltInEchoCanceller())
			solo.waitForActivity("LinphoneActivity", 8000);
		else
			solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_linphone_login) + "@sip.linphone.org"));

		solo.sleep(3000); //Wait for registration to be done
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(1, proxyConfigs.length);
		LinphoneProxyConfig proxyConfig = proxyConfigs[0];
		Assert.assertEquals(RegistrationState.RegistrationOk, proxyConfig.getState());
		
		//Check the wizard added sip.linphone.org custom settings
		LinphonePreferences prefs = LinphonePreferences.instance();
		String stunServer = prefs.getStunServer();
		Assert.assertEquals(aContext.getString(org.linphone.R.string.default_stun), stunServer);
				
		String transport = prefs.getTransportKey();
		Assert.assertEquals(aContext.getString(org.linphone.R.string.pref_transport_tls_key), transport);
				
		String proxy = prefs.getAccountProxy(0);
		Assert.assertEquals(aContext.getString(org.linphone.R.string.default_domain) + ":5223", proxy);
		Assert.assertEquals(true, prefs.isAccountOutboundProxySet(0));
				
		boolean ice = prefs.isIceEnabled();
		Assert.assertEquals(ice, true);
	}
	
	@LargeTest
	public void testCWizardDoesntShowWhenAccountIsConfigured() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	@LargeTest
	public void testDLoginWithGenericAccount() {
		startWizard();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.setup_next));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_login_generic));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_username), iContext.getString(R.string.account_generic_login));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password), iContext.getString(R.string.account_generic_pwd));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_domain), iContext.getString(R.string.account_generic_domain));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_apply));
		
		if (!Hacks.hasBuiltInEchoCanceller())
			solo.waitForActivity("LinphoneActivity", 8000);
		else
			solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_generic_login) + "@" + iContext.getString(R.string.account_generic_domain)));
		
		solo.sleep(3000); //Wait for registration to be done
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(proxyConfigs.length, 2);
		LinphoneProxyConfig proxyConfig = proxyConfigs[1];
		Assert.assertEquals(RegistrationState.RegistrationOk, proxyConfig.getState());
	}
	
	@LargeTest
	public void testECreateNewAccount() {
		startWizard();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.setup_next));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_create_account));
		
		TextView error = (TextView) solo.getView(org.linphone.R.id.setup_error);
		ImageView createAccount = (ImageView) solo.getView(org.linphone.R.id.setup_create);
		int sleepingTime = 1500;
				
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_username), iContext.getString(R.string.account_create_login).substring(0,2));
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), aContext.getString(org.linphone.R.string.wizard_username_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
				
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_username));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_username), iContext.getString(R.string.account_linphone_login));
		solo.sleep(sleepingTime*2);
		Assert.assertEquals(error.getText(), aContext.getString(org.linphone.R.string.wizard_username_unavailable));
		Assert.assertFalse(createAccount.isEnabled());
				
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password), iContext.getString(R.string.account_create_pwd).substring(0,2));
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), aContext.getString(org.linphone.R.string.wizard_passwords_unmatched));
		Assert.assertFalse(createAccount.isEnabled());
				
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_password));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password), iContext.getString(R.string.account_create_pwd).substring(0,2));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password_confirm), iContext.getString(R.string.account_create_pwd).substring(0,2));
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), aContext.getString(org.linphone.R.string.wizard_password_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
		
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_email), iContext.getString(R.string.account_create_email).substring(0, 12));
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), aContext.getString(org.linphone.R.string.wizard_email_incorrect));
		Assert.assertFalse(createAccount.isEnabled());
		
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_username));
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_password));
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_password_confirm));
		solo.clearEditText((EditText) solo.getView(org.linphone.R.id.setup_email));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_username), iContext.getString(R.string.account_create_login));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password), iContext.getString(R.string.account_create_pwd));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_password_confirm), iContext.getString(R.string.account_create_pwd));
		solo.enterText((EditText) solo.getView(org.linphone.R.id.setup_email), iContext.getString(R.string.account_create_email));
		solo.sleep(sleepingTime);
		Assert.assertEquals(error.getText(), "");
		Assert.assertTrue(createAccount.isEnabled());
	}
	
	@LargeTest
	public void testFCancelWizard() {
		startWizard();
		solo.clickOnView(solo.getView(org.linphone.R.id.setup_cancel));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	private void startWizard() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.settings));
		solo.clickOnText(aContext.getString(org.linphone.R.string.setup_title).substring(0, STRING_LENGTH_MAX));
	}
}
