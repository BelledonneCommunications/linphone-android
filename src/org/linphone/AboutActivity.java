/*
AboutActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.linphone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.linphone.core.Log;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;



public class AboutActivity extends Activity implements OnClickListener {

	private Handler mHandler = new Handler();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);

		TextView aboutText = (TextView) findViewById(R.id.AboutText);
		try {
			aboutText.setText(String.format(getString(R.string.about_text), getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
		} catch (NameNotFoundException e) {
			Log.e(e, "cannot get version name");
		}

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.getBoolean(getString(R.string.pref_debug_key), false)) {
			View issue = findViewById(R.id.about_report_issue);
			issue.setOnClickListener(this);
			issue.setVisibility(View.VISIBLE);
		}
	}

	private Thread thread;
	@Override
	public void onClick(View v) {
		if (thread != null) return;
		Toast.makeText(this, getString(R.string.about_reading_logs), Toast.LENGTH_LONG).show();
		thread = new ReadLogThread();
		thread.start();
	}

	private File writeLogs(String logs, File directory) {
		File tempFile = null;
		try {
			tempFile = File.createTempFile("bugreport", ".txt", directory);
			tempFile.deleteOnExit();
			FileWriter writer = new FileWriter(tempFile);
			writer.append(logs);
			return tempFile;
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.about_error_generating_bugreport_attachement), Toast.LENGTH_LONG).show();
			Log.e(e, "couldn't write to temporary file");
			return null;
		}
	}

	private void onLogsRead(String logs) {
		if (logs == null) {
			Toast.makeText(this, getString(R.string.about_logs_not_found), Toast.LENGTH_SHORT).show();
		} else {
			File tempFile = writeLogs(logs, null);
			if (tempFile == null) {
				// If writing to temporary file to default location failed
				// Write one to our storage place
				tempFile = writeLogs(logs, getFilesDir());
			}

			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setType("plain/text");
			intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.about_bugreport_email)});
			intent.putExtra(Intent.EXTRA_SUBJECT,"Bug report " + getString(R.string.app_name) + "-android");
			intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.about_bugreport_email_text));
			intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile));
			intent = Intent.createChooser(intent,getString(R.string.about_mailer_chooser_text));
			startActivityForResult(intent, 0);
		}
	}


	private class ReadLogThread extends Thread {
		@Override
		public void run() {
			final String logs = readLogs();
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					onLogsRead(logs);
					thread=null;
				}
			});
			super.run();
		}
	}

	private String readLogs() {
		StringBuilder sb1 = null;
		StringBuilder sb2 = null;
		BufferedReader br = null;
		Process p = null;

		try {
			p = Runtime.getRuntime().exec(new String[] {"logcat", "-d"});
			br = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);

			String line;
			while ((line = br.readLine()) != null) {
				if (line.contains(LinphoneService.START_LINPHONE_LOGS)) {
					if (sb1 != null) {
						sb2 = sb1;
					}
					sb1 = new StringBuilder();
				}
				if (sb1 != null) {
					sb1.append(line).append('\n');
				}
			}

			if (sb1 == null) return null;
			if (sb2 != null) {
				sb1.append(sb2);
			}
			return sb1.toString();
		} catch (IOException e) {
			Log.e(e, "Error while reading logs");
			return null;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {}
			}
			if (p != null) {
				p.destroy();
			}
		}
	}
	
}
