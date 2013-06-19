package org.linphone.tester;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

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
		super.onCreate(savedInstanceState);
		try {
			copyFromPackage(R.raw.laure_rc, new File("laure_rc").getName());
			copyFromPackage(R.raw.marie_rc, new File("marie_rc").getName());
			copyFromPackage(R.raw.marie_early_rc, new File("marie_early_rc").getName());
			copyFromPackage(R.raw.multi_account_lrc, new File("multi_account_lrc").getName());
			copyFromPackage(R.raw.pauline_rc, new File("pauline_rc").getName());
			copyFromPackage(R.raw.rootca, new File("rootca.pem").getName());
			copyFromPackage(R.raw.cacert, new File("cacert.pem").getName());
			copyFromPackage(R.raw.tester_hosts, new File("tester_hosts").getName());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		setContentView(R.layout.activity_main);
		
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
