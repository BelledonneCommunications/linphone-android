package org.linphone.tester;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;
import android.content.Context;
import android.test.AndroidTestCase;

public class TestUnit extends AndroidTestCase {
	private String mSuite;
	private String mTest;
	static Boolean isAssetCopied=false;
	
	public TestUnit(String suite, String test) {
		mSuite = suite;
		mTest = test;
		setName(suite + "/" + test);
	}
	private void copyAssetsFromPackage() throws IOException {
		Context ctx= getContext();
		for (String file :ctx.getAssets().list("rc_files")) {
			FileOutputStream lOutputStream = ctx.openFileOutput (new File(file).getName(), 0); 
			InputStream lInputStream = ctx.getAssets().open("rc_files/"+file);
			int readByte;
			byte[] buff = new byte[8048];
			while (( readByte = lInputStream.read(buff)) != -1) {
				lOutputStream.write(buff,0, readByte);
			}
			lOutputStream.flush();
			lOutputStream.close();
			lInputStream.close();
		}
	}
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		if (isAssetCopied ==false) {
			copyAssetsFromPackage();
			isAssetCopied=true;
		}
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Override
	protected void runTest() {
		String path = getContext().getFilesDir().getPath();
		Tester tester = new Tester();
		List<String> list = new LinkedList<String>(Arrays.asList(new String[]{"tester", "--verbose", "--config", path, "--suite", mSuite, "--test", mTest}));
		String[] array = list.toArray(new String[list.size()]);
		Assert.assertTrue(tester.run(array) == 0);
	}
}