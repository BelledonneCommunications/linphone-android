package org.linphone;

/*
Transfer.java
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
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCall;
import org.linphone.mediastream.Log;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class Transfer extends SampleTest {
	@SmallTest
	@MediumTest
	@LargeTest
	public void testACallTransfer() {
		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.call));

		assertCallIsCorrectlyRunning();

		solo.clickOnView(solo.getView(R.id.options));
		solo.clickOnView(solo.getView(R.id.transfer));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.enterText(0, iContext.getString(R.string.conference_account_login) + "@" + iContext.getString(R.string.conference_account_domain));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.call)); // Transfer button as the same id, only the image changes

		solo.sleep(2000);
		Assert.assertTrue(LinphoneTestManager.getLc(1).getCallsNb() > 0);
		Assert.assertTrue(LinphoneTestManager.getLc(2).getCallsNb() > 0);
		LinphoneTestManager.getLc(1).terminateAllCalls();
		solo.sleep(500);
		Assert.assertTrue(LinphoneTestManager.getLc(1).getCallsNb() == 0);
		Assert.assertTrue(LinphoneTestManager.getLc(2).getCallsNb() == 0);
	}

	private void assertCallIsCorrectlyRunning() {
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", CallActivity.class);

		solo.sleep(2000);
		LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

		int retry = 0;
		while ((call.getState() == LinphoneCall.State.OutgoingProgress || call.getState() == LinphoneCall.State.IncomingReceived) && retry < 5) {
			solo.sleep(1000);
			retry++;
			Log.w("Call in progress but not running, retry = " + retry);
		}

		waitForCallState(call,LinphoneCall.State.StreamsRunning);
	}
}
