package org.linphone.tester;

import java.util.List;

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
		List<String> cpuabis=Version.getCpuAbis();
		String ffmpegAbi;
		boolean libLoaded=false;
		Throwable firstException=null;
		for (String abi : cpuabis){
			Log.i("LinphoneCoreFactoryImpl","Trying to load liblinphone for " + abi);
			loadOptionalLibrary("ffmpeg-linphone-" + abi);
			//Main library
			try {
				System.loadLibrary("bctoolbox-" + abi);
				System.loadLibrary("bctoolbox-tester-" + abi);
				System.loadLibrary("ortp-" + abi);
				System.loadLibrary("mediastreamer_base-" + abi);
				System.loadLibrary("mediastreamer_voip-" + abi);
				System.loadLibrary("linphone-" + abi);
				System.loadLibrary("linphonetester-" + abi);
				
				Log.i("LinphoneCoreFactoryImpl","Loading done with " + abi);
				libLoaded=true;
				break;
			}catch(Throwable e) {
				if (firstException == null) firstException=e;
			}
		}
		
		if (!libLoaded){
			throw new RuntimeException(firstException);
			
		}else{
			Version.dumpCapabilities();
		}
	}
	
	public native int run(String args[]);
	public static native void keepAccounts(boolean keep);
	public static native void clearAccounts();
	
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
