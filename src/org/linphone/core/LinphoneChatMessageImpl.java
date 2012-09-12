package org.linphone.core;

public class LinphoneChatMessageImpl implements LinphoneChatMessage {
	protected final long nativePtr;
	private native void setUserData(long ptr);
	private native String getMessage(long ptr);
	private native LinphoneAddress getPeerAddress(long ptr);
	
	protected LinphoneChatMessageImpl(long aNativePtr)  {
		nativePtr = aNativePtr;
		setUserData();
	}
	
	public long getNativePtr() {
		return nativePtr;
	}
	
	@Override
	public Object getUserData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setUserData() {
		setUserData(nativePtr);
	}

	@Override
	public String getMessage() {
		return getMessage(nativePtr);
	}
	
	@Override
	public LinphoneAddress getPeerAddress() {
		return getPeerAddress(nativePtr);
	}
}
