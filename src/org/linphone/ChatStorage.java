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
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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

	public ChatStorage(Context c) {
	    context = c;
	    ChatHelper chatHelper = new ChatHelper(context);
	    db = chatHelper.getWritableDatabase();
	}
	
	public void close() {
		db.close();
	}
	
	public void saveMessage(String from, String to, String message) {
		ContentValues values = new ContentValues();
		if (from.equals("")) {
			values.put("localContact", from);
			values.put("remoteContact", to);
			values.put("direction", OUTGOING);
		} else if (to.equals("")) {
			values.put("localContact", to);
			values.put("remoteContact", from);
			values.put("direction", INCOMING);
		}
		values.put("message", message);
		values.put("read", NOT_READ);
		values.put("time", System.currentTimeMillis());
		db.insert(TABLE_NAME, null, values);
	}
	
	public List<ChatMessage> getMessages(String correspondent) {
		List<ChatMessage> chatMessages = new ArrayList<ChatMessage>();
		
		Cursor c = db.query(TABLE_NAME, null, "remoteContact LIKE \"" + correspondent + "\"", null, null, null, "id ASC");
		
		while (c.moveToNext()) {
			String message, timestamp;
			int id = c.getInt(c.getColumnIndex("id"));
			int direction = c.getInt(c.getColumnIndex("direction"));
			message = c.getString(c.getColumnIndex("message"));
			timestamp = c.getString(c.getColumnIndex("time"));
			
			ChatMessage chatMessage = new ChatMessage(id, message, timestamp, direction == INCOMING);
			chatMessages.add(chatMessage);
		}
		
		return chatMessages;
	}
	
	public void removeDiscussion(String correspondent) {
		db.delete(TABLE_NAME, "remoteContact LIKE \"" + correspondent + "\"", null);
	}
	
	public ArrayList<String> getChatList() {
		ArrayList<String> chatList = new ArrayList<String>();
		
		Cursor c = db.query(TABLE_NAME, null, null, null, "remoteContact", null, "id DESC");
		while (c.moveToNext()) {
			String remoteContact = c.getString(c.getColumnIndex("remoteContact"));
			chatList.add(remoteContact);
		}
		
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

	class ChatHelper extends SQLiteOpenHelper {
	
	    private static final int DATABASE_VERSION = 2;
	    private static final String DATABASE_NAME = "linphone-android";
	    
	    ChatHelper(Context context) {
	        super(context, DATABASE_NAME, null, DATABASE_VERSION);
	    }
	
	    @Override
	    public void onCreate(SQLiteDatabase db) {
	        db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT, localContact TEXT NOT NULL, remoteContact TEXT NOT NULL, direction INTEGER, message TEXT NOT NULL, time NUMERIC, read INTEGER);");
	    }
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME + ";");
			onCreate(db);
		}
	}
}