package org.linphone;
/*
FragmentsAvailable.java
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
public enum FragmentsAvailable {
	UNKNOW,
	DIALER,
	HISTORY,
	HISTORY_DETAIL,
	CONTACTS,
	CONTACT,
	SETTINGS,
	CHATLIST,
	CHAT;
	
	public boolean shouldAddToBackStack() {
		return true;
	}

	public boolean shouldAnimate() {
		return true;
	}

	public boolean isRightOf(FragmentsAvailable fragment) {
		switch (this) {
		case HISTORY:
		case HISTORY_DETAIL:
			return fragment == UNKNOW;
			
		case CONTACTS:
		case CONTACT:
			return HISTORY.isRightOf(fragment) || fragment == HISTORY || fragment == HISTORY_DETAIL;
			
		case DIALER:
			return CONTACTS.isRightOf(fragment) || fragment == CONTACT || fragment == CONTACTS;
			
		case SETTINGS:
			return DIALER.isRightOf(fragment) || fragment == DIALER;
			
		case CHAT:
		case CHATLIST:
			return SETTINGS.isRightOf(fragment) || fragment == SETTINGS;
			
		default:
			return false;
		}
	}
}
