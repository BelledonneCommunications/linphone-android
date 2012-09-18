package org.linphone.core;

public class LinphoneChatMessageImpl implements LinphoneChatMessage {
	protected final long nativePtr;
	private native void setUserData(long ptr);
	private native String getMessage(long ptr);
	private native long getPeerAddress(long ptr);
	private native String getExternalBodyUrl(long ptr);
	private native void setExternalBodyUrl(long ptr, String url);
	private native long getFrom(long ptr);
	
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
		return new LinphoneAddressImpl(getPeerAddress(nativePtr));
	}
	
	@Override
	public String getExternalBodyUrl() {
		return getExternalBodyUrl(nativePtr);
	}
	
	@Override
	public void setExternalBodyUrl(String url) {
		setExternalBodyUrl(nativePtr, url);
	}
	
	@Override
	public LinphoneAddress getFrom() {
		return new LinphoneAddressImpl(getFrom(nativePtr));
	}
}
