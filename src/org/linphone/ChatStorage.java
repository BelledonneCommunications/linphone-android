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
import java.util.List;

import org.linphone.core.LinphoneChatMessage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;

/**
 * @author Sylvain Berfini
 */
public class ChatStorage {
	private static final int INCOMING = 1;
	private static final int OUTGOING = 0;
	private static final int READ = 1;
	private static final int NOT_READ = 0;
	private Context context;
	private SQLiteDatabase db;
	private static final String TABLE_NAME = "chat";
	private static final String DRAFT_TABLE_NAME = "chat_draft";

	public ChatStorage(Context c) {
	    context = c;
	    ChatHelper chatHelper = new ChatHelper(context);
	    db = chatHelper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
	}
	
	public void updateMessageStatus(String to, String message, int status) {
		ContentValues values = new ContentValues();
		values.put("status", status);
		
		String where = "direction LIKE ? AND remoteContact LIKE ? AND message LIKE ?";
		String[] whereArgs = { String.valueOf(OUTGOING), to, message };
		
		db.update(TABLE_NAME, values, where, whereArgs);
	}
	
	public void updateMessageStatus(String to, int id, int status) {
		ContentValues values = new ContentValues();
		values.put("status", status);
		
		db.update(TABLE_NAME, values, "id LIKE " + id, null);
	}
	
	public int saveMessage(String from, String to, String message) {
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
		values.put("time", System.currentTimeMillis());
		return (int) db.insert(TABLE_NAME, null, values);
	}
	
	public int saveMessage(String from, String to, Bitmap image) {
		if (image == null)
			return -1;
		
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
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(CompressFormat.JPEG, 100, baos);
		values.put("image", baos.toByteArray());
		
		values.put("time", System.currentTimeMillis());
		return (int) db.insert(TABLE_NAME, null, values);
	}
	
	public int saveDraft(String to, String message) {
		ContentValues values = new ContentValues();
		values.put("remoteContact", to);
		values.put("message", message);
		return (int) db.insert(DRAFT_TABLE_NAME, null, values);
	}
	
	public void updateDraft(String to, String message) {
		ContentValues values = new ContentValues();
		values.put("message", message);
		
		db.update(DRAFT_TABLE_NAME, values, "remoteContact LIKE \"" + to + "\"", null);
	}
	
	public void deleteDraft(String to) {
		db.delete(DRAFT_TABLE_NAME, "remoteContact LIKE \"" + to + "\"", null);
	}
	
	public String getDraft(String to) {
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
		
		return drafts;
	}
	
	public List<ChatMessage> getMessages(String correspondent) {
		List<ChatMessage> chatMessages = new ArrayList<ChatMessage>();
		
		Cursor c = db.query(TABLE_NAME, null, "remoteContact LIKE \"" + correspondent + "\"", null, null, null, "id ASC");
		
		while (c.moveToNext()) {
			try {
				String message, timestamp;
				int id = c.getInt(c.getColumnIndex("id"));
				int direction = c.getInt(c.getColumnIndex("direction"));
				message = c.getString(c.getColumnIndex("message"));
				timestamp = c.getString(c.getColumnIndex("time"));
				int status = c.getInt(c.getColumnIndex("status"));
				byte[] rawImage = c.getBlob(c.getColumnIndex("image"));
				
				ChatMessage chatMessage = new ChatMessage(id, message, rawImage, timestamp, direction == INCOMING, status);
				chatMessages.add(chatMessage);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();
		
		return chatMessages;
	}

	public String getTextMessageForId(int id) {
		Cursor c = db.query(TABLE_NAME, null, "id LIKE " + id, null, null, null, null);

		String message = null;
		if (c.moveToFirst()) {
			try {
				message = c.getString(c.getColumnIndex("message"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();
		
		return message;
	}
	
	public void removeDiscussion(String correspondent) {
		db.delete(TABLE_NAME, "remoteContact LIKE \"" + correspondent + "\"", null);
	}
	
	public ArrayList<String> getChatList() {
		ArrayList<String> chatList = new ArrayList<String>();
		
		Cursor c = db.query(TABLE_NAME, null, null, null, "remoteContact", null, "id DESC");
		while (c != null && c.moveToNext()) {
			try {
				String remoteContact = c.getString(c.getColumnIndex("remoteContact"));
				chatList.add(remoteContact);
			} catch (IllegalStateException ise) {
			}
		}
		c.close();
		
		return chatList;
	}

	public void deleteMessage(int id) {
		db.delete(TABLE_NAME, "id LIKE " + id, null);
	}
	
	public void markMessageAsRead(int id) {
		ContentValues values = new ContentValues();
		values.put("read", READ);
		db.update(TABLE_NAME, values, "id LIKE " + id, null);
	}
	
	public int getUnreadMessageCount() {
		return db.query(TABLE_NAME, null, "read LIKE " + NOT_READ, null, null, null, null).getCount();
	}

	public int getUnreadMessageCount(String contact) {
		return db.query(TABLE_NAME, null, "remoteContact LIKE \"" + contact + "\" AND read LIKE " + NOT_READ, null, null, null, null).getCount();
	}

	public byte[] getRawImageFromMessage(int id) {
		String[] columns = { "image" };
		Cursor c = db.query(TABLE_NAME, columns, "id LIKE " + id + "", null, null, null, null);
		
		if (c.moveToFirst()) {
			byte[] rawImage = c.getBlob(c.getColumnIndex("image"));
			c.close();
			return rawImage;
		}

		c.close();
		return null;
	}

	class ChatHelper extends SQLiteOpenHelper {
	
	    private static final int DATABASE_VERSION = 14;
	    private static final String DATABASE_NAME = "linphone-android";
	    
	    ChatHelper(Context context) {
	        super(context, DATABASE_NAME, null, DATABASE_VERSION);
	    }
	
	    @Override
	    public void onCreate(SQLiteDatabase db) {
	        db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT, localContact TEXT NOT NULL, remoteContact TEXT NOT NULL, direction INTEGER, message TEXT, image BLOB, time NUMERIC, read INTEGER, status INTEGER);");
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