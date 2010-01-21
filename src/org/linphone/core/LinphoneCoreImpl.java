/*
LinphoneCoreImpl.java
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

import java.io.File;
import java.io.IOException;


class LinphoneCoreImpl implements LinphoneCore {

	private final  LinphoneCoreListener mListener;
	private final long nativePtr;
	private native long newLinphoneCore(LinphoneCoreListener listener,String userConfig,String factoryConfig,Object  userdata);
	private native void iterate(long nativePtr);
	private native void setDefaultProxyConfig(long nativePtr,long proxyCfgNativePtr);
	private native int addProxyConfig(long nativePtr,long proxyCfgNativePtr);
	private native void addAuthInfo(long nativePtr,long authInfoNativePtr);
	LinphoneCoreImpl(LinphoneCoreListener listener, File userConfig,File factoryConfig,Object  userdata) throws IOException {
		mListener=listener;
		nativePtr = newLinphoneCore(listener,userConfig.getCanonicalPath(),factoryConfig.getCanonicalPath(),userdata);
	}
	
	public void addAuthInfo(LinphoneAuthInfo info) {
		addAuthInfo(nativePtr,((LinphoneAuthInfoImpl)info).nativePtr);
	}

	public LinphoneProxyConfig createProxyConfig(String identity, String proxy,String route) throws LinphoneCoreException {
		return new LinphoneProxyConfigImpl(identity, proxy, route);
	}

	public LinphoneProxyConfig getDefaultProxyConfig() {
		throw new RuntimeException("not implemenetd yet");
	}

	public void invite(String url) {
		throw new RuntimeException("not implemenetd yet");
	}

	public void iterate() {
		iterate(nativePtr);
	}

	public void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg) {
		setDefaultProxyConfig(nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr);
	}
	public void addtProxyConfig(LinphoneProxyConfig proxyCfg) throws LinphoneCoreException{
		if (addProxyConfig(nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr) !=0) {
			throw new LinphoneCoreException("bad proxy config");
		}
	}
	
	
}
