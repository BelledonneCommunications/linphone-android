package org.linphone.purchase;
/*
InAppPurchaseListener.java
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
import java.util.ArrayList;
import java.util.Locale;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;

/**
 * @author Sylvain Berfini
 */
public class InAppPurchaseActivity extends Activity implements InAppPurchaseListener, OnClickListener {
	private InAppPurchaseHelper inAppPurchaseHelper;
	private LinearLayout purchasableItemsLayout;
	private ArrayList<Purchasable> purchasedItems;
	private ImageView buyItemButton;
	
	private EditText username, password, passwordConfirm;
	private TextView errorMessage;
	private Handler mHandler = new Handler();
	private char[] acceptedChars = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+' };
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean confirmPasswordOk = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		inAppPurchaseHelper = new InAppPurchaseHelper(this, this);
		
		setContentView(R.layout.in_app_store);
		purchasableItemsLayout = (LinearLayout) findViewById(R.id.purchasable_items);
		
		username = (EditText) findViewById(R.id.setup_username);
    	ImageView usernameOkIV = (ImageView) findViewById(R.id.setup_username_ok);
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
    	password = (EditText) findViewById(R.id.setup_password);
    	passwordConfirm = (EditText) findViewById(R.id.setup_password_confirm);
    	ImageView passwordOkIV = (ImageView) findViewById(R.id.setup_password_ok);
    	addXMLRPCPasswordHandler(password, passwordOkIV);
    	ImageView passwordConfirmOkIV = (ImageView) findViewById(R.id.setup_confirm_password_ok);
    	addXMLRPCConfirmPasswordHandler(password, passwordConfirm, passwordConfirmOkIV);
    	errorMessage = (TextView) findViewById(R.id.setup_error);
	}
	
	@Override
	protected void onDestroy() {
		inAppPurchaseHelper.destroy();
		super.onDestroy();
	}
	
	@Override
	public void onServiceAvailableForQueries() {
		inAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
		inAppPurchaseHelper.getPurchasedItemsAsync();
	}

	@Override
	public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {
		purchasableItemsLayout.removeAllViews();
		
		for (Purchasable item : items) {
			View layout = LayoutInflater.from(this).inflate(R.layout.in_app_purchasable, purchasableItemsLayout);
			TextView text = (TextView) layout.findViewById(R.id.text);
			text.setText("Buy account (" + item.getPrice() + ")");
			ImageView image = (ImageView) layout.findViewById(R.id.image);
			image.setTag(item);
			image.setOnClickListener(this);
			
			buyItemButton = image;
			buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
		}
	}

	@Override
	public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {
		purchasedItems = items;
		for (Purchasable purchasedItem : purchasedItems) {
			Log.d("[In-app purchase] Found already bought item, expires " + purchasedItem.getExpireDate());
		}
	}

	@Override
	public void onPurchasedItemConfirmationQueryFinished(Purchasable item) {
		if (item != null) {
			Log.d("[In-app purchase] Item bought, expires " + item.getExpireDate());
		}
	}

	@Override
	public void onClick(View v) {
		Purchasable item = (Purchasable) v.getTag();
		inAppPurchaseHelper.purchaseItemAsync(item.getId(), getUsername());
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		inAppPurchaseHelper.parseAndVerifyPurchaseItemResultAsync(requestCode, resultCode, data, getUsername(), password.getText().toString());
	}
	
	private String getUsername() {
		String username = this.username.getText().toString();
		LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
		username = lpc.normalizePhoneNumber(username);
		return username.toLowerCase(Locale.getDefault());
	}
	
	private boolean isUsernameCorrect(String username) {
		LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
		return lpc.isPhoneNumber(username);
	}
	
	private void isUsernameRegistred(String username, final ImageView icon) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				errorMessage.setText(R.string.wizard_server_unavailable);
				usernameOk = false;
				icon.setImageResource(R.drawable.wizard_notok);
				buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
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
						buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
    					errorMessage.setText("");
    					usernameOk = true;
    					icon.setImageResource(R.drawable.wizard_ok);
    					buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
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
	
	private boolean isPasswordCorrect(String password) {
		return password.length() >= 6;
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
					LinphoneProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
					username = lpc.normalizePhoneNumber(username);
					isUsernameRegistred(username, icon);
				} else {
					errorMessage.setText(R.string.wizard_username_incorrect);
					icon.setImageResource(R.drawable.wizard_notok);
				}
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
				buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
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
				buyItemButton.setEnabled(usernameOk && passwordOk && confirmPasswordOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}
}
