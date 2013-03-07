package org.linphone.tester;

import org.linphone.mediastream.CpuUtils;
import org.linphone.mediastream.Version;

import android.util.Log;

public class Tester {
	public static String TAG = "liblinphone-tester";
	private static boolean loadOptionalLibrary(String s) {
		try {
			System.loadLibrary(s);
			return true;
		} catch (Throwable e) {
			Log.w("Unable to load optional library lib", s);
		}
		return false;
	}
	
	public static boolean hasNeonInCpuFeatures()
	{
		CpuUtils cpu = new CpuUtils();
		return cpu.isCpuNeon();
	}
	
	public static boolean isArmv7()
	{
		return System.getProperty("os.arch").contains("armv7");
	}
	
	static	{
		// FFMPEG (audio/video)
		loadOptionalLibrary("avutil");
		loadOptionalLibrary("swscale");
		loadOptionalLibrary("avcore");

		System.loadLibrary("neon");
		
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

		System.loadLibrary("cunit");
		
		//Main library
		if (!hasNeonInCpuFeatures()) {
			try {
				if (!isArmv7() && !Version.isX86()) {
					System.loadLibrary("linphonearmv5"); 
					System.loadLibrary("linphone_testerarmv5");
				} else {
					System.loadLibrary("linphonenoneon");
					System.loadLibrary("linphone_testernoneon");
				}
				Log.w("linphone", "No-neon liblinphone loaded");
			} catch (UnsatisfiedLinkError ule) {
				Log.w("linphone", "Failed to load no-neon liblinphone, loading neon liblinphone");
				System.loadLibrary("linphone"); 
				System.loadLibrary("linphone_tester");
			}
		} else {
			System.loadLibrary("linphone"); 
			System.loadLibrary("linphone_tester");
		}
		
		Version.dumpCapabilities();
	}
	
	public native int run(String args[]);
	
	public void printLog(final int level, final String message) {
		MainActivity.instance.runOnUiThread(new Runnable() {
			public void run() {
				MainActivity.instance.addLog(level, message);
			}
		});
		switch(level) {
		case 0:
			android.util.Log.i(TAG, message);
		break;
		case 1:
			android.util.Log.e(TAG, message);
		break;
		}
	}
}
