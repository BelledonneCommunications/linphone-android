package org.linphone;

/*
Contacts.java
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


import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class Contacts extends SampleTest {

	@MediumTest
	@LargeTest
	public void testAAddContactFromHistoryAndDeleteIt() {
		goToHistory();

		solo.clickOnView(solo.getView(R.id.detail));
		solo.clickOnView(solo.getView(R.id.add_contact));
		solo.clickOnView(solo.getView(R.id.newContact));

		Assert.assertTrue(solo.searchText(aContext.getString(R.string.account_test_calls_login)));

		solo.enterText(0, iContext.getString(R.string.contact_name));
		solo.clickOnView(solo.getView(R.id.ok));

		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_name)));

		solo.clickOnText(iContext.getString(R.string.contact_name));
		solo.clickOnView(solo.getView(R.id.deleteContact));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.delete_button));

		Assert.assertFalse(solo.searchText(iContext.getString(R.string.contact_name)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testBCreateContactWithPhoneNumber() {
		goToContacts();

		solo.clickOnView(solo.getView(R.id.newContact));
		solo.enterText(0, iContext.getString(R.string.contact_name));
		solo.enterText(3, iContext.getString(R.string.contact_number));
		solo.clickOnView(solo.getView(R.id.ok));
		solo.sleep(2000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testCTestContactFilter1() {
		goToContacts();

		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_name)));
		solo.clickOnView(solo.getView(R.id.linphone_contacts));
		Assert.assertFalse(solo.searchText(iContext.getString(R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testDEditContactAddSipAddressAndRemoveNumber() {
		goToContacts();
		solo.clickOnView(solo.getView(R.id.all_contacts));

		solo.clickOnText(iContext.getString(R.string.contact_name));
		solo.clickOnView(solo.getView(R.id.editContact));
		solo.clickOnView(solo.getView(R.id.delete_field));
		solo.enterText(2, iContext.getString(R.string.contact_sip));
		solo.clickOnView(solo.getView(R.id.ok));

		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_sip)));
		Assert.assertFalse(solo.searchText(iContext.getString(R.string.contact_number)));

	}

	@MediumTest
	@LargeTest
	public void testETestContactFilter2() {
		goToContacts();

		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_name)));
		solo.clickOnView(solo.getView(R.id.linphone_contacts));
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.contact_name)));
	}

	@MediumTest
	@LargeTest
	public void testFStartChatFromContact() {
		goToContacts();

		solo.clickOnText(iContext.getString(R.string.contact_name));
		solo.clickOnView(solo.getView(R.id.contact_chat));
		//Assert.assertTrue(solo.search(aContext.get(R.string.send_message)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testGDeleteContact() {
		goToContacts();

		solo.clickOnText(iContext.getString(R.string.contact_name));
		solo.clickOnView(solo.getView(R.id.deleteContact));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.delete_button));
		Assert.assertFalse(solo.searchText(iContext.getString(R.string.contact_name)));
	}

	private void goToContacts() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(R.id.contacts));
	}

	private void goToHistory() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(R.id.history));
	}
}
