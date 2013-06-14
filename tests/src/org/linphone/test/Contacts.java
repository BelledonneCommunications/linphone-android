package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * @author Sylvain Berfini
 */
public class Contacts extends SampleTest {

	@MediumTest
	@LargeTest
	public void testAAddContactFromHistoryAndDeleteIt() {
		goToHistory();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.detail));
		solo.clickOnText(aContext.getString(org.linphone.R.string.add_to_contacts));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_add_contact));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain)));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));
		solo.clickOnText(aContext.getString(org.linphone.R.string.delete_contact));
		
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBCreateContactWithPhoneNumber() {
		goToContacts();
		
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_add_contact));
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.contact_name));
		solo.enterText(2, iContext.getString(org.linphone.test.R.string.contact_number));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testCTestContactFilter1() {
		goToContacts();

		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_sip_contacts));
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testDEditContactAddSipAddressAndRemoveNumber() {
		goToContacts();
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));
		solo.clickOnView(solo.getView(org.linphone.R.id.delete));
		solo.enterText(3, iContext.getString(org.linphone.test.R.string.contact_sip));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_sip)));
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_number)));
		
	}

	@MediumTest
	@LargeTest
	public void testETestContactFilter2() {
		goToContacts();
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_sip_contacts));
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testFStartChatFromContact() {
		goToContacts();

		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.start_chat));
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.button_send_message)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testGDeleteContact() {
		goToContacts();
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));
		solo.clickOnText(aContext.getString(org.linphone.R.string.delete_contact));
		
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}
	
	private void goToContacts() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.contacts));
	}
	
	private void goToHistory() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.history));
	}	
}
