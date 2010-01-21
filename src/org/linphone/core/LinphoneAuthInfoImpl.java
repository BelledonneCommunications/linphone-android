package org.linphone.core;

class LinphoneAuthInfoImpl implements LinphoneAuthInfo {
	protected final long nativePtr;
	private native long newLinphoneAuthInfo(String username, String userid, String passwd, String ha1,String realm);
	private native void  delete(long ptr);
	protected LinphoneAuthInfoImpl(String username,String password)  {
		nativePtr = newLinphoneAuthInfo(username,null,password,null,null);
	}
	protected void finalize() throws Throwable {
		delete(nativePtr);
	}
}
