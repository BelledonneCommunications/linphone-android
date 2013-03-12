package org.linphone.tester;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;
import android.test.AndroidTestCase;

public class TestUnit extends AndroidTestCase {
	private String mSuite;
	private String mTest;

	public TestUnit(String suite, String test) {
		mSuite = suite;
		mTest = test;
		setName(suite + "/" + test);
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Override
	protected void runTest() {
		String path = getContext().getFilesDir().getAbsolutePath();
		Tester tester = new Tester();
		List<String> list = new LinkedList<String>(Arrays.asList(new String[]{"tester", "--verbose", "--config", path, "--suite", mSuite, "--test", mTest}));
		String[] array = list.toArray(new String[list.size()]);
		Assert.assertTrue(tester.run(array) == 0);
	}
}