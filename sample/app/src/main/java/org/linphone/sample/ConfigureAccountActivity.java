package org.linphone.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.linphone.core.AccountCreator;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;

public class ConfigureAccountActivity extends Activity {
    private EditText mUsername, mPassword, mDomain;
    private RadioGroup mTransport;
    private Button mConnect;

    private AccountCreator mAccountCreator;
    private CoreListenerStub mCoreListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.configure_account);

        // Account creator can help you create/config accounts, even not sip.linphone.org ones
        // As we only want to configure an existing account, no need for server URL to make requests
        // to know whether or not account exists, etc...
        mAccountCreator = LinphoneService.getCore().createAccountCreator(null);

        mUsername = findViewById(R.id.username);
        mPassword = findViewById(R.id.password);
        mDomain = findViewById(R.id.domain);
        mTransport = findViewById(R.id.assistant_transports);

        mConnect = findViewById(R.id.configure);
        mConnect.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        configureAccount();
                    }
                });

        mCoreListener = new CoreListenerStub() {
            @Override
            public void onRegistrationStateChanged(Core core, ProxyConfig cfg, RegistrationState state, String message) {
                if (state == RegistrationState.Ok) {
                    finish();
                } else if (state == RegistrationState.Failed) {
                    Toast.makeText(ConfigureAccountActivity.this, "Failure: " + message, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        LinphoneService.getCore().addListener(mCoreListener);
    }

    @Override
    protected void onPause() {
        LinphoneService.getCore().removeListener(mCoreListener);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void configureAccount() {
        // At least the 3 below values are required
        mAccountCreator.setUsername(mUsername.getText().toString());
        mAccountCreator.setDomain(mDomain.getText().toString());
        mAccountCreator.setPassword(mPassword.getText().toString());

        // By default it will be UDP if not set, but TLS is strongly recommended
        switch (mTransport.getCheckedRadioButtonId()) {
            case R.id.transport_udp:
                mAccountCreator.setTransport(TransportType.Udp);
                break;
            case R.id.transport_tcp:
                mAccountCreator.setTransport(TransportType.Tcp);
                break;
            case R.id.transport_tls:
                mAccountCreator.setTransport(TransportType.Tls);
                break;
        }

        // This will automatically create the proxy config and auth info and add them to the Core
        ProxyConfig cfg = mAccountCreator.createProxyConfig();
        // Make sure the newly created one is the default
        LinphoneService.getCore().setDefaultProxyConfig(cfg);
    }
}
