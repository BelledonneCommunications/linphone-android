package org.linphone;

/*
AccountAssistant.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import junit.framework.Assert;

import org.linphone.assistant.AssistantActivity;
import org.linphone.core.LinphoneNatPolicy;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

public class AccountAssistant extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAWizardDisplayedAfterInstall() {
		LinphonePreferences.instance().setXmlrpcUrl("https://sip3.linphone.org:444/inapp.php");
		solo.waitForActivity("AssistantActivity", 3000);
		solo.assertCurrentActivity("Expected Assistant Activity", AssistantActivity.class);
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBLoginWithLinphoneAccount() {
		solo.waitForActivity("AssistantActivity", 3000);
		solo.assertCurrentActivity("Expected Assistant Activity", AssistantActivity.class);

		solo.clickOnView(solo.getView(R.id.login_linphone));
		solo.clickOnView(solo.getView(R.id.use_username));
		solo.enterText((EditText) solo.getView(R.id.assistant_username), aContext.getString(R.string.account_linphone_login));
		solo.enterText((EditText) solo.getView(R.id.assistant_password), aContext.getString(R.string.account_linphone_pwd));
		solo.clickOnView(solo.getView(R.id.assistant_apply));
		solo.sleep(3000);
		solo.clickOnView(solo.getView(R.id.assistant_skip));
		solo.sleep(1000);

		//Test download openh264
		if (LinphoneManager.getLc().downloadOpenH264Enabled()) {
			Assert.assertTrue(solo.searchText(aContext.getString(R.string.assistant_codec_down_question)));
			solo.clickOnView(solo.getView(R.id.answerNo));
		}

		solo.waitForActivity("LinphoneActivity", 8000);
		Assert.assertTrue(solo.searchText(aContext.getString(R.string.account_linphone_login) + "@sip.linphone.org"));

		solo.sleep(3000); //Wait for registration to be done
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(1, proxyConfigs.length);
		LinphoneProxyConfig proxyConfig = proxyConfigs[0];
		waitForRegistration(proxyConfig);

		//Check the wizard added sip.linphone.org custom settings
		LinphoneNatPolicy natPolicy = proxyConfig.getNatPolicy();
		LinphonePreferences prefs = LinphonePreferences.instance();
		String stunServer = natPolicy.getStunServer();
		Assert.assertEquals(aContext.getString(R.string.default_stun), stunServer);

		String transport = prefs.getAccountTransportKey(0);
		Assert.assertEquals(aContext.getString(R.string.pref_transport_tls_key), transport);

		String proxy = prefs.getAccountProxy(0);
		Assert.assertEquals("<sip:" + aContext.getString(R.string.default_domain) + ";transport=tls>", proxy);

		String username = prefs.getAccountUsername(0);
		Assert.assertEquals(aContext.getString(R.string.account_linphone_login), username);

		boolean ice = natPolicy.iceEnabled();
		Assert.assertEquals(ice, true);
	}

	@LargeTest
	public void testCWizardDoesntShowWhenAccountIsConfigured() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@LargeTest
	public void testDLoginWithGenericAccount() {
		startAssistant();

		solo.clickOnView(solo.getView(R.id.login_generic));
		solo.enterText((EditText) solo.getView(R.id.assistant_username), aContext.getString(R.string.account_generic_login));
		solo.enterText((EditText) solo.getView(R.id.assistant_password), aContext.getString(R.string.account_generic_pwd));
		solo.enterText((EditText) solo.getView(R.id.assistant_domain), aContext.getString(R.string.account_generic_domain));
		solo.clickOnView(solo.getView(R.id.assistant_apply));

		if (!Hacks.hasBuiltInEchoCanceller())
			solo.waitForActivity("LinphoneActivity", 8000);
		else
			solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText(aContext.getString(R.string.account_generic_login) + "@" + aContext.getString(R.string.account_generic_domain)));

		solo.sleep(3000); //Wait for registration to be done
		LinphoneProxyConfig[] proxyConfigs = LinphoneManager.getLc().getProxyConfigList();
		Assert.assertEquals(proxyConfigs.length, 2);
		LinphoneProxyConfig proxyConfig = proxyConfigs[1];
		waitForRegistration(proxyConfig);
	}

	@LargeTest
	public void testECreateNewAccount() {
		int sleepingTime = 1500;
		startAssistant();

		solo.clickOnView(solo.getView(R.id.create_account));

		if (aContext.getResources().getBoolean(R.bool.isTablet)) {
			solo.enterText((EditText) solo.getView(R.id.username), iContext.getString(R.string.account_create_login).substring(0, 2));
			solo.sleep(200);
			TextView error = (TextView) solo.getView(R.id.username_error);
			Button createAccount = (Button) solo.getView(R.id.assistant_create);

			Assert.assertEquals(error.getText(), aContext.getString(R.string.wizard_username_incorrect));
			Assert.assertFalse(createAccount.isEnabled());

			solo.clearEditText((EditText) solo.getView(R.id.username));
			solo.enterText((EditText) solo.getView(R.id.username), iContext.getString(R.string.account_linphone_login));
			solo.sleep(sleepingTime * 2);
			Assert.assertEquals(error.getText(), aContext.getString(R.string.wizard_username_unavailable));
			Assert.assertFalse(createAccount.isEnabled());

			solo.enterText((EditText) solo.getView(R.id.password), iContext.getString(R.string.account_create_pwd).substring(0, 2));
			solo.sleep(sleepingTime);
			error = (TextView) solo.getView(R.id.confirm_password_error);
			Assert.assertEquals(error.getText(), aContext.getString(R.string.wizard_passwords_unmatched));
			Assert.assertFalse(createAccount.isEnabled());

			solo.clearEditText((EditText) solo.getView(R.id.password));
			solo.enterText((EditText) solo.getView(R.id.password), iContext.getString(R.string.account_create_pwd).substring(0, 2));
			solo.enterText((EditText) solo.getView(R.id.confirm_password), iContext.getString(R.string.account_create_pwd).substring(0, 2));
			solo.sleep(sleepingTime);
			error = (TextView) solo.getView(R.id.password_error);
			Assert.assertEquals(error.getText(), aContext.getString(R.string.wizard_password_incorrect));
			Assert.assertFalse(createAccount.isEnabled());

			solo.enterText((EditText) solo.getView(R.id.email), iContext.getString(R.string.account_create_email).substring(0, 12));
			solo.sleep(sleepingTime);
			error = (TextView) solo.getView(R.id.email_error);
			Assert.assertEquals(error.getText(), aContext.getString(R.string.wizard_email_incorrect));
			Assert.assertFalse(createAccount.isEnabled());

			solo.clearEditText((EditText) solo.getView(R.id.username));
			solo.clearEditText((EditText) solo.getView(R.id.password));
			solo.clearEditText((EditText) solo.getView(R.id.confirm_password));
			solo.clearEditText((EditText) solo.getView(R.id.email));
			solo.enterText((EditText) solo.getView(R.id.username), iContext.getString(R.string.account_create_login));
			solo.enterText((EditText) solo.getView(R.id.password), iContext.getString(R.string.account_create_pwd));
			solo.enterText((EditText) solo.getView(R.id.confirm_password), iContext.getString(R.string.account_create_pwd));
			solo.enterText((EditText) solo.getView(R.id.email), iContext.getString(R.string.account_create_email));
			solo.sleep(sleepingTime);
			Assert.assertEquals(error.getText(), "");
			Assert.assertTrue(createAccount.isEnabled());
		} else {
			solo.clickOnView(solo.getView(R.id.select_country));

			solo.enterText((EditText) solo.getView(R.id.search_country), aContext.getString(R.string.account_create_country_name));
			solo.sleep(500);
			solo.clickInList(0);
			solo.sleep(500);
			Assert.assertEquals(((Button)solo.getView(R.id.select_country)).getText().toString().toLowerCase(),
					aContext.getString(R.string.account_create_country_name).toLowerCase());
			Assert.assertEquals(((EditText)solo.getView(R.id.dial_code)).getText().toString(),
					"+"+aContext.getString(R.string.account_create_country_code));

			Assert.assertEquals(((TextView)solo.getView(R.id.sip_uri)).getText().toString(), "");
			solo.enterText((EditText) solo.getView(R.id.phone_number),
					aContext.getString(R.string.account_create_phone_number).substring(2));
			Assert.assertEquals(((TextView)solo.getView(R.id.phone_number_error)).getText().toString(),
					aContext.getString(R.string.phone_number_too_short));
			solo.clearEditText((EditText) solo.getView(R.id.phone_number));
			solo.enterText((EditText) solo.getView(R.id.phone_number), aContext.getString(R.string.account_create_phone_number)+"1234");
			Assert.assertEquals(((TextView)solo.getView(R.id.phone_number_error)).getText().toString(),
					aContext.getString(R.string.phone_number_too_long));
			solo.clearEditText((EditText) solo.getView(R.id.phone_number));
			solo.enterText((EditText) solo.getView(R.id.phone_number), aContext.getString(R.string.account_create_phone_number));
			Assert.assertEquals(((TextView)solo.getView(R.id.phone_number_error)).getText().toString(), "");
			Assert.assertEquals(((TextView)solo.getView(R.id.sip_uri)).getText().toString(),
					aContext.getString(R.string.assistant_create_account_phone_number_address)
							+ " <" + "+" + aContext.getString(R.string.account_create_country_code)
							+ aContext.getString(R.string.account_create_phone_number)
							+ "@" + aContext.getString(R.string.default_domain) + ">");

			solo.clickOnView(solo.getView(R.id.use_username));
			Assert.assertEquals(((TextView)solo.getView(R.id.sip_uri)).getText().toString(), "");
			solo.enterText((EditText) solo.getView(R.id.username), aContext.getString(R.string.account_create_login));
			Assert.assertEquals(((TextView)solo.getView(R.id.sip_uri)).getText().toString(),
					aContext.getString(R.string.assistant_create_account_phone_number_address)
							+ " <" + aContext.getString(R.string.account_create_login)
							+ "@" + aContext.getString(R.string.default_domain) + ">");

			Button createAccount = (Button) solo.getView(R.id.assistant_create);

			Assert.assertTrue(createAccount.isEnabled());
		}
	}

	@LargeTest
	public void testFCancelWizard() {
		startAssistant();
		solo.clickOnView(solo.getView(R.id.assistant_cancel));

		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	private void startAssistant() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(R.id.side_menu_button));
		solo.clickOnText(aContext.getString(R.string.menu_assistant));
	}
}
