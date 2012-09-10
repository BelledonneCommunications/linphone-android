package org.linphone.test;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;

import com.jayway.android.robotium.solo.Solo;

public class CallTest extends
		ActivityInstrumentationTestCase2<LinphoneActivity> {

	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public CallTest() {
		super("org.linphone", LinphoneActivity.class);
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	public void testOutgoingCallToCotcot() {
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), "cotcot@sip.linphone.org");
		solo.clickOnView(solo.getView(R.id.Call));
		
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
	}
	
	@Override
	public void tearDown() throws Exception {
		solo.clickOnView(solo.getView(R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 2000);
        solo.finishOpenedActivities();
	}
}
