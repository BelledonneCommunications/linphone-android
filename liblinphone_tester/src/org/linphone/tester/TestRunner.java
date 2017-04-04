package org.linphone.tester;

import junit.framework.TestSuite;
import android.os.Bundle;

import com.zutubi.android.junitreport.JUnitReportTestRunner;

import java.lang.Override;


public class TestRunner extends JUnitReportTestRunner {
	String mSuite = null;
	String mTest = null;

	@Override
	public void onCreate(Bundle arguments) {
		mSuite = arguments.getString("suite");
		mTest = arguments.getString("test");
		Tester.keepAccounts(true);

		super.onCreate(arguments);
	}

	@Override
	public void onDestroy() {
		Tester.clearAccounts();
		super.onDestroy();
	}

	@Override
	public TestSuite getAllTests () {
		TestSuite suite = new TestSuite("Tests");
		suite.addTest(new WrapperTester());
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
