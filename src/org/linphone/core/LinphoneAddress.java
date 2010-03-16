/*
LinphoneAddress.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone.core;

public interface LinphoneAddress {
	/**
	 * Human display name
	 * @return null if not set
	 */
	public String getDisplayName();
	/**
	 * userinfo 
	 * @return null if not set
	 */
	public String getUserName();
	/**
	 * 
	 * @return null if not set
	 */
	public String getDomain();
	/**
	 * set display name 
	 * @param name
	 */
	public void setDisplayName(String name);
	/**
	 * @return an URI version of the address that can be used to place a call using {@link LinphoneCore#invite(String)}
	 */
	public String toUri();
}
