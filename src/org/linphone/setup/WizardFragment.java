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
import java.util.Locale;
import java.util.regex.Pattern;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
	private EditText username, password, passwordConfirm, email;
	
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean emailOk = false;
	private boolean confirmPasswordOk = false;
	private ImageView createAccount;
	private TextView errorMessage;
	private char[] acceptedChars = new char[]{ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '_', '-' };
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.setup_wizard, container, false);
		
		username = (EditText) view.findViewById(R.id.setup_username);
    	ImageView usernameOkIV = (ImageView) view.findViewById(R.id.setup_username_ok);
    	addXMLRPCUsernameHandler(username, usernameOkIV);
    	InputFilter filter = new InputFilter(){
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (end > start) {
                    for (int index = start; index < end; index++) {                                         
                        if (!new String(acceptedChars).contains(String.valueOf(source.charAt(index)))) { 
                            return ""; 
                        }               
                    }
                }
                return null;
            }
        };
    	username.setFilters(new InputFilter[] { filter });

    	password = (EditText) view.findViewById(R.id.setup_password);
    	passwordConfirm = (EditText) view.findViewById(R.id.setup_password_confirm);
    	
    	ImageView passwordOkIV = (ImageView) view.findViewById(R.id.setup_password_ok);
    	addXMLRPCPasswordHandler(password, passwordOkIV);
    	
    	ImageView passwordConfirmOkIV = (ImageView) view.findViewById(R.id.setup_confirm_password_ok);
    	addXMLRPCConfirmPasswordHandler(password, passwordConfirm, passwordConfirmOkIV);

    	email = (EditText) view.findViewById(R.id.setup_email);
    	ImageView emailOkIV = (ImageView) view.findViewById(R.id.setup_email_ok);
    	addXMLRPCEmailHandler(email, emailOkIV);

    	errorMessage = (TextView) view.findViewById(R.id.setup_error);
    	
    	createAccount = (ImageView) view.findViewById(R.id.setup_create);
    	createAccount.setEnabled(false);
    	createAccount.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						createAccount(username.getText().toString().toLowerCase(Locale.getDefault()), password.getText().toString(), email.getText().toString(), false);
					}
				});
				builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
				if(!username.getText().toString().equals(username.getText().toString().toLowerCase(Locale.getDefault()))){
					builder.setMessage(getString(R.string.setup_confirm_username).replace("%s", username.getText().toString().toLowerCase(Locale.getDefault())));
					AlertDialog dialog = builder.create();
					dialog.show();
				} else {
					createAccount(username.getText().toString().toLowerCase(Locale.getDefault()), password.getText().toString(), email.getText().toString(), false);
				}
			}
    	});
    	
    	if (getResources().getBoolean(R.bool.pre_fill_email_in_wizard)) {
    		Account[] accounts = AccountManager.get(getActivity()).getAccountsByType("com.google");
    		
    	    for (Account account: accounts) {
    	    	if (isEmailCorrect(account.name)) {
    	            String possibleEmail = account.name;
    	        	email.setText(possibleEmail);
    	        	break;
    	        }
    	    }
    	}
    	
		return view;
	}
	
	private boolean isUsernameCorrect(String username) {
		return username.matches("^[a-zA-Z]+[a-zA-Z0-9.\\-_]{2,}$");
	}
	
	private void isUsernameRegistred(String username, final ImageView icon) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				errorMessage.setText(R.string.wizard_server_unavailable);
				usernameOk = false;
				icon.setImageResource(R.drawable.wizard_notok);
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(getString(R.string.wizard_url)));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					errorMessage.setText(R.string.wizard_username_unavailable);
    					usernameOk = false;
						icon.setImageResource(R.drawable.wizard_notok);
						createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
    					errorMessage.setText("");
    					icon.setImageResource(R.drawable.wizard_ok);
						usernameOk = true;
						createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
					}
	    		};
				
			    public void onResponse(long id, Object result) {
			    	int answer = (Integer) result;
			    	if (answer != 0) {
			    		mHandler.post(runNotOk);
					}
					else {
						mHandler.post(runOk);
					}
			    }
			    
			    public void onError(long id, XMLRPCException error) {
			    	mHandler.post(runNotReachable);
			    }
			   
			    public void onServerError(long id, XMLRPCServerException error) {
			    	mHandler.post(runNotReachable);
			    }
			};

		    client.callAsync(listener, "check_account", username);
		} 
		catch(Exception ex) {
			mHandler.post(runNotReachable);
		}
	}
	
	private boolean isEmailCorrect(String email) {
    	Pattern emailPattern = Patterns.EMAIL_ADDRESS;
    	return emailPattern.matcher(email).matches();
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
		
		final Context context = SetupActivity.instance() == null ? LinphoneService.instance().getApplicationContext() : SetupActivity.instance();
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(context.getString(R.string.wizard_url)));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					errorMessage.setText(R.string.wizard_failed);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
    					SetupActivity.instance().saveCreatedAccount(username, password, context.getString(R.string.default_domain));
    					SetupActivity.instance().displayWizardConfirm(username);
					}
	    		};
	    		
			    public void onResponse(long id, Object result) {
			    	int answer = (Integer) result;
			    	if (answer != 0) {
			    		mHandler.post(runNotOk);
			    	} else {
			    		mHandler.post(runOk);
			    	}
			    }
			    
			    public void onError(long id, XMLRPCException error) {
			    	mHandler.post(runNotReachable);
			    }
			   
			    public void onServerError(long id, XMLRPCServerException error) {
			    	mHandler.post(runNotReachable);
			    }
			};

		    client.callAsync(listener, "create_account_with_useragent", username, password, email, LinphoneManager.getInstance().getUserAgent());
		} 
		catch(Exception ex) {
			mHandler.post(runNotReachable);
		}
	}
	
	private void addXMLRPCUsernameHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				usernameOk = false;
				String username = field.getText().toString().toLowerCase(Locale.getDefault());
				if (isUsernameCorrect(username)) {
					isUsernameRegistred(username, icon);
				} else {
					errorMessage.setText(R.string.wizard_username_incorrect);
					icon.setImageResource(R.drawable.wizard_notok);
				}
			}
		});
	}
	
	private void addXMLRPCEmailHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				emailOk = false;
				if (isEmailCorrect(field.getText().toString())) {
					icon.setImageResource(R.drawable.wizard_ok);
					emailOk = true;
					errorMessage.setText("");
				}
				else {
					errorMessage.setText(R.string.wizard_email_incorrect);
					icon.setImageResource(R.drawable.wizard_notok);
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		});
	}
	
	private void addXMLRPCPasswordHandler(final EditText field1, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
				
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				passwordOk = false;
				if (isPasswordCorrect(field1.getText().toString())) {
					passwordOk = true;
					icon.setImageResource(R.drawable.wizard_ok);
					errorMessage.setText("");
				}
				else {
					errorMessage.setText(R.string.wizard_password_incorrect);
					icon.setImageResource(R.drawable.wizard_notok);
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
	}
	
	private void addXMLRPCConfirmPasswordHandler(final EditText field1, final EditText field2, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
				
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				confirmPasswordOk = false;
				if (field1.getText().toString().equals(field2.getText().toString())) {
					confirmPasswordOk = true;
					icon.setImageResource(R.drawable.wizard_ok);

					if (!isPasswordCorrect(field1.getText().toString())) {
						errorMessage.setText(R.string.wizard_password_incorrect);
					}
					else {
						errorMessage.setText("");
					}
				}
				else {
					errorMessage.setText(R.string.wizard_passwords_unmatched);
					icon.setImageResource(R.drawable.wizard_notok);
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}
}
