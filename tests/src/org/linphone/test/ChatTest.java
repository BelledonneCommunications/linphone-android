package org.linphone.test;

import junit.framework.Assert;

import org.linphone.LinphoneActivity;
import org.linphone.R;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;

import com.jayway.android.robotium.solo.Solo;

public class ChatTest extends ActivityInstrumentationTestCase2<LinphoneActivity> {

	private static final String testTextMessage = "Test";
	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public ChatTest() {
		super("org.linphone", LinphoneActivity.class);
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	public void testADisplayEmptyChatList() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.chat));
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.no_chat_history)));
		Log.testSuccess("Empty chat list displayed");
	}
	
	public void testBStartConversationAndSaveItAsDraft() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.chat));
		
		solo.enterText((EditText) solo.getView(R.id.newFastChat), "cotcot@sip.linphone.org");
		solo.clickOnText(context.getString(R.string.button_new_chat));
		solo.sleep(1000);
		
		solo.enterText((EditText) solo.getView(R.id.message), testTextMessage);
		solo.goBack();
		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(context.getString(R.string.draft)));
		Log.testSuccess("Conversation created and message saved as draft");
	}
	
	public void testCUseSavedDraftMessageAndSentIt() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.chat));
		
		solo.clickOnText(context.getString(R.string.draft));
		Assert.assertTrue(solo.searchText(testTextMessage));
		Log.testSuccess("Draft successfully restored");
		
		solo.clickOnText(context.getString(R.string.button_send_message));
		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(testTextMessage));
		Log.testSuccess("Chat message sent");
		
		solo.goBack();
		solo.sleep(1000);
		Assert.assertTrue(solo.searchText("cotcot"));
		Assert.assertFalse(solo.searchText(context.getString(R.string.draft), true));
		Log.testSuccess("Conversation created but no more saved as draft");
	}
	
	public void testDDeleteMessage() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.chat));
		
		solo.clickOnText("cotcot");
		Assert.assertTrue(solo.searchText(testTextMessage));
		solo.clickLongOnText(testTextMessage);
		solo.sleep(1000);
		
		solo.clickOnText(context.getString(R.string.delete));
		solo.sleep(1000);
		Assert.assertFalse(solo.searchText(testTextMessage));
		Log.testSuccess("Chat message successfully deleted");
	}
	
	public void testEIncomingMessageAndDeleteConversation() {
		Context context = getActivity();
		
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		solo.clickOnView(solo.getView(R.id.chat));
		
		solo.enterText((EditText) solo.getView(R.id.newFastChat), "junit");
		solo.clickOnText(context.getString(R.string.button_new_chat));
		solo.sleep(1000);
		
		solo.enterText((EditText) solo.getView(R.id.message), testTextMessage);
		solo.clickOnText(context.getString(R.string.button_send_message));
		solo.sleep(1000);
		
		Assert.assertTrue(solo.searchText(testTextMessage, 2));
		Log.testSuccess("Chat message successfully received");
		
		solo.goBack();
		Assert.assertTrue(solo.searchText("junit", 2));
		solo.clickOnView(solo.getView(R.id.clearFastChatField));
		EditText fastChat = (EditText) solo.getView(R.id.newFastChat);
		Assert.assertEquals(fastChat.getText().toString(), "");
		Log.testSuccess("Fast new chat cleaned");
		
		solo.clickOnText(context.getString(R.string.button_edit));
		solo.clickOnText("junit");
		solo.clickOnText(context.getString(R.string.button_ok));
		solo.sleep(1000);
		
		Assert.assertTrue(solo.searchText(context.getString(R.string.no_chat_history)));
		Log.testSuccess("Conversation successfully deleted");
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
