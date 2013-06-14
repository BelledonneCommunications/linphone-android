package org.linphone.test;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * @author Sylvain Berfini
 */
public class ZShutdownTestEnv extends SampleTest {
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testZShutDownLinphoneCore() {
		LinphoneTestManager.destroy();
	}
	
}
