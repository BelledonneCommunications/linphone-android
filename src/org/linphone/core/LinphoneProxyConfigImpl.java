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




class LinphoneProxyConfigImpl implements LinphoneProxyConfig {

	protected final long nativePtr;
	protected LinphoneProxyConfigImpl(String identity,String proxy,String route) throws LinphoneCoreException {
		nativePtr = newLinphoneProxyConfig();
		setIdentity(nativePtr,identity);
		if (setProxy(nativePtr,proxy)!=0) {
			throw new LinphoneCoreException("Bad proxy address ["+proxy+"]");
		}
	}
	
	protected void finalize() throws Throwable {
		delete(nativePtr);
	}
	private native long newLinphoneProxyConfig();
	private native void  delete(long ptr);

	//private native void edit(long ptr);
	//private native void done(long ptr);
	
	private native void setIdentity(long ptr,String identity);
	private native int setProxy(long ptr,String proxy);

	private native void enableRegister(long ptr,boolean value);
	
	public void enableRegister(boolean value) {
		//edit(nativePtr);
		enableRegister(nativePtr,value);
		//done(nativePtr);
	}

}
