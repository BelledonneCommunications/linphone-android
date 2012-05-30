/*
LinphonePreferencesActivity.java
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

package org.linphone;

import static org.linphone.R.string.ec_calibrating;
import static org.linphone.R.string.pref_codec_amr_key;
import static org.linphone.R.string.pref_codec_amrwb_key;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_echo_cancellation_key;
import static org.linphone.R.string.pref_echo_canceller_calibration_key;
import static org.linphone.R.string.pref_echo_limiter_key;
import static org.linphone.R.string.pref_media_encryption_key;
import static org.linphone.R.string.pref_video_enable_key;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.LinphoneManager.LinphoneConfigException;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;

public class LinphonePreferencesActivity extends PreferenceActivity implements EcCalibrationListener {
	private Handler mHandler = new Handler();
	private CheckBoxPreference ecCalibratePref;
	private CheckBoxPreference elPref;
	private CheckBoxPreference ecPref;
	private ListPreference mencPref;
	private int nbAccounts = 1;
	
	// Wizard fields
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean emailOk = false;
	private AlertDialog wizardDialog;
	private Button createAccount;
	private TextView errorMessage;
	private PreferenceCategory accounts;
	private String username;
	
	private static final int ADD_SIP_ACCOUNT = 0x666;
	private static final int WIZARD_ID = 0x667;
	private static final int CONFIRM_ID = 0x668;
	private static final int ACCOUNTS_SETTINGS_ID = 0;
	private static final int ADD_ACCOUNT_SETTINGS_ID = 1;
	private static final int WIZARD_SETTINGS_ID = 2;
	private static final int CAMERA_SETTINGS_ID = 6;

	private SharedPreferences prefs() {
		return getPreferenceManager().getSharedPreferences();
	}

	private CheckBoxPreference findCheckbox(int key) {
		return (CheckBoxPreference) findPreference(getString(key));
	}

	private void detectAudioCodec(int id, String mime, int rate, boolean hide) {
		boolean enable = LinphoneService.isReady() && LinphoneManager.getLc().findPayloadType(mime, rate)!=null;
		Preference cb = findPreference(id);
		cb.setEnabled(enable);
		if (hide && !enable) {
			cb.setLayoutResource(R.layout.hidden);
		}
	}

	private void detectVideoCodec(int id, String mime) {
		findPreference(id).setEnabled(LinphoneManager.getInstance().detectVideoCodec(mime));
	}

	private void createDynamicAccountsPreferences() {
		accounts = (PreferenceCategory) getPreferenceScreen().getPreference(ACCOUNTS_SETTINGS_ID);
		accounts.removeAll();
		
		// Get already configured extra accounts
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
		for (int i = 0; i < nbAccounts; i++) {
			// For each, add menus to configure it
			addExtraAccountPreferencesButton(accounts, i, false);
		}
	}
	
	private void createAddAccountButton() {
		Preference addAccount = (Preference) getPreferenceScreen().getPreference(ADD_ACCOUNT_SETTINGS_ID);
		addAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	        public boolean onPreferenceClick(Preference preference) {
	        	addExtraAccountPreferencesButton(accounts, nbAccounts, true);
	        	Intent i = new Intent();
				i.putExtra("Account",nbAccounts);
	        	nbAccounts++;
				i.setClass(LinphonePreferencesActivity.this, LinphonePreferencesSIPAccountActivity.class);
				startActivityForResult(i, ADD_SIP_ACCOUNT);
	        	return true;
	        }
        });
	}
	
	public int getNbAccountsExtra() {
		return nbAccounts;
	}
	
	private void addExtraAccountPreferencesButton(PreferenceCategory parent, final int n, boolean isNewAccount) {
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		if (isNewAccount) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(getString(R.string.pref_extra_accounts), n+1);
			editor.commit();
		}
		
		Preference me = new Preference(LinphonePreferencesActivity.this);
		String keyUsername = getString(R.string.pref_username_key);
		String keyDomain = getString(R.string.pref_domain_key);
		if (n > 0) {
			keyUsername += n + "";
			keyDomain += n + "";
		}
		if (prefs.getString(keyUsername, null) == null) {
			me.setTitle(getString(R.string.pref_sipaccount));
		} else {
			me.setTitle(prefs.getString(keyUsername, "") + "@" + prefs.getString(keyDomain, ""));
		}
		
		me.setOnPreferenceClickListener(new OnPreferenceClickListener() 
		{
			public boolean onPreferenceClick(Preference preference) {
				Intent i = new Intent();
				i.putExtra("Account", n);
				i.setClass(LinphonePreferencesActivity.this, LinphonePreferencesSIPAccountActivity.class);
				startActivityForResult(i, ADD_SIP_ACCOUNT);
				return false;
			}
		});
		parent.addPreference(me);
	}
	
	private void fillLinphoneAccount(int i, String username, String password, boolean createdByWizard) {
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString(getString(R.string.pref_username_key) + i, username);
		editor.putString(getString(R.string.pref_passwd_key) + i, password);
		editor.putString(getString(R.string.pref_domain_key) + i, "sip.linphone.org");
		editor.putString(getString(R.string.pref_proxy_key) + i, "");
		editor.putBoolean(getString(R.string.pref_wizard_key) + i, createdByWizard);
		editor.putBoolean(getString(R.string.pref_activated_key) + i, false);
		editor.putBoolean(getString(R.string.pref_enable_outbound_proxy_key) + i, false);
		
		editor.commit();
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == ADD_SIP_ACCOUNT) {
			//Verify if last created account is filled
			SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
			int n = prefs.getInt(getString(R.string.pref_extra_accounts), 1);
			String keyUsername = getString(R.string.pref_username_key) + (n-1 == 0 ? "" : Integer.toString(n-1));
			
			if (prefs.getString(keyUsername, "").equals("")) {
				//If not, we suppress it to not display a blank field
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt(getString(R.string.pref_extra_accounts), n-1);
				editor.commit();
			}
			
			createDynamicAccountsPreferences();
		}
	}
	
	private void addWizardPreferenceButton() {
		Preference wizard = (Preference) getPreferenceScreen().getPreference(WIZARD_SETTINGS_ID);
		wizard.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	        public boolean onPreferenceClick(Preference preference) {
	        	showDialog(WIZARD_ID);
	        	return true;
	        }
        });
	}
	
	protected Dialog onCreateDialog (int id) {
		if (id == WIZARD_ID) {
			AlertDialog.Builder builder = new AlertDialog.Builder(LinphonePreferencesActivity.this);
	    	LayoutInflater inflater = LayoutInflater.from(LinphonePreferencesActivity.this);
	    	View v = inflater.inflate(R.layout.wizard, null);
	    	builder.setView(v);
	    	
	    	final EditText username = (EditText) v.findViewById(R.id.wizardUsername);
	    	ImageView usernameOkIV = (ImageView) v.findViewById(R.id.wizardUsernameOk);
	    	addXMLRPCUsernameHandler(username, usernameOkIV);

	    	final EditText password = (EditText) v.findViewById(R.id.wizardPassword);
	    	EditText passwordConfirm = (EditText) v.findViewById(R.id.wizardPasswordConfirm);
	    	ImageView passwordOkIV = (ImageView) v.findViewById(R.id.wizardPasswordOk);
	    	addXMLRPCPasswordHandler(password, passwordConfirm, passwordOkIV);

	    	final EditText email = (EditText) v.findViewById(R.id.wizardEmail);
	    	ImageView emailOkIV = (ImageView) v.findViewById(R.id.wizardEmailOk);
	    	addXMLRPCEmailHandler(email, emailOkIV);

	    	errorMessage = (TextView) v.findViewById(R.id.wizardErrorMessage);
	    	
	    	Button cancel = (Button) v.findViewById(R.id.wizardCancel);
	    	cancel.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					wizardDialog.dismiss();
				}
	    	});
	    	
	    	createAccount = (Button) v.findViewById(R.id.wizardCreateAccount);
	    	createAccount.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					createAccount(username.getText().toString(), password.getText().toString(), email.getText().toString(), false);
				}
	    	});
			createAccount.setEnabled(false);
	    	
	    	builder.setTitle(getString(R.string.wizard_title));
	    	wizardDialog = builder.create();
	    	return wizardDialog;
		}
		else if (id == CONFIRM_ID) {
			AlertDialog.Builder builder = new AlertDialog.Builder(LinphonePreferencesActivity.this);
			builder.setTitle(R.string.wizard_confirmation);
			
			final LayoutInflater inflater = LayoutInflater.from(LinphonePreferencesActivity.this);
	    	View v = inflater.inflate(R.layout.wizard_confirm, null);
	    	builder.setView(v);
	    	
	    	Button check = (Button) v.findViewById(R.id.wizardCheckAccount);
	    	check.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					wizardDialog.dismiss();
					if (isAccountVerified(username)) {
						SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
						SharedPreferences.Editor editor = prefs.edit();
						editor.putBoolean(getString(R.string.pref_activated_key) + (nbAccounts - 1), true);
						editor.commit();
					} else {
						showDialog(CONFIRM_ID);
					}
				}
			});
	    	
	    	Button cancel = (Button) v.findViewById(R.id.wizardCancel);
	    	cancel.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					wizardDialog.dismiss();
				}
			});
	    	
			wizardDialog = builder.create();
			return wizardDialog;
		}
		return null;
	}
	
	private boolean isUsernameCorrect(String username) {
		return username.matches("^[a-zA-Z]+[a-zA-Z0-9.\\-_]{2,}$");
	}
	
	static boolean isAccountVerified(String username) {
		try {
			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
		    Object resultO = client.call("check_account_validated", "sip:" + username + "@sip.linphone.org");
		    Integer result = Integer.parseInt(resultO.toString());
		    
		    return result == 1;
		} catch(Exception ex) {

		}
		return false;
	}
	
	private void isUsernameRegistred(String username, final ImageView icon) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				errorMessage.setText(R.string.wizard_server_unavailable);
				usernameOk = false;
				icon.setImageResource(R.drawable.notok);
				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
			}
		};
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					errorMessage.setText(R.string.wizard_username_unavailable);
    					usernameOk = false;
						icon.setImageResource(R.drawable.notok);
						createAccount.setEnabled(usernameOk && passwordOk && emailOk);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
    					errorMessage.setText("");
    					icon.setImageResource(R.drawable.ok);
						usernameOk = true;
						createAccount.setEnabled(usernameOk && passwordOk && emailOk);
					}
	    		};
				
			    public void onResponse(long id, Object result) {
			    	int answer = (Integer) result;
			    	if (answer != 0) {
			    		runOnUiThread(runNotOk);
					}
					else {
						runOnUiThread(runOk);
					}
			    }
			    
			    public void onError(long id, XMLRPCException error) {
			    	runOnUiThread(runNotReachable);
			    }
			   
			    public void onServerError(long id, XMLRPCServerException error) {
			    	runOnUiThread(runNotReachable);
			    }
			};

		    client.callAsync(listener, "check_account", username);
		} 
		catch(Exception ex) {
			runOnUiThread(runNotReachable);
		}
	}
	
	private boolean isEmailCorrect(String email) {
		return email.matches("^[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$");
	}
	
	private boolean isPasswordCorrect(String password) {
		return password.length() >= 6;
	}
	
	private void createAccount(final String username, final String password, String email, boolean suscribe) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				errorMessage.setText(R.string.wizard_server_unavailable);
			}
		};
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					errorMessage.setText(R.string.wizard_failed);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
			    		addExtraAccountPreferencesButton(accounts, nbAccounts, true);
			    		LinphonePreferencesActivity.this.username = username;
			    		fillLinphoneAccount(nbAccounts, username, password, true);
			        	nbAccounts++;
			    		createDynamicAccountsPreferences();
			    		wizardDialog.dismiss();
			    		
			    		showDialog(CONFIRM_ID);
					}
	    		};
	    		
			    public void onResponse(long id, Object result) {
			    	int answer = (Integer) result;
			    	if (answer != 0) {
			    		runOnUiThread(runNotOk);
			    	} else {
			    		runOnUiThread(runOk);
			    	}
			    }
			    
			    public void onError(long id, XMLRPCException error) {
			    	runOnUiThread(runNotReachable);
			    }
			   
			    public void onServerError(long id, XMLRPCServerException error) {
			    	runOnUiThread(runNotReachable);
			    }
			};

		    client.callAsync(listener, "create_account", username, password, email, suscribe ? 1 : 0);
		} 
		catch(Exception ex) {
			runOnUiThread(runNotReachable);
		}
	}
	
	private void addXMLRPCUsernameHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
				
			}

			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
				
			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) 
			{
				usernameOk = false;
				if (isUsernameCorrect(field.getText().toString()))
				{
					isUsernameRegistred(field.getText().toString(), icon);
				}
				else {
					errorMessage.setText(R.string.wizard_username_incorrect);
					icon.setImageResource(R.drawable.notok);
				}
			}
		});
	}
	
	private void addXMLRPCEmailHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
				
			}

			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
				
			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) 
			{
				emailOk = false;
				if (isEmailCorrect(field.getText().toString())) {
					icon.setImageResource(R.drawable.ok);
					emailOk = true;
					errorMessage.setText("");
				}
				else {
					errorMessage.setText(R.string.wizard_email_incorrect);
					icon.setImageResource(R.drawable.notok);
				}
				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
			}
		});
	}
	
	private void addXMLRPCPasswordHandler(final EditText field1, final EditText field2, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
				
			}

			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
				
			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) 
			{
				passwordOk = false;
				if (isPasswordCorrect(field1.getText().toString()) && field1.getText().toString().equals(field2.getText().toString())) {
					passwordOk = true;
					icon.setImageResource(R.drawable.ok);
					errorMessage.setText("");
				}
				else {
					if (isPasswordCorrect(field1.getText().toString())) {
						errorMessage.setText(R.string.wizard_passwords_unmatched);
					}
					else {
						errorMessage.setText(R.string.wizard_password_incorrect);
					}
					icon.setImageResource(R.drawable.notok);
				}
				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}
	
	private void verifiyAccountsActivated() {
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		for (int i = 0; i < nbAccounts; i++) {
			String key = (i == 0 ? "" : Integer.toString(i));
			boolean createdByWizard = prefs.getBoolean(getString(R.string.pref_wizard_key) + key, false);
			boolean activated = prefs.getBoolean(getString(R.string.pref_activated_key) + key, true);
			if (createdByWizard && !activated) {
				//Check if account has been activated since
				activated = isAccountVerified(prefs.getString(getString(R.string.pref_username_key) + key, ""));
				if (activated) {
					SharedPreferences.Editor editor = prefs.edit();
					editor.putBoolean(getString(R.string.pref_activated_key) + key, true);
					editor.commit();
				} else {
					showDialog(CONFIRM_ID);
				}
			}
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

		if (!getResources().getBoolean(R.bool.hide_accounts)) {
			createDynamicAccountsPreferences();
			
			// Accounts have to be displayed to show add account button
			if (getResources().getBoolean(R.bool.hide_add_account_button)) {
				Preference addAccount = (Preference) getPreferenceScreen().getPreference(ADD_ACCOUNT_SETTINGS_ID);
				addAccount.setLayoutResource(R.layout.hidden);
			} else {
				createAddAccountButton();
			}
		} else {
			// Hide add account button if accounts are hidden
			Preference addAccount = (Preference) getPreferenceScreen().getPreference(ADD_ACCOUNT_SETTINGS_ID);
			addAccount.setLayoutResource(R.layout.hidden);
			
			// Hide category
			PreferenceCategory accounts = (PreferenceCategory) getPreferenceScreen().getPreference(ACCOUNTS_SETTINGS_ID);
			accounts.removeAll();
			accounts.setLayoutResource(R.layout.hidden);
		}
		
		if (getResources().getBoolean(R.bool.hide_wizard)) {
			Preference wizard = (Preference) getPreferenceScreen().getPreference(WIZARD_SETTINGS_ID);
			wizard.setLayoutResource(R.layout.hidden);
		} else {
			addWizardPreferenceButton();
			verifiyAccountsActivated();
		}
		
		addTransportChecboxesListener();
		
		ecCalibratePref = (CheckBoxPreference) findPreference(pref_echo_canceller_calibration_key);
		ecCalibratePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startEcCalibration();
				return false;
			}
		});
		ecPref = (CheckBoxPreference) findPreference(pref_echo_cancellation_key);
		elPref = (CheckBoxPreference) findPreference(pref_echo_limiter_key);
		mencPref = (ListPreference) findPreference(pref_media_encryption_key);

		boolean fastCpu = Version.isArmv7();
		if (fastCpu) {
			detectAudioCodec(pref_codec_ilbc_key, "iLBC", 8000, false);
			findPreference(pref_codec_speex16_key).setEnabled(true);
			//findPreference(pref_codec_speex32_key)).setEnabled(enableIlbc);
		}
		findPreference(pref_echo_limiter_key).setEnabled(true);

		initializeMediaEncryptionPreferences();
	
		detectAudioCodec(pref_codec_amr_key,"AMR", 8000, false);
        detectAudioCodec(pref_codec_amrwb_key,"AMR-WB", 16000, false);
		//detectAudioCodec(R.string.pref_codec_silk8_key,"SILK",8000, true);
		//detectAudioCodec(R.string.pref_codec_silk12_key,"SILK",12000, true);
		detectAudioCodec(R.string.pref_codec_silk16_key,"SILK",16000, true);
		detectAudioCodec(R.string.pref_codec_silk24_key,"SILK",24000, true);
		detectAudioCodec(R.string.pref_codec_g729_key,"G729",8000, true);
		
		// No video
		if (!Version.isVideoCapable()) {
			uncheckAndDisableCheckbox(pref_video_enable_key);
		} else if (!AndroidCameraConfiguration.hasFrontCamera()) {
			uncheckDisableAndHideCheckbox(R.string.pref_video_use_front_camera_key);
		}

		if (prefs().getBoolean(LinphoneActivity.PREF_FIRST_LAUNCH,true)) {
			doOnFirstLaunch();
		}
		if (Hacks.hasBuiltInEchoCanceller()) {
			uncheckDisableAndHideCheckbox(R.string.pref_echo_limiter_key);
			uncheckDisableAndHideCheckbox(R.string.pref_echo_cancellation_key);
			uncheckDisableAndHideCheckbox(R.string.pref_echo_canceller_calibration_key);
		}


		detectVideoCodec(R.string.pref_video_codec_h264_key, "H264");
		if (!Version.hasNeon())
		{
			// Android without neon doesn't support H264
			findPreference(R.string.pref_video_codec_h264_key).setEnabled(false);
			findPreference(R.string.pref_video_codec_h264_key).setDefaultValue(false);
		}
		
		addEchoPrefsListener();
		
		if (Hacks.needSoftvolume()) checkAndDisableCheckbox(R.string.pref_audio_soft_volume_key);

		if (!LinphoneManager.getLc().isTunnelAvailable()){
			hidePreferenceCategory(R.string.pref_tunnel_key);
		}
		
		if (getResources().getBoolean(R.bool.hide_camera_settings)) {
			PreferenceScreen screen = getPreferenceScreen();
			PreferenceCategory videoSettings = (PreferenceCategory) screen.getPreference(CAMERA_SETTINGS_ID);
			videoSettings.removeAll();
			videoSettings.setLayoutResource(R.layout.hidden);
			
			CheckBoxPreference enableVideo = (CheckBoxPreference) findPreference(R.string.pref_video_enable_key);
			enableVideo.setLayoutResource(R.layout.hidden);
		}
	}

	private void hidePreferenceCategory(int key) {
		PreferenceCategory p = (PreferenceCategory) findPreference(key);
		p.removeAll();
		p.setLayoutResource(R.layout.hidden);
	}

	private void doOnFirstLaunch() {
		manageCheckbox(R.string.pref_echo_limiter_key, !Hacks.hasBuiltInEchoCanceller(), true, false);
		prefs().edit().putBoolean(LinphoneActivity.PREF_FIRST_LAUNCH, false).commit();
	}

	private void initializeMediaEncryptionPreferences() {
		LinphoneCore lc=LinphoneManager.getLc();
		boolean hasZrtp=lc.mediaEncryptionSupported(MediaEncryption.ZRTP);
		boolean hasSrtp=lc.mediaEncryptionSupported(MediaEncryption.SRTP);
		if (!hasSrtp && !hasZrtp){
			mencPref.setEnabled(false);
		}else{
			List<CharSequence> mencEntries=new ArrayList<CharSequence>();
			List<CharSequence> mencEntryValues=new ArrayList<CharSequence>();
			mencEntries.add(getString(R.string.media_encryption_none));
			mencEntryValues.add(getString(R.string.pref_media_encryption_key_none));
			if (hasSrtp){
				mencEntries.add(getString(R.string.media_encryption_srtp));
				mencEntryValues.add(getString(R.string.pref_media_encryption_key_srtp));
			}
			if (hasZrtp){
				mencEntries.add(getString(R.string.media_encryption_zrtp));
				mencEntryValues.add(getString(R.string.pref_media_encryption_key_zrtp));
			}
			CharSequence[] contents=new CharSequence[mencEntries.size()];
			mencEntries.toArray(contents);
			mencPref.setEntries(contents);
			contents=new CharSequence[mencEntryValues.size()];
			mencEntryValues.toArray(contents);
			mencPref.setEntryValues(contents);
			mencPref.setDefaultValue(getString(R.string.media_encryption_none));
			//mencPref.setValueIndex(mencPref.findIndexOfValue(getString(R.string.media_encryption_none)));
		}
	}

	private void addEchoPrefsListener(){
		OnPreferenceChangeListener ec_listener=new OnPreferenceChangeListener(){
			public boolean onPreferenceChange(Preference arg0, Object newValue) {
				Boolean val=(Boolean)newValue;
				if (val){
					elPref.setChecked(!val);
				}
				return true;
			}
		};
		OnPreferenceChangeListener el_listener=new OnPreferenceChangeListener(){
			public boolean onPreferenceChange(Preference arg0, Object newValue) {
				Boolean val=(Boolean)newValue;
				if (val){
					ecPref.setChecked(!val);
				}
				return true;
			}
		};
		ecPref.setOnPreferenceChangeListener(ec_listener);
		elPref.setOnPreferenceChangeListener(el_listener);
	}

	private void addTransportChecboxesListener() {

		final List<CheckBoxPreference> checkboxes = Arrays.asList(
				findCheckbox(R.string.pref_transport_udp_key)
				,findCheckbox(R.string.pref_transport_tcp_key)
				,findCheckbox(R.string.pref_transport_tls_key)
				);
		

		OnPreferenceChangeListener changedListener = new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						p.setChecked(false);
					}
					return true;
				} else {
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						if (p.isChecked()) return true;
					}
					return false;
				}
			}
		};
		
		OnPreferenceClickListener clickListener = new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				// Forbid no protocol selection
				
				if (((CheckBoxPreference) preference).isChecked()) {
					// Trying to unckeck
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						if (p.isChecked()) return false;
					}
					/*Toast.makeText(LinphonePreferencesActivity.this,
							getString(R.string.at_least_a_protocol),
							Toast.LENGTH_SHORT).show();*/
					return true;
				}
				return false;
			}
		};

		for (CheckBoxPreference c : checkboxes) {
			c.setOnPreferenceChangeListener(changedListener);
			c.setOnPreferenceClickListener(clickListener);
		}
	}

	private synchronized void startEcCalibration() {
		try {
			LinphoneManager.getInstance().startEcCalibration(this);

			ecCalibratePref.setSummary(ec_calibrating);
			ecCalibratePref.getEditor().putBoolean(getString(pref_echo_canceller_calibration_key), false).commit();
		} catch (LinphoneCoreException e) {
			Log.w(e, "Cannot calibrate EC");
		}	
	}

	public void onEcCalibrationStatus(final EcCalibratorStatus status, final int delayMs) {

		mHandler.post(new Runnable() {
			public void run() {
				if (status == EcCalibratorStatus.Done) {
					ecCalibratePref.setSummary(String.format(getString(R.string.ec_calibrated), delayMs));
					ecCalibratePref.setChecked(true);

				} else if (status == EcCalibratorStatus.Failed) {
					ecCalibratePref.setSummary(R.string.failed);
					ecCalibratePref.setChecked(false);
					elPref.setChecked(true);
					ecPref.setChecked(false);
				}
			}
		});
	}

	private void uncheckDisableAndHideCheckbox(int key) { 
		manageCheckbox(key, false, false, true);
	}

	private void uncheckAndDisableCheckbox(int key) {
		manageCheckbox(key, false, false, false);
	}
	private void checkAndDisableCheckbox(int key) {
		manageCheckbox(key, true, false, false);
	}
	private void manageCheckbox(int key, boolean value, boolean enabled, boolean hidden) {
		CheckBoxPreference box = (CheckBoxPreference) findPreference(key);
		box.setEnabled(enabled);
		box.setChecked(value);
		writeBoolean(key, value);
		if (hidden) box.setLayoutResource(R.layout.hidden);
	}

	private Preference findPreference(int key) {
		return getPreferenceManager().findPreference(getString(key));
	}

	private void writeBoolean(int key, boolean value) {
		prefs().edit().putBoolean(getString(key), value).commit();
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (!isFinishing()) return;

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		
		if (lc != null && (lc.isInComingInvitePending() || lc.isIncall())) {
			Log.w("Call in progress => settings not applied");
			return;
		}

		try {
			LinphoneManager.getInstance().initFromConf();
			lc.setVideoPolicy(LinphoneManager.getInstance().isAutoInitiateVideoCalls(), LinphoneManager.getInstance().isAutoAcceptCamera());
		} catch (LinphoneException e) {
			if (! (e instanceof LinphoneConfigException)) {
				Log.e(e, "Cannot update config");
				return;
			}

			LinphoneActivity.instance().showPreferenceErrorDialog(e.getMessage());
		}
	}

}
