package org.linphone;

/*
Chat.java
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

public class Chat extends SampleTest {

	@SmallTest
	@MediumTest
	@LargeTest
	public void testAEmptyChatHistory() {
		goToChat();

		LinphoneChatRoom[] chats = LinphoneTestManager.getInstance().getLc().getChatRooms();
		for (LinphoneChatRoom chatroom : chats) {
			chatroom.markAsRead();
			chatroom.deleteHistory();
		}

		Assert.assertEquals(0, LinphoneActivity.instance().getUnreadMessageCount());
	}

	@LargeTest
	public void testBDisplayEmptyChatHistory() {
		goToChat();

		Assert.assertTrue(solo.searchText(aContext.getString(R.string.no_chat_history)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testCSendTextMessage() {
		goToChat();

		solo.clickOnView(solo.getView(R.id.new_discussion));
		solo.enterText((EditText)solo.getView(R.id.search_contact_field), "sip:" + iContext.getString(R.string.account_test_calls_login) + "@" + iContext.getString(R.string.account_test_calls_domain));

		solo.enterText((EditText)solo.getView(R.id.message), iContext.getString(R.string.chat_test_text_sent));
		solo.clickOnView(solo.getView(R.id.send_message));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_sent)));
		Assert.assertEquals(iContext.getString(R.string.chat_test_text_sent), LinphoneTestManager.getInstance().lastMessageReceived);
	}


	@LargeTest
	public void testDIsNotEmptyChatHistory() {
		goToChat();
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_test_calls_login)));
	}

	@SmallTest
	@MediumTest
	@LargeTest
	public void testEReceiveTextMessage() {
		goToChat();
		solo.clickOnText(iContext.getString(R.string.account_test_calls_login));

		LinphoneChatRoom chatRoom = LinphoneTestManager.getLc().getOrCreateChatRoom("sip:" + iContext.getString(R.string.account_linphone_login) + "@" + iContext.getString(R.string.account_linphone_domain));
		LinphoneChatMessage msg = chatRoom.createLinphoneChatMessage(iContext.getString(R.string.chat_test_text_received));
		chatRoom.sendChatMessage(msg);

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_received)));
	}

	@MediumTest
	@LargeTest
	public void testFDeleteMessage() {
		goToChat();
		solo.clickOnText(iContext.getString(R.string.account_test_calls_login));

		solo.clickLongOnText(iContext.getString(R.string.chat_test_text_received));
		solo.clickOnText(aContext.getString(R.string.delete));

		solo.sleep(1000);
		Assert.assertFalse(solo.searchText(iContext.getString(R.string.chat_test_text_received)));
	}

	@MediumTest
	@LargeTest
	public void testGChatLandscape() {
		goToChat();

		solo.clickOnText(iContext.getString(R.string.account_test_calls_login));

		solo.sleep(1000);
		solo.setActivityOrientation(Solo.LANDSCAPE);
		solo.sleep(1000);

		solo.enterText((EditText) solo.getView(R.id.message), iContext.getString(R.string.chat_test_text_sent));
		solo.clickOnView(solo.getView(R.id.send_message));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.chat_test_text_sent)));
		Assert.assertEquals(iContext.getString(R.string.chat_test_text_sent), LinphoneTestManager.getInstance().lastMessageReceived);

		solo.clickOnView(solo.getView(R.id.back));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(iContext.getString(R.string.account_test_calls_login)));
	}

	@MediumTest
	@LargeTest
	public void testHDeleteConversation() {
		goToChat();

		/*solo.clickOnText(aContext.getString(R.string.button_edit));
		solo.sleep(1000);
		solo.clickOnView(solo.getView(R.id.delete));
		solo.clickOnText(aContext.getString(R.string.button_ok));

		solo.sleep(1000);
		Assert.assertTrue(solo.searchText(aContext.getString(R.string.no_chat_history)));*/
	}

	private void goToChat() {
		solo.waitForActivity("LinphoneActivity", 2000);
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);

		solo.clickOnView(solo.getView(R.id.chat));
	}

}
