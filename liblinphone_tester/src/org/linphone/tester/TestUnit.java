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

	public TestUnit(String name) {
		String[] tab = name.split("/");
		mSuite = tab[0];
		mTest = tab[1];
		setName(name);
	}

	static public void copyAssetsFromPackage(Context ctx) throws IOException {
		copyAssetsFromPackage(ctx,"config_files");
	}


	public static void copyAssetsFromPackage(Context ctx,String fromPath) throws IOException {
		new File(ctx.getFilesDir().getPath()+"/"+fromPath).mkdir();

		for (String f :ctx.getAssets().list(fromPath)) {
			String current_name=fromPath+"/"+f;
			InputStream lInputStream;
			try {
				 lInputStream = ctx.getAssets().open(current_name);
			} catch (IOException e) {
				//probably a dir
				new File(ctx.getFilesDir().getPath()+"/"+current_name).mkdir();
				copyAssetsFromPackage(ctx,current_name);
				continue;
			}
			FileOutputStream lOutputStream =  new FileOutputStream(new File(ctx.getFilesDir().getPath()+"/"+current_name));//ctx.openFileOutput (fromPath+"/"+f, 0);


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
			copyAssetsFromPackage(getContext());
			isAssetCopied=true;
		}
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	@Override
	protected void runTest() {
		String res_path = getContext().getFilesDir().getPath()+"/config_files";
		String write_path = getContext().getCacheDir().getPath();
		Tester tester = new Tester();

		List<String> list;
		if (mTest != null && !mTest.isEmpty()) {
			list = new LinkedList<String>(Arrays.asList(new String[]{"tester", "--verbose", "--resource-dir", res_path, "--writable-dir", write_path, "--suite", mSuite, "--test", mTest}));
		} else {
			list = new LinkedList<String>(Arrays.asList(new String[]{"tester", "--verbose", "--resource-dir", res_path, "--writable-dir", write_path, "--suite", mSuite}));
		}
		String[] array = list.toArray(new String[list.size()]);
		Assert.assertTrue(tester.run(array) == 0);
	}
}
