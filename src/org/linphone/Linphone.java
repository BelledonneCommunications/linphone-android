package org.linphone;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.app.Activity;
import android.os.Bundle;

public class Linphone extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    public void copyAssetsFromPackage() throws IOException {
   		File lFileToPlay = new File("/data/data/"+this.getPackageName()+"/files/oldphone_mono.wav");
		if (!lFileToPlay.exists()) {		
			FileOutputStream lOutputStream = openFileOutput ("oldphone_mono.wav", 0);
			InputStream lInputStream = getResources().openRawResource(R.raw.oldphone_mono);
			int readByte;
			while (( readByte = lInputStream.read())!=-1) {
				lOutputStream.write(readByte);
			}
			lOutputStream.flush();
			lOutputStream.close();
			lInputStream.close();
		} 
    }
}