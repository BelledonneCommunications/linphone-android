package org.linphone.tester;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import org.linphone.tester.Tester;

public class MainActivity extends Activity {
	static public MainActivity instance = null;
	static public Tester tester = new Tester();
	String mLogs = "";
	MainThread mThread;
	
	private class MainThread extends Thread {
		MainActivity mActivity;
		public MainThread(MainActivity activity) {
			mActivity = activity;
		}
		@Override
		public void run() {
			String path = mActivity.getFilesDir().getAbsolutePath();
			tester.run(new String[]{"tester", "--verbose", "--config", path});
	 		mActivity.runOnUiThread(new Runnable() {
    			public void run() {
    				mActivity.done();
    			}
    		});
		}
	}

	private void copyFromPackage(int ressourceId,String target) throws IOException{
		FileOutputStream lOutputStream = openFileOutput (target, 0); 
		InputStream lInputStream = getResources().openRawResource(ressourceId);
		int readByte;
		byte[] buff = new byte[8048];
		while (( readByte = lInputStream.read(buff)) != -1) {
			lOutputStream.write(buff,0, readByte);
		}
		lOutputStream.flush();
		lOutputStream.close();
		lInputStream.close();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			copyFromPackage(R.raw.laure_rc, new File("laure_rc").getName());
			copyFromPackage(R.raw.marie_rc, new File("marie_rc").getName());
			copyFromPackage(R.raw.multi_account_lrc, new File("multi_account_lrc").getName());
			copyFromPackage(R.raw.pauline_rc, new File("pauline_rc").getName());
		} catch (IOException e) {
			e.printStackTrace();
		}

		super.onCreate(savedInstanceState);
		instance = this;
		setContentView(R.layout.activity_main);
		((TextView)findViewById(R.id.textView1)).setText(mLogs);
		if(mThread == null || !mThread.isAlive()) {
			findViewById(R.id.button1).setEnabled(true);
		} else {
			findViewById(R.id.button1).setEnabled(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	public void onBtnClicked(View v) {
		mLogs = "";
		((TextView)findViewById(R.id.textView1)).setText(mLogs);
		findViewById(R.id.button1).setEnabled(false);
		mThread = new MainThread(this);
		mThread.start();
	}
	
	public void addLog(int level, String message) {
		mLogs += message;
		((TextView)findViewById(R.id.textView1)).append(message);
	}
	
	public void done() {
		findViewById(R.id.button1).setEnabled(true);
	}
}
