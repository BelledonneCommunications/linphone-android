package org.linphone.fragments;

/*
FragmentsAvailable.java
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
    CHAT,
    CREATE_CHAT,
    INFO_GROUP_CHAT,
    GROUP_CHAT,
    MESSAGE_IMDN;

    public boolean shouldAddItselfToTheRightOf(FragmentsAvailable fragment) {
        switch (this) {
            case HISTORY_DETAIL:
                return fragment == HISTORY_LIST || fragment == HISTORY_DETAIL;

            case CONTACT_DETAIL:
                return fragment == CONTACTS_LIST || fragment == CONTACT_EDITOR || fragment == CONTACT_DETAIL;

            case CONTACT_EDITOR:
                return fragment == CONTACTS_LIST || fragment == CONTACT_DETAIL || fragment == CONTACT_EDITOR;

            case CHAT:
                return fragment == CHAT_LIST || fragment == CHAT;

            case GROUP_CHAT:
                return fragment == CHAT_LIST || fragment == GROUP_CHAT;

            case MESSAGE_IMDN:
                return fragment == GROUP_CHAT || fragment == MESSAGE_IMDN;

            default:
                return false;
        }
    }
}
