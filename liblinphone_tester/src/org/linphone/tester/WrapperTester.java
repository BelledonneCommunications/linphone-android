package org.linphone.tester;

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.PayloadType;

import android.test.AndroidTestCase;
import junit.framework.Assert;

public class WrapperTester extends AndroidTestCase {

	public WrapperTester() {
		super();
		setName("wrapper tester");
	}
	
	LinphoneCore mCore;
	@Override
	protected void runTest() throws Throwable {
		mCore.enableAudioMulticast(true);
		Assert.assertEquals(true, mCore.audioMulticastEnabled());
		mCore.enableAudioMulticast(false);
		Assert.assertEquals(false, mCore.audioMulticastEnabled());
		
		mCore.enableVideoMulticast(true);
		Assert.assertEquals(true, mCore.videoMulticastEnabled());
		mCore.enableVideoMulticast(false);
		Assert.assertEquals(false, mCore.videoMulticastEnabled());
		
		String ip = "224.3.2.1";
		mCore.setAudioMulticastAddr(ip);
		Assert.assertEquals(ip, mCore.getAudioMulticastAddr());
		
		ip = "224.3.2.3";
		mCore.setVideoMulticastAddr(ip);
		Assert.assertEquals(ip, mCore.getVideoMulticastAddr());
		
		mCore.setAudioMulticastTtl(3);
		Assert.assertEquals(3, mCore.getAudioMulticastTtl());
		
		mCore.setVideoMulticastTtl(4);
		Assert.assertEquals(4, mCore.getVideoMulticastTtl());

		//Test setPrimaryContact
		String address = "Linphone Android <sip:linphone.android@unknown-host>";
		mCore.setPrimaryContact(address);
		Assert.assertEquals(LinphoneCoreFactory.instance().createLinphoneAddress(address).getDisplayName(),
				LinphoneCoreFactory.instance().createLinphoneAddress(mCore.getPrimaryContact()).getDisplayName());
		Assert.assertEquals(LinphoneCoreFactory.instance().createLinphoneAddress(address).getUserName(),
				LinphoneCoreFactory.instance().createLinphoneAddress(mCore.getPrimaryContact()).getUserName());

		//Test setPayloadTypeNumber
		mCore.setPayloadTypeNumber(mCore.findPayloadType("PCMU"),12);
		Assert.assertEquals(mCore.getPayloadTypeNumber(mCore.findPayloadType("PCMU")),12);
		
	}

	@Override
	protected void setUp() throws Exception {
		// TODO Auto-generated method stub
		super.setUp();
		mCore = LinphoneCoreFactory.instance().createLinphoneCore(new LinphoneCoreListenerBase(),null);
	}

	@Override
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		mCore.destroy();
		mCore=null;
	}

}
