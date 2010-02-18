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
import java.util.ArrayList;
import java.util.List;


class LinphoneCoreImpl implements LinphoneCore {

	private final  LinphoneCoreListener mListener;
	private final long nativePtr;
	private native long newLinphoneCore(LinphoneCoreListener listener,String userConfig,String factoryConfig,Object  userdata);
	private native void iterate(long nativePtr);
	private native long getDefaultProxyConfig(long nativePtr);

	private native void setDefaultProxyConfig(long nativePtr,long proxyCfgNativePtr);
	private native int addProxyConfig(long nativePtr,long proxyCfgNativePtr);
	private native void clearAuthInfos(long nativePtr);
	
	private native void clearProxyConfigs(long nativePtr);
	private native void addAuthInfo(long nativePtr,long authInfoNativePtr);
	private native void invite(long nativePtr,String uri);
	private native void terminateCall(long nativePtr);
	private native long getRemoteAddress(long nativePtr);
	private native boolean  isInCall(long nativePtr);
	private native boolean isInComingInvitePending(long nativePtr);
	private native void acceptCall(long nativePtr);
	private native long getCallLog(long nativePtr,int position);
	private native int getNumberOfCallLogs(long nativePtr);
	
	
	LinphoneCoreImpl(LinphoneCoreListener listener, File userConfig,File factoryConfig,Object  userdata) throws IOException {
		mListener=listener;
		nativePtr = newLinphoneCore(listener,userConfig.getCanonicalPath(),factoryConfig.getCanonicalPath(),userdata);
	}
	
	public synchronized void addAuthInfo(LinphoneAuthInfo info) {
		addAuthInfo(nativePtr,((LinphoneAuthInfoImpl)info).nativePtr);
	}

	public synchronized LinphoneProxyConfig createProxyConfig(String identity, String proxy,String route,boolean enableRegister) throws LinphoneCoreException {
		return new LinphoneProxyConfigImpl(identity, proxy, route,enableRegister);
	}

	public synchronized LinphoneProxyConfig getDefaultProxyConfig() {
		long lNativePtr = getDefaultProxyConfig(nativePtr);
		if (lNativePtr!=0) {
			return new LinphoneProxyConfigImpl(lNativePtr); 
		} else {
			return null;
		}
	}

	public synchronized void invite(String uri) {
		invite(nativePtr,uri);
	}

	public synchronized void iterate() {
		iterate(nativePtr);
	}

	public synchronized void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg) {
		setDefaultProxyConfig(nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr);
	}
	public synchronized void addtProxyConfig(LinphoneProxyConfig proxyCfg) throws LinphoneCoreException{
		if (addProxyConfig(nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr) !=0) {
			throw new LinphoneCoreException("bad proxy config");
		}
	}
	public synchronized void clearAuthInfos() {
		clearAuthInfos(nativePtr);
		
	}
	public synchronized void clearProxyConfigs() {
		clearProxyConfigs(nativePtr);
	}
	public synchronized void terminateCall() {
		terminateCall(nativePtr);
	}
	public synchronized LinphoneAddress getRemoteAddress() {
		long ptr = getRemoteAddress(nativePtr);
		if (ptr==0) {
			return null;
		} else {
			return new LinphoneAddressImpl(ptr);
		}
	}
	public synchronized  boolean isIncall() {
		return isInCall(nativePtr);
	}
	public synchronized boolean isInComingInvitePending() {
		return isInComingInvitePending(nativePtr);
	}
	public synchronized void acceptCall() {
		acceptCall(nativePtr);
		
	}
	public List<LinphoneCallLog> getCallLogs() {
		List<LinphoneCallLog> logs = new ArrayList<LinphoneCallLog>(); 
		for (int i=0;i < getNumberOfCallLogs(nativePtr);i++) {
			logs.add(new LinphoneCallLogImpl(getCallLog(nativePtr, i)));
		}
		return logs;
	}
}
