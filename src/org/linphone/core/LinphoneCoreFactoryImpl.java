/*
LinphoneCoreFactoryImpl.java
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

import org.linphone.LinphoneManager;

import android.util.Log;

public class LinphoneCoreFactoryImpl extends LinphoneCoreFactory {

	private static void loadOptionalLibrary(String s) {
		try {
			System.loadLibrary(s);
		} catch (Throwable e) {
			Log.w(LinphoneManager.TAG, "Unable to load optional library lib" + s);
		}
	}

	static {
		loadOptionalLibrary("avutil");
		loadOptionalLibrary("swscale");
		loadOptionalLibrary("avcore");
		loadOptionalLibrary("avcodec");
		System.loadLibrary("linphone");
	}
	@Override
	public LinphoneAuthInfo createAuthInfo(String username, String password,
			String realm) {
		return new LinphoneAuthInfoImpl(username,password,realm);
	}

	@Override
	public LinphoneAddress createLinphoneAddress(String username,
			String domain, String displayName) {
		return new LinphoneAddressImpl(username,domain,displayName);
	}

	@Override
	public LinphoneAddress createLinphoneAddress(String identity) {
		return new LinphoneAddressImpl(identity);
	}

	@Override
	public LinphoneCore createLinphoneCore(LinphoneCoreListener listener,
			String userConfig, String factoryConfig, Object userdata)
			throws LinphoneCoreException {
		try {
			return new LinphoneCoreImpl(listener,new File(userConfig),new File(factoryConfig),userdata);
		} catch (IOException e) {
			throw new LinphoneCoreException("Cannot create LinphoneCore",e);
		}
	}

	@Override
	public LinphoneCore createLinphoneCore(LinphoneCoreListener listener) throws LinphoneCoreException {
		try {
			return new LinphoneCoreImpl(listener);
		} catch (IOException e) {
			throw new LinphoneCoreException("Cannot create LinphoneCore",e);
		}
	}

	@Override
	public LinphoneProxyConfig createProxyConfig(String identity, String proxy,
			String route, boolean enableRegister) throws LinphoneCoreException {
		return new LinphoneProxyConfigImpl(identity,proxy,route,enableRegister);
	}

	@Override
	public native void setDebugMode(boolean enable);

	@Override
	public void setLogHandler(LinphoneLogHandler handler) {
		//not implemented on Android
		
	}

	@Override
	public LinphoneFriend createLinphoneFriend(String friendUri) {
		return new LinphoneFriendImpl(friendUri);
	}

	@Override
	public LinphoneFriend createLinphoneFriend() {
		return createLinphoneFriend(null);
	}
}
