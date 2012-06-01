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
import java.io.InputStream;

import org.linphone.mediastream.Version;

import android.util.Log;

public class LinphoneCoreFactoryImpl extends LinphoneCoreFactory {

	private static boolean loadOptionalLibrary(String s) {
		try {
			System.loadLibrary(s);
			return true;
		} catch (Throwable e) {
			Log.w("Unable to load optional library lib", s);
		}
		return false;
	}

	static {
		// FFMPEG (audio/video)
		loadOptionalLibrary("avutil");
		loadOptionalLibrary("swscale");
		loadOptionalLibrary("avcore");
		
		if (!hasNeonInCpuFeatures()) {
			boolean noNeonLibrariesLoaded = loadOptionalLibrary("avcodecnoneon");
			if (!noNeonLibrariesLoaded) {
				loadOptionalLibrary("avcodec");
			}
		} else {
			loadOptionalLibrary("avcodec");
		}
 
		// OPENSSL (cryptography)
		// lin prefix avoids collision with libs in /system/lib
		loadOptionalLibrary("lincrypto");
		loadOptionalLibrary("linssl");

		// Secure RTP and key negotiation
		loadOptionalLibrary("srtp");
		loadOptionalLibrary("zrtpcpp"); // GPLv3+

		// Tunnel
		loadOptionalLibrary("tunnelclient");
		
		// g729 A implementation
		loadOptionalLibrary("bcg729");

		//Main library
		if (!hasNeonInCpuFeatures()) {
			try {
				if (!isArmv7()) {
					System.loadLibrary("linphonearmv5"); 
				} else {
					System.loadLibrary("linphonenoneon"); 
				}
				Log.w("linphone", "No-neon liblinphone loaded");
			} catch (UnsatisfiedLinkError ule) {
				Log.w("linphone", "Failed to load no-neon liblinphone, loading neon liblinphone");
				System.loadLibrary("linphone"); 
			}
		} else {
			System.loadLibrary("linphone"); 
		}

		Version.dumpCapabilities();
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

	public static boolean hasNeonInCpuFeatures()
	{
		ProcessBuilder cmd;
		boolean result = false;
		
		try {
			String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
			cmd = new ProcessBuilder(args);
	
		   Process process = cmd.start();
		   InputStream in = process.getInputStream();
		   byte[] re = new byte[1024];
		   while(in.read(re) != -1){
			   String line = new String(re);
			   if (line.contains("Features")) {
				   result = line.contains("neon");
				   break;
			   }
		   }
		   in.close();
		} catch(IOException ex){
			ex.printStackTrace();
		}
		return result;
	}
	
	public static boolean isArmv7()
	{
		return System.getProperty("os.arch").contains("armv7");
	}
}
