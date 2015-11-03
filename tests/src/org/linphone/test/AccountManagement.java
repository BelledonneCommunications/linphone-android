package org.linphone.test;

import junit.framework.Assert;

import org.linphone.FragmentsAvailable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneProxyConfig;

import android.test.suitebuilder.annotation.LargeTest;
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
		
		solo.clickOnView(solo.getView(android.R.id.button1));
		
		solo.goBack();
		solo.goBack();
		solo.waitForFragmentByTag(FragmentsAvailable.DIALER.toString(), 2000);
		
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		LinphoneProxyConfig proxyConfig = proxyConfigs[0];
		waitForRegistration(proxyConfig);
		Assert.assertEquals(proxyConfigs.length, 2);
		proxyConfig = proxyConfigs[1];
		waitForRegistration(proxyConfig);
		Assert.assertTrue(proxyConfig.getIdentity(), proxyConfig.getIdentity().contains("new"));
	}

	@LargeTest
	public void testBDeleteAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_generic_login) + "new");
		selectItemInListOnUIThread(16);
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
		selectItemInListOnUIThread(14);
		solo.clickLongOnText(aContext.getString(org.linphone.R.string.pref_disable_account));
		
		solo.goBack();
		solo.goBack();
		
		Assert.assertFalse(LinphonePreferences.instance().isAccountEnabled(0));
	}
	
	@LargeTest
	public void testDEnableAccount() {
		goToSettings();
		solo.clickOnText(iContext.getString(R.string.account_linphone_login));
		selectItemInListOnUIThread(14);
		solo.clickLongOnText(aContext.getString(org.linphone.R.string.pref_disable_account));
		
		solo.goBack();
		solo.goBack();
		
		Assert.assertTrue(LinphonePreferences.instance().isAccountEnabled(0));
	}
	
	private void goToSettings() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(org.linphone.R.id.side_menu_button));
		solo.clickOnText("Settings");
	}
}
