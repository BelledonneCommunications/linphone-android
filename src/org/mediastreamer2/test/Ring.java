/*
Ring.java
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
package org.mediastreamer2.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.linphone.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class Ring extends Activity {
	Thread mWorkerThread = new Thread("Worker Thread");
	/** Called when the activity is first created. */

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ring);
		try {
			try {
				System.loadLibrary("ring");
			} catch (UnsatisfiedLinkError e) {
				Log.e("mediastreamer2", "cannot load libring.so, did you compile SDK with ndk-build RING=yes ", e);
			}
			final File lFileToPlay = getFileStreamPath("oldphone_mono.wav");
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
			init();
			mWorkerThread =  new Thread(new Runnable() {
				public void run() {
					play(lFileToPlay.getAbsolutePath());      
					echo(44100); 
				}

			},"Worker Thread");
			mWorkerThread.start();
		} catch (Exception e) {
			Log.e("ring","error",e); 
		}
	}
	native void play(String file); 
	native void echo(int freq);
	native void init();
}
    