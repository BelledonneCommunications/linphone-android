package org.linphone.setup;
/*
WizardFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
import java.net.URL;

import org.linphone.PreferencesActivity;
import org.linphone.R;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;
/**
 * @author Sylvain Berfini
 */
public class WizardFragment extends Fragment {
	private Handler mHandler = new Handler();
	
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean emailOk = false;
	private AlertDialog wizardDialog;
	private Button createAccount;
	private TextView errorMessage;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.setup_wizard, container, false);
		
		return view;
	}
	
//	protected Dialog onCreateDialog (int id) {
//	if (id == WIZARD_ID) {
//		AlertDialog.Builder builder = new AlertDialog.Builder(PreferencesActivity.this);
//    	LayoutInflater inflater = LayoutInflater.from(PreferencesActivity.this);
//    	View v = inflater.inflate(R.layout.wizard, null);
//    	builder.setView(v);
//    	
//    	final EditText username = (EditText) v.findViewById(R.id.wizardUsername);
//    	ImageView usernameOkIV = (ImageView) v.findViewById(R.id.wizardUsernameOk);
//    	addXMLRPCUsernameHandler(username, usernameOkIV);
//
//    	final EditText password = (EditText) v.findViewById(R.id.wizardPassword);
//    	EditText passwordConfirm = (EditText) v.findViewById(R.id.wizardPasswordConfirm);
//    	ImageView passwordOkIV = (ImageView) v.findViewById(R.id.wizardPasswordOk);
//    	addXMLRPCPasswordHandler(password, passwordConfirm, passwordOkIV);
//
//    	final EditText email = (EditText) v.findViewById(R.id.wizardEmail);
//    	ImageView emailOkIV = (ImageView) v.findViewById(R.id.wizardEmailOk);
//    	addXMLRPCEmailHandler(email, emailOkIV);
//
//    	errorMessage = (TextView) v.findViewById(R.id.wizardErrorMessage);
//    	
//    	Button cancel = (Button) v.findViewById(R.id.wizardCancel);
//    	cancel.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				wizardDialog.dismiss();
//			}
//    	});
//    	
//    	createAccount = (Button) v.findViewById(R.id.wizardCreateAccount);
//    	createAccount.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				createAccount(username.getText().toString(), password.getText().toString(), email.getText().toString(), false);
//			}
//    	});
//		createAccount.setEnabled(false);
//    	
//    	builder.setTitle(getString(R.string.wizard_title));
//    	wizardDialog = builder.create();
//    	return wizardDialog;
//	}
//	else if (id == CONFIRM_ID) {
//		AlertDialog.Builder builder = new AlertDialog.Builder(PreferencesActivity.this);
//		builder.setTitle(R.string.wizard_confirmation);
//		
//		final LayoutInflater inflater = LayoutInflater.from(PreferencesActivity.this);
//    	View v = inflater.inflate(R.layout.wizard_confirm, null);
//    	builder.setView(v);
//    	
//    	Button check = (Button) v.findViewById(R.id.wizardCheckAccount);
//    	check.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				wizardDialog.dismiss();
//				if (isAccountVerified(username)) {
//					SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
//					SharedPreferences.Editor editor = prefs.edit();
//					editor.putBoolean(getString(R.string.pref_activated_key) + (nbAccounts - 1), true);
//					editor.commit();
//				} else {
//					showDialog(CONFIRM_ID);
//				}
//			}
//		});
//    	
//    	Button cancel = (Button) v.findViewById(R.id.wizardCancel);
//    	cancel.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				wizardDialog.dismiss();
//			}
//		});
//    	
//		wizardDialog = builder.create();
//		return wizardDialog;
//	}
//	return null;
//}
//	
//	private boolean isUsernameCorrect(String username) {
//		return username.matches("^[a-zA-Z]+[a-zA-Z0-9.\\-_]{2,}$");
//	}
//	
//	static boolean isAccountVerified(String username) {
//		try {
//			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
//		    Object resultO = client.call("check_account_validated", "sip:" + username + "@sip.linphone.org");
//		    Integer result = Integer.parseInt(resultO.toString());
//		    
//		    return result == 1;
//		} catch(Exception ex) {
//
//		}
//		return false;
//	}
//	
//	private void isUsernameRegistred(String username, final ImageView icon) {
//		final Runnable runNotReachable = new Runnable() {
//			public void run() {
//				errorMessage.setText(R.string.wizard_server_unavailable);
//				usernameOk = false;
//				icon.setImageResource(R.drawable.wizard_notok);
//				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
//			}
//		};
//		
//		try {
//			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
//			
//			XMLRPCCallback listener = new XMLRPCCallback() {
//				Runnable runNotOk = new Runnable() {
//    				public void run() {
//    					errorMessage.setText(R.string.wizard_username_unavailable);
//    					usernameOk = false;
//						icon.setImageResource(R.drawable.wizard_notok);
//						createAccount.setEnabled(usernameOk && passwordOk && emailOk);
//					}
//	    		};
//	    		
//	    		Runnable runOk = new Runnable() {
//    				public void run() {
//    					errorMessage.setText("");
//    					icon.setImageResource(R.drawable.wizard_ok);
//						usernameOk = true;
//						createAccount.setEnabled(usernameOk && passwordOk && emailOk);
//					}
//	    		};
//				
//			    public void onResponse(long id, Object result) {
//			    	int answer = (Integer) result;
//			    	if (answer != 0) {
//			    		mHandler.post(runNotOk);
//					}
//					else {
//						mHandler.post(runOk);
//					}
//			    }
//			    
//			    public void onError(long id, XMLRPCException error) {
//			    	mHandler.post(runNotReachable);
//			    }
//			   
//			    public void onServerError(long id, XMLRPCServerException error) {
//			    	mHandler.post(runNotReachable);
//			    }
//			};
//
//		    client.callAsync(listener, "check_account", username);
//		} 
//		catch(Exception ex) {
//			mHandler.post(runNotReachable);
//		}
//	}
//	
//	private boolean isEmailCorrect(String email) {
//		return email.matches("^[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$");
//	}
//	
//	private boolean isPasswordCorrect(String password) {
//		return password.length() >= 6;
//	}
//	
//	private void createAccount(final String username, final String password, String email, boolean suscribe) {
//		final Runnable runNotReachable = new Runnable() {
//			public void run() {
//				errorMessage.setText(R.string.wizard_server_unavailable);
//			}
//		};
//		
//		try {
//			XMLRPCClient client = new XMLRPCClient(new URL("https://www.linphone.org/wizard.php"));
//			
//			XMLRPCCallback listener = new XMLRPCCallback() {
//				Runnable runNotOk = new Runnable() {
//    				public void run() {
//    					errorMessage.setText(R.string.wizard_failed);
//					}
//	    		};
//	    		
//	    		Runnable runOk = new Runnable() {
//    				public void run() {
//			    		addExtraAccountPreferencesButton(accounts, nbAccounts, true);
//			    		PreferencesActivity.this.username = username;
//			    		fillLinphoneAccount(nbAccounts, username, password, true);
//			        	nbAccounts++;
//			    		createDynamicAccountsPreferences();
//			    		wizardDialog.dismiss();
//			    		
//			    		showDialog(CONFIRM_ID);
//					}
//	    		};
//	    		
//			    public void onResponse(long id, Object result) {
//			    	int answer = (Integer) result;
//			    	if (answer != 0) {
//			    		mHandler.post(runNotOk);
//			    	} else {
//			    		mHandler.post(runOk);
//			    	}
//			    }
//			    
//			    public void onError(long id, XMLRPCException error) {
//			    	mHandler.post(runNotReachable);
//			    }
//			   
//			    public void onServerError(long id, XMLRPCServerException error) {
//			    	mHandler.post(runNotReachable);
//			    }
//			};
//
//		    client.callAsync(listener, "create_account", username, password, email, suscribe ? 1 : 0);
//		} 
//		catch(Exception ex) {
//			mHandler.post(runNotReachable);
//		}
//	}
//	
//	private void addXMLRPCUsernameHandler(final EditText field, final ImageView icon) {
//		field.addTextChangedListener(new TextWatcher() {
//			public void afterTextChanged(Editable arg0) {
//				
//			}
//
//			public void beforeTextChanged(CharSequence arg0, int arg1,
//					int arg2, int arg3) {
//				
//			}
//
//			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
//					int arg3) 
//			{
//				usernameOk = false;
//				if (isUsernameCorrect(field.getText().toString()))
//				{
//					isUsernameRegistred(field.getText().toString(), icon);
//				}
//				else {
//					errorMessage.setText(R.string.wizard_username_incorrect);
//					icon.setImageResource(R.drawable.wizard_notok);
//				}
//			}
//		});
//	}
//	
//	private void addXMLRPCEmailHandler(final EditText field, final ImageView icon) {
//		field.addTextChangedListener(new TextWatcher() {
//			public void afterTextChanged(Editable arg0) {
//				
//			}
//
//			public void beforeTextChanged(CharSequence arg0, int arg1,
//					int arg2, int arg3) {
//				
//			}
//
//			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
//					int arg3) 
//			{
//				emailOk = false;
//				if (isEmailCorrect(field.getText().toString())) {
//					icon.setImageResource(R.drawable.wizard_ok);
//					emailOk = true;
//					errorMessage.setText("");
//				}
//				else {
//					errorMessage.setText(R.string.wizard_email_incorrect);
//					icon.setImageResource(R.drawable.wizard_notok);
//				}
//				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
//			}
//		});
//	}
//	
//	private void addXMLRPCPasswordHandler(final EditText field1, final EditText field2, final ImageView icon) {
//		TextWatcher passwordListener = new TextWatcher() {
//			public void afterTextChanged(Editable arg0) {
//				
//			}
//
//			public void beforeTextChanged(CharSequence arg0, int arg1,
//					int arg2, int arg3) {
//				
//			}
//
//			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
//					int arg3) 
//			{
//				passwordOk = false;
//				if (isPasswordCorrect(field1.getText().toString()) && field1.getText().toString().equals(field2.getText().toString())) {
//					passwordOk = true;
//					icon.setImageResource(R.drawable.wizard_ok);
//					errorMessage.setText("");
//				}
//				else {
//					if (isPasswordCorrect(field1.getText().toString())) {
//						errorMessage.setText(R.string.wizard_passwords_unmatched);
//					}
//					else {
//						errorMessage.setText(R.string.wizard_password_incorrect);
//					}
//					icon.setImageResource(R.drawable.wizard_notok);
//				}
//				createAccount.setEnabled(usernameOk && passwordOk && emailOk);
//			}
//		};
//		
//		field1.addTextChangedListener(passwordListener);
//		field2.addTextChangedListener(passwordListener);
//	}
//	
//	private void verifiyAccountsActivated() {
//		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
//		for (int i = 0; i < nbAccounts; i++) {
//			String key = (i == 0 ? "" : Integer.toString(i));
//			boolean createdByWizard = prefs.getBoolean(getString(R.string.pref_wizard_key) + key, false);
//			boolean activated = prefs.getBoolean(getString(R.string.pref_activated_key) + key, true);
//			if (createdByWizard && !activated) {
//				//Check if account has been activated since
//				activated = isAccountVerified(prefs.getString(getString(R.string.pref_username_key) + key, ""));
//				if (activated) {
//					SharedPreferences.Editor editor = prefs.edit();
//					editor.putBoolean(getString(R.string.pref_activated_key) + key, true);
//					editor.commit();
//				} else {
//					showDialog(CONFIRM_ID);
//				}
//			}
//		}
//	}
}
