/*
LinphoneProxyConfigImpl.java
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

import org.linphone.Linphone;

import android.util.Log;




class LinphoneProxyConfigImpl implements LinphoneProxyConfig {

	protected final long nativePtr;
	boolean ownPtr = false;
	protected LinphoneProxyConfigImpl(String identity,String proxy,String route, boolean enableRegister) throws LinphoneCoreException {
		nativePtr = newLinphoneProxyConfig();
		setIdentity(identity);
		setProxy(proxy);
		enableRegister(enableRegister);
		ownPtr=true;
		Log.w(Linphone.TAG, "route ["+route+"] not used yet");
	}
	protected LinphoneProxyConfigImpl(long aNativePtr)  {
		nativePtr = aNativePtr;
		ownPtr=false;
	}
	protected void finalize() throws Throwable {
		if (ownPtr) delete(nativePtr);
	}
	private native long newLinphoneProxyConfig();
	private native void  delete(long ptr);

	private native void edit(long ptr);
	private native void done(long ptr);
	
	private native void setIdentity(long ptr,String identity);
	private native int setProxy(long ptr,String proxy);

	private native void enableRegister(long ptr,boolean value);
	
	private native void setDialPrefix(long ptr, String prefix);
	
	private native String normalizePhoneNumber(long ptr,String number);
	
	private native String getDomain(long ptr);
	
	public void enableRegister(boolean value) {
		enableRegister(nativePtr,value);
	}

	public void done() {
		done(nativePtr);
	}

	public void edit() {
		edit(nativePtr);
	}

	public void setIdentity(String identity) throws LinphoneCoreException {
		setIdentity(nativePtr,identity);
	}

	public void setProxy(String proxyUri) throws LinphoneCoreException {
		if (setProxy(nativePtr,proxyUri)!=0) {
			throw new LinphoneCoreException("Bad proxy address ["+proxyUri+"]");
		}
	}
	public String normalizePhoneNumber(String number) {
		return normalizePhoneNumber(nativePtr,number);
	}
	public void setDialPrefix(String prefix) {
		setDialPrefix(nativePtr, prefix);
	}
	public String getDomain() {
		return getDomain(nativePtr);
	}
}
