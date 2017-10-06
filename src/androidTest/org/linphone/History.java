package org.linphone;

/*
History.java
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

import org.linphone.CallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.core.LinphoneCall;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class History extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testACheckForTestCallInHistory() {
		goToHistory();

		Assert.assertTrue(solo.searchText(aContext.getString(R.string.today)));
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_test_calls_login)));
	}

	@MediumTest
	@LargeTest
	public void testBFilterMissedCalls() {
		goToHistory();

		solo.clickOnView(solo.getView(R.id.missed_calls));
		Assert.assertTrue(solo.searchText(aContext.getString(R.string.no_missed_call_history)));
	}

	public void testCCallBackFromHistory() {
		goToHistory();

		solo.clickOnText(iContext.getString(R.string.account_test_calls_login));

		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", CallActivity.class);

		solo.sleep(2000);
		Assert.assertEquals(1, LinphoneTestManager.getLc().getCallsNb());
		waitForCallState(LinphoneTestManager.getLc().getCalls()[0],LinphoneCall.State.StreamsRunning);

		solo.clickOnView(solo.getView(R.id.hang_up));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}

	@MediumTest
	@LargeTest
	public void testDDeleteOne() {
		goToHistory();

		solo.clickOnView(solo.getView(R.id.edit));
		solo.sleep(500);
		solo.clickOnCheckBox(1);
		solo.clickOnView(solo.getView(R.id.delete));
		solo.sleep(500);
		solo.clickOnView(solo.getView(R.id.delete_button));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testEDeleteAll() {
		goToHistory();

		solo.clickOnView(solo.getView(R.id.edit));
		solo.clickOnView(solo.getView(R.id.select_all));
		solo.clickOnView(solo.getView(R.id.delete));
		solo.sleep(500);
		solo.clickOnView(solo.getView(R.id.delete_button));

		Assert.assertTrue(solo.searchText(aContext.getString(R.string.no_call_history)));
	}

	private void goToHistory() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(R.id.history));
	}
}
