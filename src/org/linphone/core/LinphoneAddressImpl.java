/*
LinphoneAddressImpl.java
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



public class LinphoneAddressImpl implements LinphoneAddress {
	protected final long nativePtr;
	boolean ownPtr = false;
	private native long newLinphoneAddressImpl(String uri,String displayName);
	private native void  delete(long ptr);
	private native String getDisplayName(long ptr);
	private native String getUserName(long ptr);
	private native String getDomain(long ptr);
	private native String toUri(long ptr);
	private native void setDisplayName(long ptr,String name);
	private native String toString(long ptr);
	
	protected LinphoneAddressImpl(String identity)  {
		nativePtr = newLinphoneAddressImpl(identity, null);
	}
	
	protected LinphoneAddressImpl(String username,String domain,String displayName)  {
		nativePtr = newLinphoneAddressImpl("sip:"+username+"@"+domain, displayName);
	}
	protected LinphoneAddressImpl(long aNativePtr,boolean javaOwnPtr)  {
		nativePtr = aNativePtr;
		ownPtr=javaOwnPtr;
	}
	protected LinphoneAddressImpl(long aNativePtr)  {
		nativePtr = aNativePtr;
		ownPtr=false;
	}
	protected void finalize() throws Throwable {
		if (ownPtr) delete(nativePtr);
	}
	public String getDisplayName() {
		return getDisplayName(nativePtr);
	}
	public String getDomain() {
		return getDomain(nativePtr);
	}
	public String getUserName() {
		return getUserName(nativePtr);
	}
	
	public String toString() {
		return toString(nativePtr);
	}
	public String toUri() {
		return toUri(nativePtr);	
	}
	public void setDisplayName(String name) {
		setDisplayName(nativePtr,name);
	}
	public String asString() {
		return toString();
	}
	public String asStringUriOnly() {
		return toUri(nativePtr);
	}
	public void clean() {
		throw new RuntimeException("Not implemented");
	}
	public String getPort() {
		return String.valueOf(getPortInt());
	}
	public int getPortInt() {
		return getPortInt();
	}
	public void setDomain(String domain) {
		throw new RuntimeException("Not implemented");
	}
	public void setPort(String port) {
		throw new RuntimeException("Not implemented");
	}
	public void setPortInt(int port) {
		throw new RuntimeException("Not implemented");
	}
	public void setUserName(String username) {
		throw new RuntimeException("Not implemented");
	}
 
}
