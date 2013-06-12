package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

public class History extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testACheckForTestCallInHistory() {		
		goToHistory();
		
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.today)));
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.account_test_calls_login)));
	}
	
	private void goToHistory() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.history));
	}
}
