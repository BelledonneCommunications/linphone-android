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
	EMPTY,
	HISTORY_LIST,
	HISTORY_DETAIL,
	CONTACTS_LIST,
	CONTACT_DETAIL,
	CONTACT_EDITOR,
	ABOUT,
	ACCOUNT_SETTINGS,
	SETTINGS,
	CHAT_LIST,
	CHAT;

	public boolean shouldAnimate() {
		return true;
	}

	public boolean isRightOf(FragmentsAvailable fragment) {
		switch (this) {
		case HISTORY_LIST:
			return fragment == UNKNOW;

		case HISTORY_DETAIL:
			return HISTORY_LIST.isRightOf(fragment) || fragment == HISTORY_LIST;
			
		case CONTACTS_LIST:
			return HISTORY_DETAIL.isRightOf(fragment) || fragment == HISTORY_DETAIL;
			
		case CONTACT_DETAIL:
			return CONTACTS_LIST.isRightOf(fragment) || fragment == CONTACTS_LIST;
			
		case CONTACT_EDITOR:
			return CONTACT_DETAIL.isRightOf(fragment) || fragment == CONTACT_DETAIL;
			
		case DIALER:
			return CONTACT_EDITOR.isRightOf(fragment) || fragment == CONTACT_EDITOR;

		case CHAT_LIST:
			return DIALER.isRightOf(fragment) || fragment == DIALER;

		case SETTINGS:
			return CHAT_LIST.isRightOf(fragment) || fragment == CHAT_LIST;
		
		case ABOUT:
		case ACCOUNT_SETTINGS:
			return SETTINGS.isRightOf(fragment) || fragment == SETTINGS;
			
		case CHAT:
			return CHAT_LIST.isRightOf(fragment) || fragment == CHAT_LIST;
			
		default:
			return false;
		}
	}

	public boolean shouldAddItselfToTheRightOf(FragmentsAvailable fragment) {
		switch (this) {
		case HISTORY_DETAIL:
			return fragment == HISTORY_LIST;
			
			case CONTACT_DETAIL:
			return fragment == CONTACTS_LIST;
			
		case CONTACT_EDITOR:
			return fragment == CONTACT_DETAIL || fragment == CONTACTS_LIST;
			
		case CHAT:
			return fragment == CHAT_LIST;
			
		default:
			return false;
		}
	}
}
