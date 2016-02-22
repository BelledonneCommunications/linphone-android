package org.linphone.test;

import junit.framework.Assert;

import org.linphone.ContactsListFragment;
import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.widget.ScrollView;

/**
 * @author Sylvain Berfini
 */
public class Contacts extends SampleTest {

	@MediumTest
	@LargeTest
	public void testAAddContactFromHistoryAndDeleteIt() {
		goToHistory();
		
		solo.clickOnView(solo.getView(org.linphone.R.id.detail));
		solo.clickOnView(solo.getView(org.linphone.R.id.add_contact));
		solo.clickOnView(solo.getView(org.linphone.R.id.newContact));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.account_test_calls_login) + "@" + iContext.getString(org.linphone.test.R.string.account_test_calls_domain)));
		
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.ok));

		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.deleteContact));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.delete_button));

		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBCreateContactWithPhoneNumber() {
		goToContacts();

		solo.clickOnView(solo.getView(org.linphone.R.id.newContact));
		solo.enterText(0, iContext.getString(org.linphone.test.R.string.contact_name));
		solo.enterText(3, iContext.getString(org.linphone.test.R.string.contact_number));
		solo.clickOnView(solo.getView(org.linphone.R.id.ok));
		
		if (ContactsListFragment.instance() != null) {
			ContactsListFragment.instance().invalidate();
			solo.sleep(1000);
		}
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testCTestContactFilter1() {
		goToContacts();

		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		solo.clickOnView(solo.getView(org.linphone.R.id.linphone_contacts));
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testDEditContactAddSipAddressAndRemoveNumber() {
		goToContacts();
		solo.clickOnView(solo.getView(org.linphone.R.id.all_contacts));
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.editContact));
		solo.clickOnView(solo.getView(org.linphone.R.id.delete_field));
		solo.enterText(2, iContext.getString(org.linphone.test.R.string.contact_sip));
		solo.clickOnView(solo.getView(org.linphone.R.id.ok));
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_sip)));
		Assert.assertFalse(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_number)));
		
	}

	@MediumTest
	@LargeTest
	public void testETestContactFilter2() {
		goToContacts();
		
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
		solo.clickOnView(solo.getView(org.linphone.R.id.linphone_contacts));
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testFStartChatFromContact() {
		goToContacts();

		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.contact_chat));
		//Assert.assertTrue(solo.search(aContext.get(org.linphone.R.string.send_message)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testGDeleteContact() {
		goToContacts();
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.contact_name));
		solo.clickOnView(solo.getView(org.linphone.R.id.deleteContact));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.delete_button));
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
