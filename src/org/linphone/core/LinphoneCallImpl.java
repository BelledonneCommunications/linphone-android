/*
LinphoneCallImpl.java
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


class LinphoneCallImpl implements LinphoneCall {

	protected final long nativePtr;
	boolean ownPtr = false;
	native private void ref(long ownPtr);
	native private void unref(long ownPtr);
	native private long  getCallLog(long nativePtr);
	private native boolean isIncoming(long nativePtr);
	native private long getRemoteAddress(long nativePtr);
	native private int getState(long nativePtr);
	private native long getCurrentParamsCopy(long nativePtr);
	private native void enableCamera(long nativePtr, boolean enabled);
	private native void enableEchoCancellation(long nativePtr,boolean enable);
	private native boolean isEchoCancellationEnabled(long nativePtr) ;
	private native void enableEchoLimiter(long nativePtr,boolean enable);
	private native boolean isEchoLimiterEnabled(long nativePtr) ;

	protected LinphoneCallImpl(long aNativePtr)  {
		nativePtr = aNativePtr;
		ref(nativePtr);
	}
	protected void finalize() throws Throwable {
		unref(nativePtr);
	}
	public LinphoneCallLog getCallLog() {
		long lNativePtr = getCallLog(nativePtr);
		if (lNativePtr!=0) {
			return new LinphoneCallLogImpl(lNativePtr); 
		} else {
			return null;
		}
	}
	public CallDirection getDirection() {
		return isIncoming(nativePtr)?CallDirection.Incoming:CallDirection.Outgoing;
	}
	public LinphoneAddress getRemoteAddress() {
		long lNativePtr = getRemoteAddress(nativePtr);
		if (lNativePtr!=0) {
			return new LinphoneAddressImpl(lNativePtr); 
		} else {
			return null;
		}
	}
	public State getState() {
		return LinphoneCall.State.fromInt(getState(nativePtr));
	}
	public LinphoneCallParams getCurrentParamsCopy() {
		return new LinphoneCallParamsImpl(getCurrentParamsCopy(nativePtr));
	}

	public void enableCamera(boolean enabled) {
		enableCamera(nativePtr, enabled);
	}
	public boolean equals(Object call) {
		return nativePtr == ((LinphoneCallImpl)call).nativePtr;
	}
	public void enableEchoCancellation(boolean enable) {
		enableEchoCancellation(nativePtr,enable);
		
	}
	public boolean isEchoCancellationEnabled() {
		return isEchoCancellationEnabled(nativePtr);
	}
	public void enableEchoLimiter(boolean enable) {
		enableEchoLimiter(nativePtr,enable);
	}
	public boolean isEchoLimiterEnabled() {
		return isEchoLimiterEnabled(nativePtr);
	}
}
