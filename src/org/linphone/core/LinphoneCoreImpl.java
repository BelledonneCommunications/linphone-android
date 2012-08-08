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
	private native Object invite(long nativePtr,String uri);
	private native void terminateCall(long nativePtr, long call);
	private native long getRemoteAddress(long nativePtr);
	private native boolean  isInCall(long nativePtr);
	private native boolean isInComingInvitePending(long nativePtr);
	private native void acceptCall(long nativePtr, long call);
	private native long getCallLog(long nativePtr,int position);
	private native int getNumberOfCallLogs(long nativePtr);
	private native void delete(long nativePtr);
	private native void setNetworkStateReachable(long nativePtr,boolean isReachable);
	private native void setPlaybackGain(long nativeptr, float gain);
	private native float getPlaybackGain(long nativeptr);
	private native void muteMic(long nativePtr,boolean isMuted);
	private native long interpretUrl(long nativePtr,String destination);
	private native Object inviteAddress(long nativePtr,long to);
	private native Object inviteAddressWithParams(long nativePtrLc,long to, long nativePtrParam);
	private native void sendDtmf(long nativePtr,char dtmf);
	private native void clearCallLogs(long nativePtr);
	private native boolean isMicMuted(long nativePtr);
	private native long findPayloadType(long nativePtr, String mime, int clockRate);
	private native int enablePayloadType(long nativePtr, long payloadType,	boolean enable);
	private native void enableEchoCancellation(long nativePtr,boolean enable);
	private native boolean isEchoCancellationEnabled(long nativePtr);
	private native Object getCurrentCall(long nativePtr) ;
	private native void playDtmf(long nativePtr,char dtmf,int duration);
	private native void stopDtmf(long nativePtr);
	private native void setVideoWindowId(long nativePtr, Object wid);
	private native void setPreviewWindowId(long nativePtr, Object wid);
	private native void setDeviceRotation(long nativePtr, int rotation);
	private native void addFriend(long nativePtr,long friend);
	private native void setPresenceInfo(long nativePtr,int minute_away, String alternative_contact,int status);
	private native long createChatRoom(long nativePtr,String to);
	private native void enableVideo(long nativePtr,boolean vcap_enabled,boolean display_enabled);
	private native boolean isVideoEnabled(long nativePtr);
	private native void setFirewallPolicy(long nativePtr, int enum_value);
	private native int getFirewallPolicy(long nativePtr);
	private native void setStunServer(long nativePtr, String stun_server);
	private native String getStunServer(long nativePtr);
	private native long createDefaultCallParams(long nativePtr);
	private native int updateCall(long ptrLc, long ptrCall, long ptrParams);
	private native void setUploadBandwidth(long nativePtr, int bw);
	private native void setDownloadBandwidth(long nativePtr, int bw);
	private native void setPreferredVideoSize(long nativePtr, int width, int heigth);
	private native int[] getPreferredVideoSize(long nativePtr);
	private native void setRing(long nativePtr, String path);
	private native String getRing(long nativePtr);
	private native void setRootCA(long nativePtr, String path);
	private native long[] listVideoPayloadTypes(long nativePtr);
	private native long[] getProxyConfigList(long nativePtr);
	private native long[] listAudioPayloadTypes(long nativePtr);
	private native void enableKeepAlive(long nativePtr,boolean enable);
	private native boolean isKeepAliveEnabled(long nativePtr);
	private native int startEchoCalibration(long nativePtr,Object data);
	private native int getSignalingTransportPort(long nativePtr, int code);
	private native void setSignalingTransportPorts(long nativePtr, int udp, int tcp, int tls);
	private native void enableIpv6(long nativePtr,boolean enable);
	private native void adjustSoftwareVolume(long nativePtr,int db);
	private native int pauseCall(long nativePtr, long callPtr);
	private native int pauseAllCalls(long nativePtr);
	private native int resumeCall(long nativePtr, long callPtr);
	private native void setUploadPtime(long nativePtr, int ptime);
	private native void setDownloadPtime(long nativePtr, int ptime);
	private native void setZrtpSecretsCache(long nativePtr, String file);
	private native void enableEchoLimiter(long nativePtr2, boolean val);
	private native int setVideoDevice(long nativePtr2, int id);
	private native int getVideoDevice(long nativePtr2);
	private native int getMediaEncryption(long nativePtr);
	private native void setMediaEncryption(long nativePtr, int menc);
	private native boolean isMediaEncryptionMandatory(long nativePtr);
	private native void setMediaEncryptionMandatory(long nativePtr, boolean yesno);
	
	
	LinphoneCoreImpl(LinphoneCoreListener listener, File userConfig,File factoryConfig,Object  userdata) throws IOException {
		mListener=listener;
		nativePtr = newLinphoneCore(listener,userConfig.getCanonicalPath(),factoryConfig.getCanonicalPath(),userdata);
	}
	LinphoneCoreImpl(LinphoneCoreListener listener) throws IOException {
		mListener=listener;
		nativePtr = newLinphoneCore(listener,null,null,null);
	}
	
	protected void finalize() throws Throwable {
		
	}
	
	public synchronized void addAuthInfo(LinphoneAuthInfo info) {
		isValid();
		addAuthInfo(nativePtr,((LinphoneAuthInfoImpl)info).nativePtr);
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

	public synchronized LinphoneCall invite(String uri) {
		isValid();
		return (LinphoneCall)invite(nativePtr,uri);
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
	public synchronized void terminateCall(LinphoneCall aCall) {
		isValid();
		if (aCall!=null)terminateCall(nativePtr,((LinphoneCallImpl)aCall).nativePtr);
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
	public synchronized void acceptCall(LinphoneCall aCall) {
		isValid();
		acceptCall(nativePtr,((LinphoneCallImpl)aCall).nativePtr);
		
	}
	public synchronized LinphoneCallLog[] getCallLogs() {
		isValid();
		LinphoneCallLog[] logs = new LinphoneCallLog[getNumberOfCallLogs(nativePtr)]; 
		for (int i=0;i < getNumberOfCallLogs(nativePtr);i++) {
			logs[i] = new LinphoneCallLogImpl(getCallLog(nativePtr, i));
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
	public synchronized void setNetworkReachable(boolean isReachable) {
		setNetworkStateReachable(nativePtr,isReachable);
	}
	public synchronized void setPlaybackGain(float gain) {
		setPlaybackGain(nativePtr,gain);
		
	}
	public synchronized float getPlaybackGain() {
		return getPlaybackGain(nativePtr);
	}
	public synchronized void muteMic(boolean isMuted) {
		muteMic(nativePtr,isMuted);
	}
	public synchronized LinphoneAddress interpretUrl(String destination) throws LinphoneCoreException {
		long lAddress = interpretUrl(nativePtr,destination);
		if (lAddress != 0) {
			return new LinphoneAddressImpl(lAddress,true);
		} else {
			throw new LinphoneCoreException("Cannot interpret ["+destination+"]");
		}
	}
	public synchronized LinphoneCall invite(LinphoneAddress to) throws LinphoneCoreException { 
		LinphoneCall call = (LinphoneCall)inviteAddress(nativePtr,((LinphoneAddressImpl)to).nativePtr);
		if (call!=null) {
			return call;
		} else {
			throw new LinphoneCoreException("Unable to invite address " + to.asString());
		}
	}

	public synchronized void sendDtmf(char number) {
		sendDtmf(nativePtr,number);
	}
	public synchronized void clearCallLogs() {
		clearCallLogs(nativePtr);
	}
	public synchronized boolean isMicMuted() {
		return isMicMuted(nativePtr);
	}
	public synchronized PayloadType findPayloadType(String mime, int clockRate) {
		isValid();
		long playLoadType = findPayloadType(nativePtr, mime, clockRate);
		if (playLoadType == 0) {
			return null;
		} else {
			return new PayloadTypeImpl(playLoadType);
		}
	}
	public synchronized void enablePayloadType(PayloadType pt, boolean enable)
			throws LinphoneCoreException {
		isValid();
		if (enablePayloadType(nativePtr,((PayloadTypeImpl)pt).nativePtr,enable) != 0) {
			throw new LinphoneCoreException("cannot enable payload type ["+pt+"]");
		}
		
	}
	public synchronized void enableEchoCancellation(boolean enable) {
		isValid();
		enableEchoCancellation(nativePtr, enable);
	}
	public synchronized boolean isEchoCancellationEnabled() {
		isValid();
		return isEchoCancellationEnabled(nativePtr);
		
	}

	public synchronized LinphoneCall getCurrentCall() {
		isValid();
		return (LinphoneCall)getCurrentCall(nativePtr);
	}
	
	public int getPlayLevel() {
		// TODO Auto-generated method stub
		return 0;
	}
	public void setPlayLevel(int level) {
		// TODO Auto-generated method stub
		
	}
	public void enableSpeaker(boolean value) {
		// TODO Auto-generated method stub
		
	}
	public boolean isSpeakerEnabled() {
		// TODO Auto-generated method stub
		return false;
	}
	public synchronized void playDtmf(char number, int duration) {
		playDtmf(nativePtr,number, duration);
		
	}
	public synchronized void stopDtmf() {
		stopDtmf(nativePtr);
	}
	
	public synchronized void addFriend(LinphoneFriend lf) throws LinphoneCoreException {
		addFriend(nativePtr,((LinphoneFriendImpl)lf).nativePtr);
		
	}
	public synchronized void setPresenceInfo(int minute_away, String alternative_contact,
			OnlineStatus status) {
		setPresenceInfo(nativePtr,minute_away,alternative_contact,status.mValue);
		
	}
	public synchronized LinphoneChatRoom createChatRoom(String to) {
		return new LinphoneChatRoomImpl(createChatRoom(nativePtr,to));
	}
	public synchronized void setPreviewWindow(Object w) {
		setPreviewWindowId(nativePtr,w);
	}
	public synchronized void setVideoWindow(Object w) {
		setVideoWindowId(nativePtr, w);
	}
	public synchronized void setDeviceRotation(int rotation) {
		setDeviceRotation(nativePtr, rotation);
	}
	
	public synchronized void enableVideo(boolean vcap_enabled, boolean display_enabled) {
		enableVideo(nativePtr,vcap_enabled, display_enabled);
	}
	public synchronized boolean isVideoEnabled() {
		return isVideoEnabled(nativePtr);
	}
	public synchronized FirewallPolicy getFirewallPolicy() {
		return FirewallPolicy.fromInt(getFirewallPolicy(nativePtr));
	}
	public synchronized String getStunServer() {
		return getStunServer(nativePtr);
	}
	public synchronized void setFirewallPolicy(FirewallPolicy pol) {
		setFirewallPolicy(nativePtr,pol.value());
	}
	public synchronized void setStunServer(String stunServer) {
		setStunServer(nativePtr,stunServer);
	}
	
	public synchronized LinphoneCallParams createDefaultCallParameters() {
		return new LinphoneCallParamsImpl(createDefaultCallParams(nativePtr));
	}
	
	public synchronized LinphoneCall inviteAddressWithParams(LinphoneAddress to, LinphoneCallParams params) throws LinphoneCoreException {
		long ptrDestination = ((LinphoneAddressImpl)to).nativePtr;
		long ptrParams =((LinphoneCallParamsImpl)params).nativePtr;
		
		LinphoneCall call = (LinphoneCall)inviteAddressWithParams(nativePtr, ptrDestination, ptrParams);
		if (call!=null) {
			return call;
		} else {
			throw new LinphoneCoreException("Unable to invite with params " + to.asString());
		}
	}

	public synchronized int updateCall(LinphoneCall call, LinphoneCallParams params) {
		long ptrCall = ((LinphoneCallImpl) call).nativePtr;
		long ptrParams = params!=null ? ((LinphoneCallParamsImpl)params).nativePtr : 0;

		return updateCall(nativePtr, ptrCall, ptrParams);
	}
	public synchronized void setUploadBandwidth(int bw) {
		setUploadBandwidth(nativePtr, bw);
	}

	public synchronized void setDownloadBandwidth(int bw) {
		setDownloadBandwidth(nativePtr, bw);
	}

	public synchronized void setPreferredVideoSize(VideoSize vSize) {
		setPreferredVideoSize(nativePtr, vSize.width, vSize.height);
	}

	public synchronized VideoSize getPreferredVideoSize() {
		int[] nativeSize = getPreferredVideoSize(nativePtr);

		VideoSize vSize = new VideoSize();
		vSize.width = nativeSize[0];
		vSize.height = nativeSize[1];
		return vSize;
	}
	public synchronized void setRing(String path) {
		setRing(nativePtr, path);
	}
	public synchronized String getRing() {
		return getRing(nativePtr);
	}
	
	public synchronized void setRootCA(String path) {
		setRootCA(nativePtr, path);
	}
	
	public synchronized LinphoneProxyConfig[] getProxyConfigList() {
		long[] typesPtr = getProxyConfigList(nativePtr);
		if (typesPtr == null) return null;
		
		LinphoneProxyConfig[] proxies = new LinphoneProxyConfig[typesPtr.length];

		for (int i=0; i < proxies.length; i++) {
			proxies[i] = new LinphoneProxyConfigImpl(typesPtr[i]);
		}

		return proxies;
	}
	
	public synchronized PayloadType[] getVideoCodecs() {
		long[] typesPtr = listVideoPayloadTypes(nativePtr);
		if (typesPtr == null) return null;
		
		PayloadType[] codecs = new PayloadType[typesPtr.length];

		for (int i=0; i < codecs.length; i++) {
			codecs[i] = new PayloadTypeImpl(typesPtr[i]);
		}

		return codecs;
	}
	public synchronized PayloadType[] getAudioCodecs() {
		long[] typesPtr = listAudioPayloadTypes(nativePtr);
		if (typesPtr == null) return null;
		
		PayloadType[] codecs = new PayloadType[typesPtr.length];

		for (int i=0; i < codecs.length; i++) {
			codecs[i] = new PayloadTypeImpl(typesPtr[i]);
		}

		return codecs;
	}
	public synchronized boolean isNetworkReachable() {
		throw new RuntimeException("Not implemented");
	}
	public synchronized void enableKeepAlive(boolean enable) {
		enableKeepAlive(nativePtr,enable);
		
	}
	public synchronized boolean isKeepAliveEnabled() {
		return isKeepAliveEnabled(nativePtr);
	}
	public synchronized void startEchoCalibration(Object data) throws LinphoneCoreException {
		startEchoCalibration(nativePtr, data);
	}
	
	public synchronized Transports getSignalingTransportPorts() {
		Transports transports = new Transports();
		transports.udp = getSignalingTransportPort(nativePtr, 0);
		transports.tcp = getSignalingTransportPort(nativePtr, 1);
		transports.tls = getSignalingTransportPort(nativePtr, 3);
		// See C struct LCSipTransports in linphonecore.h
		// Code is the index in the structure
		return transports;
	}
	public synchronized void setSignalingTransportPorts(Transports transports) {
		setSignalingTransportPorts(nativePtr, transports.udp, transports.tcp, transports.tls);
	}

	public synchronized void enableIpv6(boolean enable) {
		enableIpv6(nativePtr,enable);
	}
	public synchronized void adjustSoftwareVolume(int i) {
		adjustSoftwareVolume(nativePtr, i);
	}

	public synchronized boolean pauseCall(LinphoneCall call) {
		return 0 == pauseCall(nativePtr, ((LinphoneCallImpl) call).nativePtr);
	}
	public synchronized boolean resumeCall(LinphoneCall call) {
		return 0 == resumeCall(nativePtr, ((LinphoneCallImpl) call).nativePtr);
	}
	public synchronized boolean pauseAllCalls() {
		return 0 == pauseAllCalls(nativePtr);
	}
	public synchronized void setDownloadPtime(int ptime) {
		setDownloadPtime(nativePtr,ptime);
		
	}
	public synchronized void setUploadPtime(int ptime) {
		setUploadPtime(nativePtr,ptime);
	}

	public synchronized void setZrtpSecretsCache(String file) {
		setZrtpSecretsCache(nativePtr,file);
	}
	public synchronized void enableEchoLimiter(boolean val) {
		enableEchoLimiter(nativePtr,val);
	}
	public void setVideoDevice(int id) {
		Log.i("Setting camera id :", id);
		if (setVideoDevice(nativePtr, id) != 0) {
			Log.e("Failed to set video device to id:", id);
		}
	}
	public int getVideoDevice() {
		return getVideoDevice(nativePtr);
	}


	private native void leaveConference(long nativePtr);	
	public synchronized void leaveConference() {
		leaveConference(nativePtr);
	}

	private native boolean enterConference(long nativePtr);	
	public synchronized boolean enterConference() {
		return enterConference(nativePtr);
	}

	private native boolean isInConference(long nativePtr);
	public synchronized boolean isInConference() {
		return isInConference(nativePtr);
	}

	private native void terminateConference(long nativePtr);
	public synchronized void terminateConference() {
		terminateConference(nativePtr);
	}
	private native int getConferenceSize(long nativePtr);
	public synchronized int getConferenceSize() {
		return getConferenceSize(nativePtr);
	}
	private native int getCallsNb(long nativePtr);
	public synchronized int getCallsNb() {
		return getCallsNb(nativePtr);
	}
	private native void terminateAllCalls(long nativePtr);
	public synchronized void terminateAllCalls() {
		terminateAllCalls(nativePtr);
	}
	private native Object getCall(long nativePtr, int position);
	public synchronized LinphoneCall[] getCalls() {
		int size = getCallsNb(nativePtr);
		LinphoneCall[] calls = new LinphoneCall[size];
		for (int i=0; i < size; i++) {
			calls[i]=((LinphoneCall)getCall(nativePtr, i));
		}
		return calls;
	}
	private native void addAllToConference(long nativePtr);
	public synchronized void addAllToConference() {
		addAllToConference(nativePtr);
		
	}
	private native void addToConference(long nativePtr, long nativePtrLcall);
	public synchronized void addToConference(LinphoneCall call) {
		addToConference(nativePtr, getCallPtr(call));
		
	}
	private native void removeFromConference(long nativePtr, long nativeCallPtr);
	public synchronized void removeFromConference(LinphoneCall call) {
		removeFromConference(nativePtr,getCallPtr(call));
	}

	private long getCallPtr(LinphoneCall call) {
		return ((LinphoneCallImpl)call).nativePtr;
	}
	
	private long getCallParamsPtr(LinphoneCallParams callParams) {
		return ((LinphoneCallParamsImpl)callParams).nativePtr;
	}

	private native int transferCall(long nativePtr, long callPtr, String referTo);
	public synchronized void transferCall(LinphoneCall call, String referTo) {
		transferCall(nativePtr, getCallPtr(call), referTo);
	}

	private native int transferCallToAnother(long nativePtr, long callPtr, long destPtr);
	public synchronized void transferCallToAnother(LinphoneCall call, LinphoneCall dest) {
		transferCallToAnother(nativePtr, getCallPtr(call), getCallPtr(dest));
	}

	private native Object findCallFromUri(long nativePtr, String uri);
	@Override
	public synchronized LinphoneCall findCallFromUri(String uri) {
		return (LinphoneCall) findCallFromUri(nativePtr, uri);
	}

	public synchronized MediaEncryption getMediaEncryption() {
		return MediaEncryption.fromInt(getMediaEncryption(nativePtr));
	}
	public synchronized boolean isMediaEncryptionMandatory() {
		return isMediaEncryptionMandatory(nativePtr);
	}
	public synchronized void setMediaEncryption(MediaEncryption menc) {
		setMediaEncryption(nativePtr, menc.mValue);	
	}
	public synchronized void setMediaEncryptionMandatory(boolean yesno) {
		setMediaEncryptionMandatory(nativePtr, yesno);
	}

	private native int getMaxCalls(long nativePtr);
	public synchronized int getMaxCalls() {
		return getMaxCalls(nativePtr);
	}
	@Override
	public boolean isMyself(String uri) {
		LinphoneProxyConfig lpc = getDefaultProxyConfig();
		if (lpc == null) return false;
		return uri.equals(lpc.getIdentity());
	}

	private native boolean soundResourcesLocked(long nativePtr);
	public synchronized boolean soundResourcesLocked() {
		return soundResourcesLocked(nativePtr);
	}

	private native void setMaxCalls(long nativePtr, int max);
	@Override
	public synchronized void setMaxCalls(int max) {
		setMaxCalls(nativePtr, max);
	}
	private native boolean isEchoLimiterEnabled(long nativePtr);
	@Override
	public synchronized boolean isEchoLimiterEnabled() {
		return isEchoLimiterEnabled(nativePtr);
	}
	private native boolean mediaEncryptionSupported(long nativePtr, int menc);
	@Override
	public synchronized boolean mediaEncryptionSupported(MediaEncryption menc) {
		return mediaEncryptionSupported(nativePtr,menc.mValue);
	}

	private native void setPlayFile(long nativePtr, String path);

	@Override
	public synchronized void setPlayFile(String path) {
		setPlayFile(nativePtr, path);
	}


	private native void tunnelAddServerAndMirror(long nativePtr, String host, int port, int mirror, int ms);
	@Override
	public synchronized void tunnelAddServerAndMirror(String host, int port, int mirror, int ms) {
		tunnelAddServerAndMirror(nativePtr, host, port, mirror, ms);
	}

	private native void tunnelAutoDetect(long nativePtr);
	@Override
	public synchronized void tunnelAutoDetect() {
		tunnelAutoDetect(nativePtr);
	}

	private native void tunnelCleanServers(long nativePtr);
	@Override
	public synchronized void tunnelCleanServers() {
		tunnelCleanServers(nativePtr);
	}

	private native void tunnelEnable(long nativePtr, boolean enable);
	@Override
	public synchronized void tunnelEnable(boolean enable) {
		tunnelEnable(nativePtr, enable);
	}

	@Override
	public native boolean isTunnelAvailable();
	
	private native void acceptCallWithParams(long nativePtr, long aCall,
			long params);
	@Override
	public synchronized void acceptCallWithParams(LinphoneCall aCall,
			LinphoneCallParams params) throws LinphoneCoreException {
		acceptCallWithParams(nativePtr, getCallPtr(aCall), getCallParamsPtr(params));
	}
	
	private native void acceptCallUpdate(long nativePtr, long aCall, long params);
	@Override
	public synchronized void acceptCallUpdate(LinphoneCall aCall, LinphoneCallParams params)
			throws LinphoneCoreException {
		acceptCallUpdate(nativePtr, getCallPtr(aCall), getCallParamsPtr(params));		
	}
	
	private native void deferCallUpdate(long nativePtr, long aCall);
	@Override
	public synchronized void deferCallUpdate(LinphoneCall aCall)
			throws LinphoneCoreException {
		deferCallUpdate(nativePtr, getCallPtr(aCall));
	}
	
	private native void setVideoPolicy(long nativePtr, boolean autoInitiate, boolean autoAccept);
	public synchronized void setVideoPolicy(boolean autoInitiate, boolean autoAccept) {
		setVideoPolicy(nativePtr, autoInitiate, autoAccept);
	}
	private native void setUserAgent(long nativePtr, String name, String version);
	@Override
	public void setUserAgent(String name, String version) {
		setUserAgent(nativePtr,name,version);
	}

	private native void setCpuCountNative(int count);
	public void setCpuCount(int count)
	{
		setCpuCountNative(count);
	}
	private native void tunnelSetHttpProxyNative(long nativePtr, String proxy_host, int port, String username, String password);
	@Override
	public void tunnelSetHttpProxy(String proxy_host, int port,
			String username, String password) {
		tunnelSetHttpProxyNative(nativePtr,proxy_host, port, username, password);
	}

	private native void removeCallLog(long nativePtr, LinphoneCallLog log);
	public void removeCallLog(LinphoneCallLog log) {
		removeCallLog(nativePtr, log);
	}

	private native int getMissedCallsCount(long nativePtr);
	public int getMissedCallsCount() {
		return getMissedCallsCount(nativePtr);
	}

	private native void resetMissedCallsCount(long nativePtr);
	public void resetMissedCallsCount() {
		resetMissedCallsCount(nativePtr);
	}
	
	private native void refreshRegisters(long nativePtr);
	public void refreshRegisters() {
		refreshRegisters(nativePtr);
	}
	@Override
	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}
}
