package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.setup.SetupActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.ListView;

import com.jayway.android.robotium.solo.Solo;

public class AccountsTest extends
		ActivityInstrumentationTestCase2<LinphoneActivity> {

	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public AccountsTest() {
		super("org.linphone", LinphoneActivity.class);
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	public void testConfigureExistingAccount() {
		Context context = getActivity();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int nbAccountsBefore = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(R.id.settings));
		solo.clickOnText(context.getString(R.string.setup_title));
		
		solo.assertCurrentActivity("Expected Setup Activity", SetupActivity.class);
		solo.clickOnView(solo.getView(R.id.setup_next));
		solo.clickOnText(context.getString(R.string.setup_login_linphone));
		solo.enterText((EditText) solo.getView(R.id.setup_username), "wizard15");
		solo.enterText((EditText) solo.getView(R.id.setup_password), "wizard15");
		solo.clickOnText(context.getString(R.string.setup_apply));
		
		solo.waitForActivity("LinphoneActivity", 2000);
		Assert.assertTrue(solo.searchText("wizard15@sip.linphone.org"));
		
		int nbAccountsAfter = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		Assert.assertEquals(nbAccountsBefore + 1, nbAccountsAfter);
	}
	
	public void testDeleteConfiguredAccount() {
		Context context = getActivity();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		int nbAccountsBefore = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.settings));
		Assert.assertTrue(solo.searchText("wizard15@sip.linphone.org"));

		solo.clickOnText("wizard15@sip.linphone.org");

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ListView list = (ListView) solo.getView(android.R.id.list);
				list.setSelection(7);
			}
		});
		solo.clickOnText(context.getString(R.string.pref_delete_account));
		
		int nbAccountsAfter = prefs.getInt(getActivity().getString(R.string.pref_extra_accounts), 0);
		Assert.assertEquals(nbAccountsBefore - 1, nbAccountsAfter);
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
