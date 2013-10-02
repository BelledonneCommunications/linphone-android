package org.linphone.tester;

import org.linphone.core.LinphoneCoreFactory;

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
	
	public static boolean isArmv7()
	{
		return System.getProperty("os.arch").contains("armv7");
	}
	
	static	{
		LinphoneCoreFactory.instance();

		System.loadLibrary("cunit");
		
		//Main library
		if (!hasNeonInCpuFeatures()) {
			try {
				if (!isArmv7() && !Version.isX86()) {
					System.loadLibrary("linphone_testerarmv5");
				} else {
					System.loadLibrary("linphone_testernoneon");
				}
			} catch (UnsatisfiedLinkError ule) {
				Log.w("linphone", "Failed to load no-neon liblinphone_tester, loading neon liblinphone_tester");
				System.loadLibrary("linphone_tester");
			}
		} else {
			System.loadLibrary("linphone_tester");
		}
		
		Version.dumpCapabilities();
	}
	
	public native int run(String args[]);
	
	public void printLog(final int level, final String message) {
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
