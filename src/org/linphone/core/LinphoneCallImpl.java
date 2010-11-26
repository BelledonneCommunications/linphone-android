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
	private native long getCurrentParams(long nativePtr);

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
	public LinphoneCallParams getCurrentParamsReadOnly() {
		return new LinphoneCallParamsImpl(getCurrentParams(nativePtr));
	}
	public LinphoneCallParams getCurrentParamsReadWrite() {
		return getCurrentParamsReadOnly().copy();
	}
}
