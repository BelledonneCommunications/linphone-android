package org.linphone.assistant;
/*
CreateAccountFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import org.linphone.core.LinphoneProxyConfig;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
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
public class CreateAccountFragment extends Fragment {
	private Handler mHandler = new Handler();
	private EditText usernameEdit, passwordEdit, passwordConfirmEdit, emailEdit;
	private TextView usernameError, passwordError, passwordConfirmError, emailError;
	
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean emailOk = false;
	private boolean confirmPasswordOk = false;
	private Button createAccount;
	private char[] acceptedChars = new char[]{ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '_', '-' };
	private char[] acceptedCharsForPhoneNumbers = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+' };
	private String inputFilterCharacters;
	
	private String getUsername() {
		String username = usernameEdit.getText().toString();
		if (getResources().getBoolean(R.bool.allow_only_phone_numbers_in_wizard)) {
			LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
			username = lpc.normalizePhoneNumber(username);
		}
		return username.toLowerCase(Locale.getDefault());
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation, container, false);

		usernameError = (TextView) view.findViewById(R.id.username_error);
		usernameEdit = (EditText) view.findViewById(R.id.username);

		passwordError = (TextView) view.findViewById(R.id.password_error);
		passwordEdit = (EditText) view.findViewById(R.id.password);

		passwordConfirmError = (TextView) view.findViewById(R.id.confirm_password_error);
		passwordConfirmEdit = (EditText) view.findViewById(R.id.confirm_password);

		emailError = (TextView) view.findViewById(R.id.email_error);
		emailEdit = (EditText) view.findViewById(R.id.email);

    	addXMLRPCUsernameHandler(usernameEdit, null);
    	
    	inputFilterCharacters = new String(acceptedChars);
    	if (getResources().getBoolean(R.bool.allow_only_phone_numbers_in_wizard)) {
    		inputFilterCharacters = new String(acceptedCharsForPhoneNumbers);
    	}
    	InputFilter filter = new InputFilter(){
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (end > start) {
                    for (int index = start; index < end; index++) {                                         
                        if (!inputFilterCharacters.contains(String.valueOf(source.charAt(index)))) { 
                            return "";
                        }               
                    }
                }
                return null;
            }
        };
		usernameEdit.setFilters(new InputFilter[] { filter });

    	addXMLRPCPasswordHandler(passwordEdit, null);
    	addXMLRPCConfirmPasswordHandler(passwordEdit, passwordConfirmEdit, null);
    	addXMLRPCEmailHandler(emailEdit, null);

    	createAccount = (Button) view.findViewById(R.id.assistant_create);
    	createAccount.setEnabled(false);
    	createAccount.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						createAccount(getUsername(), passwordEdit.getText().toString(), emailEdit.getText().toString(), false);
					}
				});
				builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
				builder.setMessage(getString(R.string.setup_confirm_username).replace("%s", getUsername()));
				AlertDialog dialog = builder.create();
				dialog.show();
			}
    	});
    	
    	if (getResources().getBoolean(R.bool.pre_fill_email_in_wizard)) {
    		Account[] accounts = AccountManager.get(getActivity()).getAccountsByType("com.google");
    		
    	    for (Account account: accounts) {
    	    	if (isEmailCorrect(account.name)) {
    	            String possibleEmail = account.name;
					emailEdit.setText(possibleEmail);
    	        	break;
    	        }
    	    }
    	}
    	
		return view;
	}

	private void displayError(Boolean isOk, TextView error, EditText editText, String errorText){
		if(isOk || editText.getText().toString().equals("")){
			error.setVisibility(View.INVISIBLE);
			error.setText(errorText);
			editText.setBackgroundResource(R.drawable.resizable_textfield);
		} else {
			error.setVisibility(View.VISIBLE);
			error.setText(errorText);
			editText.setBackgroundResource(R.drawable.resizable_textfield_error);
		}
	}
	
	private boolean isUsernameCorrect(String username) {
		if (getResources().getBoolean(R.bool.allow_only_phone_numbers_in_wizard)) {
			LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
			return lpc.isPhoneNumber(username);
		} else {
			return username.matches("^[a-zA-Z]+[a-zA-Z0-9.\\-_]{2,}$");
		}
	}
	
	private void isUsernameRegistred(final String username, final ImageView icon) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				usernameOk = false;
				displayError(usernameOk, usernameError, usernameEdit, getResources().getString(R.string.wizard_server_unavailable));
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(getString(R.string.wizard_url)));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					usernameOk = false;
						displayError(usernameOk, usernameError, usernameEdit, getResources().getString(R.string.wizard_username_unavailable));
						createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
						usernameOk = true;
						displayError(usernameOk, usernameError, usernameEdit, "");
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
				//TODO errorMessage.setText(R.string.wizard_server_unavailable);
			}
		};
		
		final Context context = AssistantActivity.instance() == null ? LinphoneService.instance().getApplicationContext() : AssistantActivity.instance();
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(context.getString(R.string.wizard_url)));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
    					//TODO errorMessage.setText(R.string.wizard_failed);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
    					AssistantActivity.instance().saveCreatedAccount(username, password, null, context.getString(R.string.default_domain), null);
    					AssistantActivity.instance().displayWizardConfirm(username);
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
					if (getResources().getBoolean(R.bool.allow_only_phone_numbers_in_wizard)) {
						LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
						username = lpc.normalizePhoneNumber(username);
					}
					isUsernameRegistred(username, icon);
				} else {
					displayError(usernameOk, usernameError, usernameEdit, getResources().getString(R.string.wizard_username_incorrect));
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
					emailOk = true;
					displayError(emailOk, emailError, emailEdit, "");
				}
				else {
					displayError(emailOk, emailError, emailEdit, getString(R.string.wizard_email_incorrect));
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
					displayError(passwordOk, passwordError, passwordEdit, "");
				}
				else {
					displayError(passwordOk, passwordError, passwordEdit, getString(R.string.wizard_password_incorrect));
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

					if (!isPasswordCorrect(field1.getText().toString())) {
						displayError(passwordOk, passwordError, passwordEdit, getString(R.string.wizard_password_incorrect));
					}
					else {
						displayError(confirmPasswordOk, passwordConfirmError, passwordConfirmEdit, "");
					}
				}
				else {
					displayError(confirmPasswordOk, passwordConfirmError, passwordConfirmEdit, getString(R.string.wizard_passwords_unmatched));
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}
}
