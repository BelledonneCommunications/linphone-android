package org.linphone.tester;

import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.MediastreamerAndroidContext;
import org.linphone.mediastream.Factory;

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
		//multicast begin
		mCore.enableAudioMulticast(true);
		Assert.assertEquals(true, mCore.audioMulticastEnabled());
		mCore.enableAudioMulticast(false);
		Assert.assertEquals(false, mCore.audioMulticastEnabled());

		mCore.enableVideoMulticast(true);
		Assert.assertEquals(true, mCore.videoMulticastEnabled());
		mCore.enableVideoMulticast(false);
		Assert.assertEquals(false, mCore.videoMulticastEnabled());

		LinphoneCallParams params = mCore.createCallParams(null);
		params.enableAudioMulticast(true);
		Assert.assertEquals(true, params.audioMulticastEnabled());
		params.enableAudioMulticast(false);
		Assert.assertEquals(false, params.audioMulticastEnabled());

		params.enableVideoMulticast(true);
		Assert.assertEquals(true, params.videoMulticastEnabled());
		params.enableVideoMulticast(false);
		Assert.assertEquals(false, params.videoMulticastEnabled());


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
		//multicast end

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

		PayloadType[] audioCodecs = mCore.getAudioCodecs();
		int audioCodecsNb = audioCodecs.length;
		if (audioCodecsNb >= 1) {
			PayloadType[] newAudioCodecs = new PayloadType[audioCodecsNb - 1];
			System.arraycopy(audioCodecs, 1, newAudioCodecs, 0, audioCodecsNb - 1);
			mCore.setAudioCodecs(newAudioCodecs);
			audioCodecs = mCore.getAudioCodecs();
			Assert.assertEquals(audioCodecs.length, audioCodecsNb - 1);
		}

		//Test LinphoneFriend ref key
		String key = "12";
		LinphoneFriend friend = mCore.createFriendWithAddress("sip:lala@test.linphone.org");
		friend.setRefKey(key);
		Assert.assertEquals(friend.getRefKey(),key);

		//Test filter enablement
		Factory factory = mCore.getMSFactory();
		factory.enableFilterFromName("MSUlawEnc", false);
		Assert.assertFalse(factory.filterFromNameEnabled("MSUlawEnc"));
		factory.enableFilterFromName("MSUlawEnc", true);
	}

	@Override
	protected void setUp() throws Exception {
		// TODO Auto-generated method stub
		super.setUp();
		LinphoneCoreFactory.instance().setDebugMode(true, "WrapperTester");
		mCore = LinphoneCoreFactory.instance().createLinphoneCore(new LinphoneCoreListenerBase(),getContext());
	}

	@Override
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		mCore.destroy();
		mCore=null;
	}

}
