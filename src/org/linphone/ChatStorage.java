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
		values.put("sender", from);
		values.put("receiver", to);
		values.put("message", message);
		values.put("time", System.currentTimeMillis());
		db.insert(TABLE_NAME, null, values);
	}
	
	public List<ChatMessage> getMessages(String correspondent) {
		List<ChatMessage> chatMessages = new ArrayList<ChatMessage>();
		
		Cursor c = db.query(TABLE_NAME, null, "receiver LIKE \"" + correspondent + 
				"\" OR sender LIKE \"" + correspondent + "\"", null, null, null, "id ASC");
		
		while (c.moveToNext()) {
			String to, message, timestamp;
			int id = c.getInt(c.getColumnIndex("id"));
			to = c.getString(c.getColumnIndex("receiver"));
			message = c.getString(c.getColumnIndex("message"));
			timestamp = c.getString(c.getColumnIndex("time"));
			
			ChatMessage chatMessage = new ChatMessage(id, message, timestamp, to.equals(""));
			chatMessages.add(chatMessage);
		}
		
		return chatMessages;
	}
	
	public void removeDiscussion(String correspondent) {
		db.delete(TABLE_NAME, "sender LIKE \"" + correspondent + "\"", null);
		db.delete(TABLE_NAME, "receiver LIKE \"" + correspondent + "\"", null);
	}
	
	public ArrayList<String> getChatList() {
		ArrayList<String> chatList = new ArrayList<String>();
		
		Cursor c = db.query(TABLE_NAME, null, null, null, null, null, "id DESC");
		while (c.moveToNext()) {
			String from, to;
			from = c.getString(c.getColumnIndex("sender"));
			to = c.getString(c.getColumnIndex("receiver"));
			
			if (from.equals("") && !to.equals("")) {
				if (!chatList.contains(to)) {
					chatList.add(to);
				}
			}
			else if (!from.equals("") && to.equals(""))
			{
				if (!chatList.contains(from)) {
					chatList.add(from);
				}
			}
		}
		
		return chatList;
	}

	class ChatHelper extends SQLiteOpenHelper {
	
	    private static final int DATABASE_VERSION = 1;
	    private static final String DATABASE_NAME = "linphone-android";
	    
	    ChatHelper(Context context) {
	        super(context, DATABASE_NAME, null, DATABASE_VERSION);
	    }
	
	    @Override
	    public void onCreate(SQLiteDatabase db) {
	        db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, receiver TEXT NOT NULL, message TEXT NOT NULL, time NUMERIC);");
	    }
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			
		}
	}
}