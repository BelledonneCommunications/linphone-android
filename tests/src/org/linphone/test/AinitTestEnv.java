package org.linphone.test;

import junit.framework.Assert;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * @author Sylvain Berfini
 */
public class AinitTestEnv extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAInitLinphoneCore() {		
		LinphoneTestManager.createAndStart(aContext, iContext, 1);
		
		solo.sleep(5000);
		Assert.assertEquals(1, LinphoneTestManager.getLc().getProxyConfigList().length);
		waitForRegistration(LinphoneTestManager.getLc().getProxyConfigList()[0]);
	}
}
