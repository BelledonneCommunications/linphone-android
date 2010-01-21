package org.linphone;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCore.GeneralState;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class Linphone extends Activity implements LinphoneCoreListener {
	static final String TAG="Linphone";
    /** Called when the activity is first created. */
	private static String LINPHONE_FACTORY_RC = "/data/data/org.linphone/files/linphonerc";
	private static String LINPHONE_RC = "/data/data/org.linphone/files/.linphonerc";
	private static String RINGBACK_SND = "/data/data/org.linphone/files/oldphone_mono.wav";

	private LinphoneCore mLinphoneCore;
	private LinphoneProxyConfig mProxyConfig;
	private LinphoneAuthInfo mAuthInfo;
	Timer mTimer = new Timer("Linphone scheduler");
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        try {
			copyAssetsFromPackage();
		
		mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(	this
																			, new File(LINPHONE_RC) 
																			, new File(LINPHONE_FACTORY_RC)
																			, null);
		mAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo("jehan", "223299");
		mLinphoneCore.addAuthInfo(mAuthInfo);

		mProxyConfig = mLinphoneCore.createProxyConfig("sip:jehan@sip.antisip.com", "sip:sip.antisip.com",null);
		mProxyConfig.enableRegister(true);
		mLinphoneCore.addtProxyConfig(mProxyConfig);
		mLinphoneCore.setDefaultProxyConfig(mProxyConfig);
		
		TimerTask lTask = new TimerTask() {

			@Override
			public void run() {
				mLinphoneCore.iterate();
				
			}
			
		};
		mTimer.scheduleAtFixedRate(lTask, 0, 100);
		} catch (Exception e) {
			Log.e(TAG,"Cannot start linphone",e);
		}
		
    }
    public void copyAssetsFromPackage() throws IOException {
    	copyIfNotExist(R.raw.oldphone_mono,RINGBACK_SND);
    	copyIfNotExist(R.raw.linphonerc,LINPHONE_FACTORY_RC);
    }
    private  void copyIfNotExist(int ressourceId,String target) throws IOException {
  		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {		
			FileOutputStream lOutputStream = openFileOutput (lFileToCopy.getName(), 0);
			InputStream lInputStream = getResources().openRawResource(ressourceId);
			int readByte;
			byte[] buff = new byte[8048];
			while (( readByte = lInputStream.read(buff))!=-1) {
				lOutputStream.write(buff,0, readByte);
			}
			lOutputStream.flush();
			lOutputStream.close();
			lInputStream.close();
		}    	
    	
    }
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		// TODO Auto-generated method stub
		
	}
	public void byeReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub
		
	}
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void displayStatus(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void generalState(LinphoneCore lc, GeneralState state) {
		// TODO Auto-generated method stub
		
	}
	public void inviteReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub
		
	}
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}
}