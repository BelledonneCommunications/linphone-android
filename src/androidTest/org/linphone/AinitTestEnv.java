package org.linphone;

/*
AinitTestEnv.java
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
