package org.linphone.test;

import junit.framework.Assert;

import org.linphone.FragmentsAvailable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneProxyConfig;

import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;

/**
 * @author Sylvain Berfini
 */
public class AccountManagement extends SampleTest {

	@LargeTest
	public void testAEditAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_generic_login) + "@" + iContext.getString(R.string.account_generic_domain));
		solo.clickOnText(aContext.getString(org.linphone.R.string.pref_username));
		solo.enterText(0, "new");
		//Hack to validate the dialog
		solo.sendKey(KeyEvent.KEYCODE_ENTER);
		solo.sendKey(KeyEvent.KEYCODE_TAB);
		solo.sendKey(KeyEvent.KEYCODE_ENTER);
		//End of hack
		
		solo.goBack();
		solo.goBack();
		solo.waitForFragmentByTag(FragmentsAvailable.DIALER.toString(), 2000);
		
		solo.sleep(2000); //Wait for registration to be done
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(proxyConfigs.length, 2);
		LinphoneProxyConfig proxyConfig = proxyConfigs[1];
		Assert.assertEquals(RegistrationState.RegistrationOk, proxyConfig.getState());
		Assert.assertTrue(proxyConfig.getIdentity(), proxyConfig.getIdentity().contains("new"));
	}

	@LargeTest
	public void testBDeleteAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_generic_login) + "new");
		selectItemInListOnUIThread(8);
		solo.clickLongOnText(aContext.getString(org.linphone.R.string.pref_delete_account));
		
		solo.goBack();
		solo.goBack();
		
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(1, proxyConfigs.length);
	}

	@LargeTest
	public void testCDisableAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_linphone_login));
		selectItemInListOnUIThread(6);
		solo.clickLongOnText(aContext.getString(org.linphone.R.string.pref_disable_account));
		
		solo.goBack();
		solo.goBack();
		
		Assert.assertFalse(LinphonePreferences.instance().isAccountEnabled(0));
	}
	
	@LargeTest
	public void testDEnableAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_linphone_login));
		selectItemInListOnUIThread(6);
		solo.clickLongOnText(aContext.getString(org.linphone.R.string.pref_disable_account));
		
		solo.goBack();
		solo.goBack();
		
		Assert.assertTrue(LinphonePreferences.instance().isAccountEnabled(0));
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.settings));
	}
}
