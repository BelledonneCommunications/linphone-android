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
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class InAppPurchaseActivity extends Activity implements InAppPurchaseListener, OnClickListener {
	private InAppPurchaseHelper inAppPurchaseHelper;
	private LinearLayout purchasableItemsLayout;
	private ArrayList<Purchasable> purchasedItems;
	private ImageView buyItemButton, recoverAccountButton;
	private Handler mHandler = new Handler();
	
	private EditText username, email;
	private TextView errorMessage;
	private boolean usernameOk = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		inAppPurchaseHelper = new InAppPurchaseHelper(this, this);
		
		setContentView(R.layout.in_app_store);
		purchasableItemsLayout = (LinearLayout) findViewById(R.id.purchasable_items);
		
		username = (EditText) findViewById(R.id.setup_username);
		email = (EditText) findViewById(R.id.setup_email);
    	ImageView usernameOkIV = (ImageView) findViewById(R.id.setup_username_ok);
    	addUsernameHandler(username, usernameOkIV);
    	errorMessage = (TextView) findViewById(R.id.setup_error);
	}
	
	@Override
	protected void onDestroy() {
		inAppPurchaseHelper.destroy();
		super.onDestroy();
	}
	
	@Override
	public void onServiceAvailableForQueries() {
		email.setText(inAppPurchaseHelper.getGmailAccount());
		email.setEnabled(false);
		inAppPurchaseHelper.getPurchasedItemsAsync();
	}

	@Override
	public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {
		purchasableItemsLayout.removeAllViews();
		
		for (Purchasable item : items) {
			displayBuySubscriptionButton(item);
		}
	}

	@Override
	public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {
		purchasedItems = items;
		
		if (items == null || items.size() == 0) {
			inAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
		} else {
			for (Purchasable purchasedItem : purchasedItems) {
				Log.d("[In-app purchase] Found already bought item, expires " + purchasedItem.getExpireDate());
				displayRecoverAccountButton(purchasedItem);
			}
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
		if (v.equals(recoverAccountButton)) {
			inAppPurchaseHelper.recoverAccount(item.getId(), getUsername());
		} else {
			inAppPurchaseHelper.purchaseItemAsync(item.getId(), getUsername());
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		inAppPurchaseHelper.parseAndVerifyPurchaseItemResultAsync(requestCode, resultCode, data, getUsername());
	}

	@Override
	public void onRecoverAccountSuccessful(boolean success) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				recoverAccountButton.setEnabled(false);				
			}
		});
	}

	@Override
	public void onError(final String error) {
		Log.e(error);
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(InAppPurchaseActivity.this, error, Toast.LENGTH_LONG).show();	
			}
		});
	}
	
	private void displayBuySubscriptionButton(Purchasable item) {
		View layout = LayoutInflater.from(this).inflate(R.layout.in_app_purchasable, purchasableItemsLayout);
		TextView text = (TextView) layout.findViewById(R.id.text);
		text.setText("Buy account (" + item.getPrice() + ")");
		ImageView image = (ImageView) layout.findViewById(R.id.image);
		image.setTag(item);
		image.setOnClickListener(this);
		
		buyItemButton = image;
		buyItemButton.setEnabled(usernameOk);
	}
	
	private void displayRecoverAccountButton(Purchasable item) {
		View layout = LayoutInflater.from(this).inflate(R.layout.in_app_purchasable, purchasableItemsLayout);
		TextView text = (TextView) layout.findViewById(R.id.text);
		text.setText("Recover account");
		ImageView image = (ImageView) layout.findViewById(R.id.image);
		image.setTag(item);
		image.setOnClickListener(this);
		
		recoverAccountButton = image;
		recoverAccountButton.setEnabled(usernameOk);
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
	
	private void addUsernameHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				usernameOk = false;
				String username = s.toString();
				if (isUsernameCorrect(username)) {
					usernameOk = true;
					icon.setImageResource(R.drawable.wizard_ok);
					errorMessage.setText("");
				} else {
					errorMessage.setText(R.string.wizard_username_incorrect);
					icon.setImageResource(R.drawable.wizard_notok);
				}
				if (buyItemButton != null) buyItemButton.setEnabled(usernameOk);
				if (recoverAccountButton != null) recoverAccountButton.setEnabled(usernameOk);
			}
		});
	}
}
