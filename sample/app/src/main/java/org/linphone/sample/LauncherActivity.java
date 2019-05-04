package org.linphone.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class LauncherActivity extends Activity {
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.launcher);

        mHandler = new Handler();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check whether the Service is already running
        if (LinphoneService.isReady()) {
            onServiceReady();
        } else {
            // If it's not, let's start it
            startService(
                    new Intent().setClass(this, LinphoneService.class));
            // And wait for it to be ready, so we can safely use it afterwards
            new ServiceWaitThread().start();
        }
    }

    private void onServiceReady() {
        // Once the service is ready, we can move on in the application
        // We'll forward the intent action, type and extras so it can be handled
        // by the next activity if needed, it's not the launcher job to do that
        Intent intent = new Intent();
        intent.setClass(LauncherActivity.this, MainActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        intent.setAction(getIntent().getAction());
        intent.setType(getIntent().getType());
        startActivity(intent);
    }

    // This thread will periodically check if the Service is ready, and then call onServiceReady
    private class ServiceWaitThread extends Thread {
        public void run() {
            while (!LinphoneService.isReady()) {
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            // As we're in a thread, we can't do UI stuff in it, must post a runnable in UI thread
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            onServiceReady();
                        }
                    });
        }
    }
}
