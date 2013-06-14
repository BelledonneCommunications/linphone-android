package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.core.LinphoneCall;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * @author Sylvain Berfini
 */
public class Calls extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testASimpleCall() {
		solo.enterText(0, iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));
		solo.clickOnView(solo.getView(org.linphone.R.id.Call));
		
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		Assert.assertEquals(LinphoneCall.State.StreamsRunning, LinphoneTestManager.getLc().getCalls()[0].getState());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
}
