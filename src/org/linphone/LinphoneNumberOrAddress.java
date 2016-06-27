/*
LinphoneNumberOrAddress.java
Copyright (C) 2016  Belledonne Communications, Grenoble, France

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

package org.linphone;

import java.io.Serializable;

public class LinphoneNumberOrAddress implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2301689469730072896L;
	
	private boolean isSIPAddress;
	private String value, oldValueForUpdatePurpose;
	
	public LinphoneNumberOrAddress(String v, boolean isSIP) {
		value = v;
		isSIPAddress = isSIP;
		oldValueForUpdatePurpose = null;
	}
	
	public LinphoneNumberOrAddress(String v, boolean isSip, String old) {
		this(v, isSip);
		oldValueForUpdatePurpose = old;
	}

	public boolean isSIPAddress() {
		return isSIPAddress;
	}
	
	public String getOldValue() {
		return oldValueForUpdatePurpose;
	}

	public void setOldValue(String v) {
		oldValueForUpdatePurpose = v;
	}
	
	public String getValue() {
		return value;
	}
	
	public void setValue(String v) {
		value = v;
	}
}
