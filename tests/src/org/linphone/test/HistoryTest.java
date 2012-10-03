package org.linphone.test;

import junit.framework.Assert;

import org.linphone.InCallActivity;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class HistoryTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {

	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public HistoryTest() {
		super("org.linphone", LinphoneActivity.class);
	}
	
	private void selectItemInListOnUIThread(final int item) {
		solo.sleep(500);
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ListView list = (ListView) solo.getView(android.R.id.list);
				list.setSelection(item);
			}
		});
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
		
		//Depending on previous tests, history may not be empty
		LinphoneManager.getLc().clearCallLogs();
	}
	
	public void testADisplayEmptyHistory() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.no_call_history)));
		Log.testSuccess("Empty History displayed");
	}
	
	public void testBCallToFillHistory() {
		Context context = getActivity();
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.settings));
		selectItemInListOnUIThread(4);
		solo.clickOnText(context.getString(R.string.pref_video_enable_title));
		
		solo.clickOnView(solo.getView(R.id.dialer));
		solo.clickOnView(solo.getView(R.id.Adress));
		solo.enterText((EditText) solo.getView(R.id.Adress), "cotcot@sip.linphone.org");
		solo.clickOnView(solo.getView(R.id.Call));
		
		solo.waitForActivity("InCallActivity", 2000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		Log.testSuccess("Outgoing call to cotcot successfull");
		
		solo.clickOnView(solo.getView(R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.history));
		Assert.assertTrue(solo.searchText("cotcot"));
		Log.testSuccess("Cotcot entry in history");
	}
	
	public void testCDisplayEmptyMissedCallHistory() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));
		
		solo.clickOnText(context.getString(R.string.button_missed_call));
		Assert.assertTrue(solo.searchText(context.getString(R.string.no_missed_call_history)));
		Log.testSuccess("Empty Missed Call History displayed");
	}	

	public void testDCallBackFromHistoryEntry() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));
		
		solo.clickOnText("cotcot");
		solo.waitForActivity("InCallActivity", 2000);
		solo.assertCurrentActivity("Expected InCall Activity", InCallActivity.class);
		Log.testSuccess("Outgoing call to cotcot from history successfull");
		
		solo.clickOnView(solo.getView(R.id.hangUp));
		solo.waitForActivity("LinphoneActivity", 2000);
	}
	
	public void testEDisplayHistoryDetails() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));
		
		solo.clickOnView(solo.getView(R.id.detail));
		Assert.assertTrue(solo.searchText("cotcot@sip.linphone.org"));
		Log.testSuccess("Displaying history entry details");
	}
	
	public void testFDeleteHistoryEntry() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));
		
		solo.clickOnText(context.getString(R.string.button_edit));
		solo.sleep(1000);
		solo.clickOnText("cotcot");
		solo.clickOnText(context.getString(R.string.button_ok));
		
		Assert.assertFalse(solo.searchText("cotcot", 2));
		Log.testSuccess("Clean history from one cotcot entries");
	}
	
	public void testGDeleteAllHistoryEntries() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.history));

		solo.clickOnText(context.getString(R.string.button_edit));
		solo.sleep(2000);
		solo.clickOnText(context.getString(R.string.button_delete_all));
		solo.clickOnText(context.getString(R.string.button_ok));
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.no_call_history)));
		Log.testSuccess("Clean history from all entries");
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
