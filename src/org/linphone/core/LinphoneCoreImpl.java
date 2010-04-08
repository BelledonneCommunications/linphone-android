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

	@SuppressWarnings("unused")
	private final  LinphoneCoreListener mListener; //to make sure to keep a reference on this object
	private long nativePtr = 0;
	private native long newLinphoneCore(LinphoneCoreListener listener,String userConfig,String factoryConfig,Object  userdata);
	private native void iterate(long nativePtr);
	private native long getDefaultProxyConfig(long nativePtr);

	private native void setDefaultProxyConfig(long nativePtr,long proxyCfgNativePtr);
	private native int addProxyConfig(LinphoneProxyConfig jprtoxyCfg,long nativePtr,long proxyCfgNativePtr);
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
	private native void delete(long nativePtr);
	private native void setNetworkStateReachable(long nativePtr,boolean isReachable);
	private native void setSoftPlayLevel(long nativeptr, float gain);
	private native float getSoftPlayLevel(long nativeptr);
	private native void muteMic(long nativePtr,boolean isMuted);
	private native long interpretUrl(long nativePtr,String destination);
	private native void inviteAddress(long nativePtr,long to);
	private native void sendDtmf(long nativePtr,char dtmf);
	private native void clearCallLogs(long nativePtr);
	private native boolean isMicMuted(long nativePtr);
	
	
	LinphoneCoreImpl(LinphoneCoreListener listener, File userConfig,File factoryConfig,Object  userdata) throws IOException {
		mListener=listener;
		nativePtr = newLinphoneCore(listener,userConfig.getCanonicalPath(),factoryConfig.getCanonicalPath(),userdata);
	}
	protected void finalize() throws Throwable {
		
	}
	
	public synchronized void addAuthInfo(LinphoneAuthInfo info) {
		isValid();
		addAuthInfo(nativePtr,((LinphoneAuthInfoImpl)info).nativePtr);
	}

	public synchronized LinphoneProxyConfig createProxyConfig(String identity, String proxy,String route,boolean enableRegister) throws LinphoneCoreException {
		isValid();
		return new LinphoneProxyConfigImpl(identity, proxy, route,enableRegister);
	}

	public synchronized LinphoneProxyConfig getDefaultProxyConfig() {
		isValid();
		long lNativePtr = getDefaultProxyConfig(nativePtr);
		if (lNativePtr!=0) {
			return new LinphoneProxyConfigImpl(lNativePtr); 
		} else {
			return null;
		}
	}

	public synchronized void invite(String uri) {
		isValid();
		invite(nativePtr,uri);
	}

	public synchronized void iterate() {
		isValid();
		iterate(nativePtr);
	}

	public synchronized void setDefaultProxyConfig(LinphoneProxyConfig proxyCfg) {
		isValid();
		setDefaultProxyConfig(nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr);
	}
	public synchronized void addProxyConfig(LinphoneProxyConfig proxyCfg) throws LinphoneCoreException{
		isValid();
		if (addProxyConfig(proxyCfg,nativePtr,((LinphoneProxyConfigImpl)proxyCfg).nativePtr) !=0) {
			throw new LinphoneCoreException("bad proxy config");
		}
	}
	public synchronized void clearAuthInfos() {
		isValid();
		clearAuthInfos(nativePtr);
		
	}
	public synchronized void clearProxyConfigs() {
		isValid();
		clearProxyConfigs(nativePtr);
	}
	public synchronized void terminateCall() {
		isValid();
		terminateCall(nativePtr);
	}
	public synchronized LinphoneAddress getRemoteAddress() {
		isValid();
		long ptr = getRemoteAddress(nativePtr);
		if (ptr==0) {
			return null;
		} else {
			return new LinphoneAddressImpl(ptr);
		}
	}
	public synchronized  boolean isIncall() {
		isValid();
		return isInCall(nativePtr);
	}
	public synchronized boolean isInComingInvitePending() {
		isValid();
		return isInComingInvitePending(nativePtr);
	}
	public synchronized void acceptCall() {
		isValid();
		acceptCall(nativePtr);
		
	}
	public synchronized List<LinphoneCallLog> getCallLogs() {
		isValid();
		List<LinphoneCallLog> logs = new ArrayList<LinphoneCallLog>(); 
		for (int i=0;i < getNumberOfCallLogs(nativePtr);i++) {
			logs.add(new LinphoneCallLogImpl(getCallLog(nativePtr, i)));
		}
		return logs;
	}
	public synchronized void destroy() {
		isValid();
		delete(nativePtr);
		nativePtr = 0;
	}
	
	private void isValid() {
		if (nativePtr == 0) {
			throw new RuntimeException("object already destroyed");
		}
	}
	public void setNetworkStateReachable(boolean isReachable) {
		setNetworkStateReachable(nativePtr,isReachable);
	}
	public void setSoftPlayLevel(float gain) {
		setSoftPlayLevel(nativePtr,gain);
		
	}
	public float getSoftPlayLevel() {
		return getSoftPlayLevel(nativePtr);
	}
	public void muteMic(boolean isMuted) {
		muteMic(nativePtr,isMuted);
	}
	public LinphoneAddress interpretUrl(String destination) throws LinphoneCoreException {
		long lAddress = interpretUrl(nativePtr,destination);
		if (lAddress != 0) {
			return new LinphoneAddressImpl(lAddress,true);
		} else {
			throw new LinphoneCoreException("Cannot interpret ["+destination+"]");
		}
	}
	public void invite(LinphoneAddress to) { 
		inviteAddress(nativePtr,((LinphoneAddressImpl)to).nativePtr);
	}
	public void sendDtmf(char number) {
		sendDtmf(nativePtr,number);
	}
	public void clearCallLogs() {
		clearCallLogs(nativePtr);
	}
	public boolean isMicMuted() {
		return isMicMuted(nativePtr);
	}
}
