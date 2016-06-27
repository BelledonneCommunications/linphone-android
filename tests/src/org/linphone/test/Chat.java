package org.linphone.test;

import junit.framework.Assert;

import org.linphone.ChatStorage;
import org.linphone.LinphoneActivity;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.mediastream.Log;

import com.robotium.solo.Solo;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;


/**
 * @author Sylvain Berfini
 */
public class Chat extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAEmptyChatHistory() {
		goToChat();
		
		ChatStorage chatStorage = ChatStorage.getInstance();
		for (String conversation : chatStorage.getChatList()) {
			chatStorage.removeDiscussion(conversation);
		}
		
		Assert.assertEquals(0, LinphoneActivity.instance().getUnreadMessageCount());
	}
	
	@LargeTest
	public void testBDisplayEmptyChatHistory() {
		goToChat();
		
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.no_chat_history)));
	}
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testCSendTextMessage() {
		goToChat();

		solo.clickOnView(solo.getView(org.linphone.R.id.new_discussion));
		solo.enterText((EditText)solo.getView(org.linphone.R.id.search_contact_field), "sip:" + iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));

		solo.enterText((EditText)solo.getView(org.linphone.R.id.message), iContext.getString(R.string.chat_test_text_sent));
		solo.clickOnView(solo.getView(org.linphone.R.id.send_message));
		
		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_sent)));
		Assert.assertEquals(iContext.getString(R.string.chat_test_text_sent), LinphoneTestManager.getInstance().lastMessageReceived);
	}


	@LargeTest
	public void testDIsNotEmptyChatHistory() {
		goToChat();
		Assert.assertTrue(solo.searchText(iContext.getString(org.linphone.test.R.string.account_test_calls_login)));
	}
	
	@SmallTest
	@MediumTest
	@LargeTest
	public void testEReceiveTextMessage() {
		goToChat();
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.account_test_calls_login));
		
		LinphoneChatRoom chatRoom = LinphoneTestManager.getLc().getOrCreateChatRoom("sip:" + iContext.getString(R.string.account_linphone_login) + "@" + iContext.getString(R.string.account_linphone_domain));
		LinphoneChatMessage msg = chatRoom.createLinphoneChatMessage(iContext.getString(R.string.chat_test_text_received));
		chatRoom.sendMessage(msg, new LinphoneChatMessage.StateListener() {
			@Override
			public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg,
					State state) {
				Log.e("Chat message state = " + state.toString());
			}
		});

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_received)));
	}

	@MediumTest
	@LargeTest
	public void testFDeleteMessage() {
		goToChat();
		solo.clickOnText(iContext.getString(org.linphone.test.R.string.account_test_calls_login));
		
		solo.clickLongOnText(iContext.getString(R.string.chat_test_text_received));
		solo.clickOnText(aContext.getString(org.linphone.R.string.delete));

		solo.sleep(1000);
		Assert.assertFalse(solo.searchText(iContext.getString(R.string.chat_test_text_received)));
	}

	@MediumTest
	@LargeTest
	public void testGChatLandscape() {
		goToChat();

		solo.clickOnText(iContext.getString(org.linphone.test.R.string.account_test_calls_login));

		solo.sleep(1000);
		solo.setActivityOrientation(Solo.LANDSCAPE);
		solo.sleep(1000);

		solo.enterText((EditText) solo.getView(org.linphone.R.id.message), iContext.getString(R.string.chat_test_text_sent));
		solo.clickOnView(solo.getView(org.linphone.R.id.send_message));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_sent)));
		Assert.assertEquals(iContext.getString(R.string.chat_test_text_sent), LinphoneTestManager.getInstance().lastMessageReceived);

		solo.clickOnView(solo.getView(org.linphone.R.id.back));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_test_calls_login)));
	}

	@MediumTest
	@LargeTest
	public void testHDeleteConversation() {
		goToChat();
		
		/*solo.clickOnText(aContext.getString(org.linphone.R.string.button_edit));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(org.linphone.R.id.delete));
		solo.clickOnText(aContext.getString(org.linphone.R.string.button_ok));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(aContext.getString(org.linphone.R.string.no_chat_history)));*/
	}
	
	private void goToChat() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		solo.clickOnView(solo.getView(org.linphone.R.id.chat));
	}
	
}
