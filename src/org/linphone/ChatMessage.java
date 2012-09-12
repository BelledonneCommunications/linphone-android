package org.linphone;

import org.linphone.core.LinphoneChatMessage;
/*
ChatMessage.java
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

/**
 * @author Sylvain Berfini
 */
public class ChatMessage {
	private String message;
	private String timestamp;
	private boolean incoming;
	private int status;
	private int id;
	
	public ChatMessage(int id, String message, String timestamp, boolean incoming, int status) {
		super();
		this.id = id;
		this.message = message;
		this.timestamp = timestamp;
		this.incoming = incoming;
		this.status = status;
	}
	
	public int getId() {
		return id;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public String getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	
	public boolean isIncoming() {
		return incoming;
	}
	
	public void setIncoming(boolean incoming) {
		this.incoming = incoming;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
	public LinphoneChatMessage.State getStatus() {
		return LinphoneChatMessage.State.fromInt(status);
	}
}
