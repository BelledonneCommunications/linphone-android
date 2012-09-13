package org.linphone.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.core.Log;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

import com.jayway.android.robotium.solo.Solo;

public class PushNotificationTest extends
		ActivityInstrumentationTestCase2<LinphoneActivity> {

	private Solo solo;
	
	@SuppressWarnings("deprecation")
	public PushNotificationTest() {
		super("org.linphone", LinphoneActivity.class);
	}

	@Override
	  protected void setUp() throws Exception {
	    super.setUp();
		solo = new Solo(getInstrumentation(), getActivity());
	}
	
	private HttpClient createHttpClient()
	{
	    HttpParams params = new BasicHttpParams();
	    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	    HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
	    HttpProtocolParams.setUseExpectContinue(params, true);

	    SchemeRegistry schReg = new SchemeRegistry();
	    schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	    schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
	    ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);

	    return new DefaultHttpClient(conMgr, params);
	}
	
	public void testIncomingPushNotification() {
		solo.assertCurrentActivity("Expected Linphone Activity", LinphoneActivity.class);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String regId = prefs.getString(getActivity().getString(R.string.push_reg_id_key), null);
		
		// Send a push notification
		HttpClient httpClient = createHttpClient();
		HttpPost httpPost = new HttpPost("https://android.googleapis.com/gcm/send");
		httpPost.setHeader("Content-type", "application/x-www-form-urlencoded;charset=UTF-8");
		httpPost.setHeader("Authorization", "key=AIzaSyBJAhCVeeqIErwTfYwy-t83_EwvZlCFo9I"); // Test API
//		httpPost.setHeader("Authorization", "key=AIzaSyDbCO1_KgFhkig_aaTutxx0jEHIib0i8C0");
		
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("data.test", "TEST"));
        nameValuePairs.add(new BasicNameValuePair("registration_id", regId));
        try {
			httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			String result = httpClient.execute(httpPost, new BasicResponseHandler());
			Log.d("TEST Http POST result: " + result);
		} catch (Exception e) {
			e.printStackTrace();
			new junit.framework.TestFailure(this, e.getCause());
		}
		
        // Can be true if a previous notification worked and log hasn't been cleared since...
		Assert.assertTrue(solo.waitForLogMessage("Push notification received", 3000));
	}
	
	@Override
	public void tearDown() throws Exception {
        solo.finishOpenedActivities();
	}
}
