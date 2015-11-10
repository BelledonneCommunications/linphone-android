package org.linphone;
/*
ChatStorage.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.mediastream.Log;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;

/**
 * @author Sylvain Berfini
 */
public class ChatStorage {
	private static final int INCOMING = 1;
	private static final int OUTGOING = 0;
	private static final int READ = 1;
	private static final int NOT_READ = 0;

	private static ChatStorage instance;
	private Context context;
	private SQLiteDatabase db;
	private boolean useNativeAPI;
	private static final String TABLE_NAME = "chat";
	private static final String DRAFT_TABLE_NAME = "chat_draft";

	public synchronized static final ChatStorage getInstance() {
		if (instance == null)
			instance = new ChatStorage(LinphoneService.instance().getApplicationContext());
		return instance;
	}

	public void restartChatStorage() {
		if (instance != null)
			instance.close();
		instance = new ChatStorage(LinphoneService.instance().getApplicationContext());
	}

	private boolean isVersionUsingNewChatStorage() {
		try {
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode >= 2200;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return true;
	}

	private ChatStorage(Context c) {
	    context = c;
	    boolean useLinphoneStorage = true;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LinphoneService.instance());
		boolean updateNeeded = prefs.getBoolean(c.getString(R.string.pref_first_time_linphone_chat_storage), !LinphonePreferences.instance().isFirstLaunch());
		updateNeeded = updateNeeded && !isVersionUsingNewChatStorage();
	    useNativeAPI = useLinphoneStorage && !updateNeeded;
	    Log.d("Using native API: " + useNativeAPI);

	    if (!useNativeAPI) {
		    ChatHelper chatHelper = new ChatHelper(context);
		    db = chatHelper.getWritableDatabase();
	    }
	}

	public void close() {
		if (!useNativeAPI) {
			db.close();
		}
	}

	public void updateMessageStatus(String to, String message, int status) {
		if (useNativeAPI) {
			return;
		}

		String[] whereArgs = { String.valueOf(OUTGOING), to, message };
		Cursor c = db.query(TABLE_NAME, null, "direction LIKE ? AND remoteContact LIKE ? AND message LIKE ?", whereArgs, null, null, "id DESC");

		String id = null;
		if (c.moveToFirst()) {
			try {
				id = c.getString(c.getColumnIndex("id"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();

		if (id != null && id.length() > 0) {
			int intID = Integer.parseInt(id);
			updateMessageStatus(to, intID, status);
		}
	}

	public void updateMessageStatus(String to, int id, int status) {
		if (useNativeAPI) {
			return;
		}

		ContentValues values = new ContentValues();
		values.put("status", status);

		db.update(TABLE_NAME, values, "id LIKE " + id, null);
	}

	public int saveTextMessage(String from, String to, String message, long time) {
		if (useNativeAPI) {
			return -1;
		}

		ContentValues values = new ContentValues();
		if (from.equals("")) {
			values.put("localContact", from);
			values.put("remoteContact", to);
			values.put("direction", OUTGOING);
			values.put("read", READ);
			values.put("status", LinphoneChatMessage.State.InProgress.toInt());
		} else if (to.equals("")) {
			values.put("localContact", to);
			values.put("remoteContact", from);
			values.put("direction", INCOMING);
			values.put("read", NOT_READ);
			values.put("status", LinphoneChatMessage.State.Idle.toInt());
		}
		values.put("message", message);
		values.put("time", time);
		return (int) db.insert(TABLE_NAME, null, values);
	}

	public int saveImageMessage(String from, String to, Bitmap image, String url, long time) {
		if (useNativeAPI) {
			return -1;
		}

		ContentValues values = new ContentValues();
		if (from.equals("")) {
			values.put("localContact", from);
			values.put("remoteContact", to);
			values.put("direction", OUTGOING);
			values.put("read", READ);
			values.put("status", LinphoneChatMessage.State.InProgress.toInt());
		} else if (to.equals("")) {
			values.put("localContact", to);
			values.put("remoteContact", from);
			values.put("direction", INCOMING);
			values.put("read", NOT_READ);
			values.put("status", LinphoneChatMessage.State.Idle.toInt());
		}
		values.put("url", url);

		if (image != null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			image.compress(CompressFormat.JPEG, 100, baos);
			values.put("image", baos.toByteArray());
		}

		values.put("time", time);
		return (int) db.insert(TABLE_NAME, null, values);
	}

	public void saveImage(int id, Bitmap image) {
		if (useNativeAPI) {
			//Handled before this point
			return;
		}

		if (image == null)
			return;

		ContentValues values = new ContentValues();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(CompressFormat.JPEG, 100, baos);
		values.put("image", baos.toByteArray());

		db.update(TABLE_NAME, values, "id LIKE " + id, null);
	}

	public int saveDraft(String to, String message) {
		if (useNativeAPI) {
			//TODO
			return -1;
		}

		ContentValues values = new ContentValues();
		values.put("remoteContact", to);
		values.put("message", message);
		return (int) db.insert(DRAFT_TABLE_NAME, null, values);
	}

	public void updateDraft(String to, String message) {
		if (useNativeAPI) {
			//TODO
			return;
		}

		ContentValues values = new ContentValues();
		values.put("message", message);

		db.update(DRAFT_TABLE_NAME, values, "remoteContact LIKE \"" + to + "\"", null);
	}

	public void deleteDraft(String to) {
		if (useNativeAPI) {
			//TODO
			return;
		}

		db.delete(DRAFT_TABLE_NAME, "remoteContact LIKE \"" + to + "\"", null);
	}

	public String getDraft(String to) {
		if (useNativeAPI) {
			//TODO
			return "";
		}

		Cursor c = db.query(DRAFT_TABLE_NAME, null, "remoteContact LIKE \"" + to + "\"", null, null, null, "id ASC");

		String message = null;
		while (c.moveToNext()) {
			try {
				message = c.getString(c.getColumnIndex("message"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();

		return message;
	}

	public List<String> getDrafts() {
		List<String> drafts = new ArrayList<String>();

		if (useNativeAPI) {
			//TODO
		} else {
			Cursor c = db.query(DRAFT_TABLE_NAME, null, null, null, null, null, "id ASC");

			while (c.moveToNext()) {
				try {
					String to = c.getString(c.getColumnIndex("remoteContact"));
					drafts.add(to);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			c.close();
		}

		return drafts;
	}

	public List<ChatMessage> getMessages(String correspondent) {
		List<ChatMessage> chatMessages = new ArrayList<ChatMessage>();

		if (!useNativeAPI) {
			Cursor c = db.query(TABLE_NAME, null, "remoteContact LIKE \"" + correspondent + "\"", null, null, null, "id ASC");

			while (c.moveToNext()) {
				try {
					String message, timestamp, url;
					int id = c.getInt(c.getColumnIndex("id"));
					int direction = c.getInt(c.getColumnIndex("direction"));
					message = c.getString(c.getColumnIndex("message"));
					timestamp = c.getString(c.getColumnIndex("time"));
					int status = c.getInt(c.getColumnIndex("status"));
					byte[] rawImage = c.getBlob(c.getColumnIndex("image"));
					int read = c.getInt(c.getColumnIndex("read"));
					url = c.getString(c.getColumnIndex("url"));

					ChatMessage chatMessage = new ChatMessage(id, message, rawImage, timestamp, direction == INCOMING, status, read == READ);
					chatMessage.setUrl(url);
					chatMessages.add(chatMessage);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			c.close();
		} else {
			LinphoneChatRoom room = LinphoneManager.getLc().getOrCreateChatRoom(correspondent);
			LinphoneChatMessage[] history = room.getHistory();
			for (int i = 0; i < history.length; i++) {
				LinphoneChatMessage message = history[i];

				Bitmap bm = null;
				String url = message.getExternalBodyUrl();
				if (url != null && !url.startsWith("http")) {
					bm = BitmapFactory.decodeFile(url);
				}
				ChatMessage chatMessage = new ChatMessage(i+1, message.getText(), bm,
						String.valueOf(message.getTime()), !message.isOutgoing(),
						message.getStatus().toInt(), message.isRead());
				chatMessage.setUrl(url);
				chatMessages.add(chatMessage);
			}
		}

		return chatMessages;
	}

	public String getTextMessageForId(LinphoneChatRoom chatroom, int id) {
		String message = null;

		if (useNativeAPI) {
			LinphoneChatMessage[] history = chatroom.getHistory();
			for (LinphoneChatMessage msg : history) {
				if (msg.getStorageId() == id) {
					message = msg.getText();
					break;
				}
			}
		} else {
			Cursor c = db.query(TABLE_NAME, null, "id LIKE " + id, null, null, null, null);

			if (c.moveToFirst()) {
				try {
					message = c.getString(c.getColumnIndex("message"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			c.close();
		}

		return message;
	}

	public LinphoneChatMessage getMessage(LinphoneChatRoom chatroom, int id) {
		LinphoneChatMessage[] history = chatroom.getHistory();
		for (LinphoneChatMessage msg : history) {
			if (msg.getStorageId() == id) {
				return msg;
			}
		}
		return null;
	}

	public void removeDiscussion(String correspondent) {
		LinphoneChatRoom chatroom = LinphoneManager.getLc().getOrCreateChatRoom(correspondent);
		chatroom.deleteHistory();
	}

	public ArrayList<String> getChatList() {
		ArrayList<String> chatList = new ArrayList<String>();

		LinphoneChatRoom[] chats = LinphoneManager.getLc().getChatRooms();
		List<LinphoneChatRoom> rooms = new ArrayList<LinphoneChatRoom>();

		for (LinphoneChatRoom chatroom : chats) {
			if (chatroom.getHistory(1).length > 0) {
				rooms.add(chatroom);
			}
		}

		if (rooms.size() > 1) {
			Collections.sort(rooms, new Comparator<LinphoneChatRoom>() {
				@Override
				public int compare(LinphoneChatRoom a, LinphoneChatRoom b) {
					LinphoneChatMessage[] messagesA = a.getHistory(1);
					LinphoneChatMessage[] messagesB = b.getHistory(1);
					long atime = messagesA[0].getTime();
					long btime = messagesB[0].getTime();

					if (atime > btime)
						return -1;
					else if (btime > atime)
						return 1;
					else
						return 0;
				}
			});
		}

		for (LinphoneChatRoom chatroom : rooms) {
			chatList.add(chatroom.getPeerAddress().asStringUriOnly());
		}

		return chatList;
	}

	public void deleteMessage(LinphoneChatRoom chatroom, int id) {
		if (useNativeAPI) {
			LinphoneChatMessage[] history = chatroom.getHistory();
			for (LinphoneChatMessage message : history) {
				if (message.getStorageId() == id) {
					chatroom.deleteMessage(message);
					break;
				}
			}
		} else {
			db.delete(TABLE_NAME, "id LIKE " + id, null);
		}
	}

	public void markMessageAsRead(int id) {
		if (!useNativeAPI) {
			ContentValues values = new ContentValues();
			values.put("read", READ);
			db.update(TABLE_NAME, values, "id LIKE " + id, null);
		}
	}

	public void markConversationAsRead(LinphoneChatRoom chatroom) {
		if (useNativeAPI) {
			chatroom.markAsRead();
		}
	}


	class ChatHelper extends SQLiteOpenHelper {

	    private static final int DATABASE_VERSION = 15;
	    private static final String DATABASE_NAME = "linphone-android";

	    ChatHelper(Context context) {
	        super(context, DATABASE_NAME, null, DATABASE_VERSION);
	    }

	    @Override
	    public void onCreate(SQLiteDatabase db) {
	        db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT, localContact TEXT NOT NULL, remoteContact TEXT NOT NULL, direction INTEGER, message TEXT, image BLOB, url TEXT, time NUMERIC, read INTEGER, status INTEGER);");
	        db.execSQL("CREATE TABLE " + DRAFT_TABLE_NAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT, remoteContact TEXT NOT NULL, message TEXT);");
	    }

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME + ";");
			db.execSQL("DROP TABLE IF EXISTS " + DRAFT_TABLE_NAME + ";");
			onCreate(db);
		}
	}
}
