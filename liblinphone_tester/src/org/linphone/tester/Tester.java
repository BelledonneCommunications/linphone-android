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
		String eabi = "armeabi";
		if (Version.isX86()) {
			eabi = "x86";
		} else if (Version.isArmv7()) {
			eabi = "armeabi-v7a";
		}
		try {
			System.loadLibrary("linphone_tester-"+eabi);

		} catch (UnsatisfiedLinkError ule) {
			Log.w("linphone", "Failed to load liblinphone_tester-"+eabi);
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
