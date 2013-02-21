package org.linphone;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LpConfig;
import org.linphone.mediastream.Log;
import org.linphone.tools.Xml2Lpc;



public class RemoteProvisioning {
	static private class RemoteProvisioningThread extends Thread {
		String mRPAddress;
		String mSchema;
		String mLocalLP;
		boolean value;

		public RemoteProvisioningThread(final String RPAddress, final String LocalLP, final String schema) {
			this.mRPAddress = RPAddress;
			this.mLocalLP = LocalLP;
			this.mSchema = schema;
		}
		
	    public void run() {
	    	try {
	    		value = false;
	    		Log.i("Download remote provisioning file from " + mRPAddress);
	    		URL url = new URL(mRPAddress);
				URLConnection ucon = url.openConnection();
				InputStream is = ucon.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(is);
				byte[] contents = new byte[1024];
			
				int bytesRead = 0;
				String strFileContents = ""; 
				while( (bytesRead = bis.read(contents)) != -1){ 
					strFileContents = new String(contents, 0, bytesRead);               
				}
				Log.i("Download Success");
				
				// Initialize converter
				LpConfig lp = LinphoneCoreFactory.instance().createLpConfig(mLocalLP);
				Xml2Lpc x2l = new Xml2Lpc();
				if(x2l.setXmlString(strFileContents) != 0) {
					Log.e("Error during remote provisioning file parsing");
					return;
				}
				
				// Check if needed
				if(mSchema != null) {
					if(x2l.setXsdFile(mSchema) != 0) {
						Log.e("Error during schema file parsing");
					}
					if(x2l.validate() != 0) {
						Log.e("Can't validate the schema of remote provisioning file");
						return;
					}
				}
				
				// Convert
				if(x2l.convert(lp) != 0) {
					Log.e("Can't convert remote provisioning file to LinphoneConfig");
					return;
				} else {
					lp.sync();
				}
				value = true;
				Log.i("Remote provisioning ok");
	    	} catch (MalformedURLException e) {
	    		Log.e("Invalid remote provisioning url: " + e.getLocalizedMessage());
	    	} catch (IOException e) {
	    		Log.e(e);
	    	} finally {
		    	synchronized(this) {
		    		this.notify();
		    	}
	    	}
	    }
	};
	
	static boolean download(String address, String lpfile, boolean check) {
		try {
			String schema = null;
			if(check) {
				schema = LinphoneManager.getInstance().getLPConfigXsdPath();
			}
			RemoteProvisioningThread thread = new RemoteProvisioningThread(address, lpfile, schema);
			synchronized(thread) {
				thread.start();
				thread.wait();
			}
			return thread.value;
		} catch (InterruptedException e) {
			Log.e(e);
		}
		return false;
	}
	
	static boolean download(String address, String lpfile) {
		return download(address, lpfile, true);
	}
	
	static boolean isAvailable() {
		if(Xml2Lpc.isAvailable()) {
			Log.i("RemoteProvisioning is available");
			return true;
		} else {
			Log.i("RemoteProvisioning is NOT available");
			return false;
		}
	}
	
}
