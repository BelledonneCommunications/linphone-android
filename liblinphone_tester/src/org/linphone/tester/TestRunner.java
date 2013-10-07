package org.linphone.tester;

import java.io.IOException;

import junit.framework.TestSuite;
import android.os.Bundle;
import android.test.InstrumentationTestRunner;


public class TestRunner extends InstrumentationTestRunner {
	String mSuite = null;
	String mTest = null;
	
	@Override
	public void onCreate(Bundle arguments) {
		mSuite = arguments.getString("suite");
		mTest = arguments.getString("test");
		
		super.onCreate(arguments);
	}
	
	@Override
	public TestSuite getAllTests () {
		TestSuite suite = new TestSuite("Tests");
		addSuites(suite, mSuite, mTest);
		return suite;
	}
	
	public static void addSuites(TestSuite suite, String suiteCheck, String testCheck) {
		TesterList testerList = new TesterList();
		testerList.run(new String[]{"tester", "--list-suites"});
		for(String str: testerList.getList()) {
			str = str.trim();
			if(suiteCheck == null || suiteCheck.equals(str)) {
				addSuite(suite, str, testCheck);
			}
		}
	}
	
	public static void addSuite(TestSuite suite, String suiteStr, String testCheck) {
		TesterList testerList = new TesterList();
		testerList.run(new String[]{"tester", "--list-tests", suiteStr});
		for(String str: testerList.getList()) {
			str = str.trim();
			if(testCheck == null || testCheck.equals(str)) {
				suite.addTest(new TestUnit(suiteStr, str));
			}
		}
	}
}
