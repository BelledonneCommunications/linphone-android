package org.linphone.tester;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Override;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		try {
			TestUnit.copyAssetsFromPackage(getApplicationContext());
		} catch (IOException e) {
			Log.e("liblinphone_tester", "Cannot install rc files",e);
		}
		Tester.keepAccounts(true);
		TesterList suitesTest = new TesterList();
		suitesTest.run(new String[]{"tester", "--list-suites"});
		LinearLayout layout = ((LinearLayout)findViewById(R.id.suites_list));
		layout.removeAllViews();
		addButton(layout, "All", null);
		for(String str: suitesTest.getList()) {
			str = str.trim();
			addButton(layout, str, str);
		}
	}

	@Override
	protected void onDestroy(){
		Tester.clearAccounts();
		super.onDestroy();
	}

	private void addButton(LinearLayout layout, String text, String data) {
		Button button = new Button(this);
		button.setText(text);
		button.setTag(data);
		button.setGravity(Gravity.CENTER);
		button.setOnClickListener(new Button.OnClickListener() {
		    public void onClick(View v) {
		 		Button button = (Button) v;
		 		String data = (String)button.getTag();
		 		if(data == null) {
		 			Intent intent = new Intent(getBaseContext(), LogsActivity.class);
		 			intent.putExtra("args", new String[]{});
		 			startActivity(intent);
		 		} else {
		 			Intent intent = new Intent(getBaseContext(), SuitesActivity.class);
		 			intent.putExtra("suite", data);
		 			startActivity(intent);
		 		}
		    }
		});
		layout.addView(button);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
