/*
LinphoneProxyConfig.java
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

public interface LinphoneProxyConfig {
	
	/**
	 * Unregister proxy config a enable edition 
	 */
	public void edit();
	/**
	 * Validate proxy config changes. Start registration in case
	 */
	public void done();
	/**
	 * sip user made by sip:username@domain
	 */
	public void setIdentity(String identity) throws LinphoneCoreException;
	/**
	 * Set proxy uri, like sip:linphone.org:5060
	 * @param proxyUri
	 * @throws LinphoneCoreException
	 */
	public void setProxy(String proxyUri) throws LinphoneCoreException;
	/**
	 * Enable register for this proxy config.
	 * Register message is issued after call to {@link #done()}
	 * @param value
	 * @throws LinphoneCoreException
	 */
	public void enableRegister(boolean value) throws LinphoneCoreException;
	/**
	 * normalize a human readable phone number into a basic string. 888-444-222 becomes 888444222
	 * @param number
	 * @return
	 */
	public String normalizePhoneNumber(String number);
	/**
	 * usefull function to automatically add internationnal prefix to e164 phone numbers
	 * @param prefix
	 */
	public void setDialPrefix(String prefix);
	/**
	 * * Sets whether liblinphone should replace "+" by "00" in dialed numbers (passed to
	 * {@link LinphoneCore#invite(String)}).
	 * @param value default value is false
	 */
	public void setDialEscapePlus(boolean value);
	
	/**
	 * rget domain host name or ip
	 * @return may be null
	 */
	public String getDomain();
}
