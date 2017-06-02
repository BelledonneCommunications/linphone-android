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
        if (!LinphoneService.isReady()) {
            finish();
            startService(getIntent().setClass(this, LinphoneService.class));
            return;
        }
        if (!LinphoneManager.isInstanciated()) {
            finish();
            startActivity(getIntent().setClass(this, LinphoneLauncherActivity.class));
            return;
        }
    }
}
