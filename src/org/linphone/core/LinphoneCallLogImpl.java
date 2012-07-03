/*
LinPhoneCallLogImpl.java
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


class LinphoneCallLogImpl implements LinphoneCallLog {

	protected final long nativePtr;
	
	private native long getFrom(long nativePtr);
	private native long getTo(long nativePtr);
	private native boolean isIncoming(long nativePtr);
	LinphoneCallLogImpl(long aNativePtr)  {
		nativePtr = aNativePtr;
	}
	
	
	public CallDirection getDirection() {
		return isIncoming(nativePtr)?CallDirection.Incoming:CallDirection.Outgoing;
	}

	public LinphoneAddress getFrom() {
		return new LinphoneAddressImpl(getFrom(nativePtr));
	}

	public LinphoneAddress getTo() {
		return new LinphoneAddressImpl(getTo(nativePtr));
	}
	public CallStatus getStatus() {
		throw new RuntimeException("not implemented yet");
	}
	@Override
	public String getStartDate() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public int getCallDuration() {
		// TODO Auto-generated method stub
		return 0;
	}

}
