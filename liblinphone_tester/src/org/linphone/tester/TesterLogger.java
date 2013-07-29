package org.linphone.tester;

public class TesterLogger extends Tester {
	private LogsActivity mLogsActivity;
	TesterLogger(LogsActivity logsActivity) {
		mLogsActivity = logsActivity;
	}
	public void printLog(final int level, final String message) {
		super.printLog(level, message);
		mLogsActivity.runOnUiThread(new Runnable() {
			public void run() {
				mLogsActivity.addLog(level, message);
			}
		});
	}
}
