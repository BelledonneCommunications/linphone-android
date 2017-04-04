package org.linphone.tester;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class SuitesActivity extends Activity {
	String mSuite;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_suites);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mSuite = extras.getString("suite");
		    if(mSuite != null) {
		    	this.setTitle(this.getResources().getString(R.string.app_name) + " | " + mSuite);
				TesterList suitesTest = new TesterList();
				suitesTest.run(new String[]{"tester", "--list-tests", mSuite});
				LinearLayout layout = ((LinearLayout)findViewById(R.id.tests_list));
				layout.removeAllViews();
				addButton(layout, "All", null);
				for(String str: suitesTest.getList()) {
					str = str.trim();
					addButton(layout, str, str);
				}
		    }
		}

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
		    	if(mSuite != null) {
			    	if(data == null) {
			    		Intent intent = new Intent(getBaseContext(), LogsActivity.class);
			    		intent.putExtra("args", new String[]{"--suite", mSuite});
			    		startActivity(intent);
			    	} else {
			    		Intent intent = new Intent(getBaseContext(), LogsActivity.class);
			    		intent.putExtra("args", new String[]{"--suite", mSuite, "--test", data});
			    		startActivity(intent);
			    	}
		    	}
		    }
		});
		layout.addView(button);
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_suites, menu);
		return true;
	}

}
