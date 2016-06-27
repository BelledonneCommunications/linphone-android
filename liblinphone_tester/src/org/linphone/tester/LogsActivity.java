package org.linphone.tester;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;

public class LogsActivity extends Activity {
	private String mLogs = "";
	private LogsThread mLogsThread;
	private class LogsThread extends Thread {
		LogsActivity mLogsActivity;
		String mArgs[];
		TesterLogger tester;
		public LogsThread(LogsActivity logsActivity, String[] args) {
			mLogsActivity = logsActivity;
			mArgs = args;
		}

		@Override
		public void run() {
			String res_path = mLogsActivity.getFilesDir().getAbsolutePath()+"/config_files";
			String write_path = mLogsActivity.getCacheDir().getPath();
			tester = new TesterLogger(mLogsActivity);
			List<String> list = new LinkedList<String>(Arrays.asList(new String[]{"tester", "--verbose", "--resource-dir", res_path, "--writable-dir", write_path}));
			list.addAll(Arrays.asList(mArgs));
			String[] array = list.toArray(new String[list.size()]);
			tester.run(array);
			Tester.clearAccounts();
			mLogsActivity.runOnUiThread(new Runnable() {
    			public void run() {
    				mLogsActivity.done();
    			}
    		});
		}
	}

	private static String join(String [] array, String separator) {
		String ret = "";
		for(int i = 0; i < array.length; ++i) {
			if(i != 0) {
				ret += separator;
			}
			ret += array[i];
		}
		return ret;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_logs);
		((TextView)findViewById(R.id.textView1)).setText(mLogs);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
		    String[] values = extras.getStringArray("args");
		    if(values != null) {
		    	if(mLogsThread == null || !mLogsThread.isAlive()) {
		    		this.setTitle("Test Logs (" + join(values, " ") + ")");
		    		mLogs = "";
		    		((TextView)findViewById(R.id.textView1)).setText(mLogs);
		    		mLogsThread = new LogsThread(this, values);
		    		mLogsThread.start();
		    	}
		    }
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_logs, menu);
		return true;
	}

	public void addLog(int level, String message) {
		mLogs += message;
		((TextView)findViewById(R.id.textView1)).append(message);
	}
	@Override
	public void onBackPressed() {
		if(mLogsThread == null || !mLogsThread.isAlive()) {
			finish();
		}
	}

	public void done() {
	}
}
