package org.linphone;

import android.app.Activity;
import android.os.Bundle;

public class LinphoneGenericActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*After a crash, Android restart the last Activity so we need to check
        * if all dependencies are load
        */
        if (!LinphoneManager.isInstanciated()) {
            finish();
            startActivity(getIntent().setClass(this, LinphoneLauncherActivity.class));
            return;
        }
    }
}
