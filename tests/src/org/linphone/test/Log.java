package org.linphone.test;

public class Log {
	public static void testSuccess(String testName) {
		android.util.Log.wtf("TEST SUCCESS", testName);
	}
	
	public static void testFailure(String testName) {
		android.util.Log.wtf("TEST FAILURE", testName);
	}
}
