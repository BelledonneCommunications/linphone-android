package org.linphone;

/*
ContactAddress.java
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

public class ContactAddress {
	LinphoneContact contact;
	String address;
	boolean isLinphoneContact;
	boolean isSelect = false;

	public boolean isSelect() {
		return isSelect;
	}

	public void setSelect(boolean select) {
		isSelect = select;
	}

	public boolean isLinphoneContact() {
		return isLinphoneContact;
	}

	public ContactAddress(LinphoneContact c, String a, boolean isLC){
		this.contact = c;
		this.address = a;
		this.isLinphoneContact = isLC;
	}
}
