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
public class History extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testACheckForTestCallInHistory() {		
		goToHistory();

		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.today)));
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.account_test_calls_login)));
	}
	
	@MediumTest
	@LargeTest
	public void testBFilterMissedCalls() {		
		goToHistory();
		
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_missed_call));
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.no_missed_call_history)));
	}
	
	public void testCCallBackFromHistory() {
		goToHistory();
		
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.account_test_calls_login));
		
		solo.waitForActivity("InCallActivity", 5000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		
		solo.sleep(2000);
		Assert.assertEquals(1, LinphoneTestManager.getLc().getCallsNb());
		Assert.assertEquals(LinphoneCall.State.StreamsRunning, LinphoneTestManager.getLc().getCalls()[0].getState());
		
		solo.clickOnView(solo.getView(org.linphone.R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 5000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
	}
	
	@MediumTest
	@LargeTest
	public void testDDeleteOne() {		
		goToHistory();
		
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));	
		solo.clickOnView(solo.getView(org.linphone.R.id.delete));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testEDeleteAll() {		
		goToHistory();

		solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));	
		solo.clickOnView(solo.getView(org.linphone.R.id.deleteAll));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.no_call_history)));
	}
	
	private void goToHistory() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.history));
	}
}
