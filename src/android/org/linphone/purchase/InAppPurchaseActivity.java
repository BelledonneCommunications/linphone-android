package org.linphone.purchase;
/*
InAppPurchaseListener.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.mediastream.Log;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class InAppPurchaseActivity extends Activity implements InAppPurchaseListener, OnClickListener {
	private static InAppPurchaseActivity instance;
	private InAppPurchaseHelper inAppPurchaseHelper;
	private ImageView cancel, back;
	private ProgressBar inProgress;

	private List<Purchasable> purchasedItems;
	private Fragment fragment;
	private Handler mHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		inAppPurchaseHelper = new InAppPurchaseHelper(this, this);
		setContentView(R.layout.in_app);

		inProgress = (ProgressBar) findViewById(R.id.purchaseItemsFetchInProgress);
		inProgress.setVisibility(View.VISIBLE);

		back = (ImageView) findViewById(R.id.back);
		back.setOnClickListener(this);
		back.setVisibility(View.INVISIBLE);
		cancel = (ImageView) findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		instance = this;
	}

	private void changeFragment(Fragment newFragment) {
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragment_container, newFragment);
		transaction.commitAllowingStateLoss();
	}

	public void displayInappList() {
		fragment = new InAppPurchaseListFragment();
		changeFragment(fragment);
	}

	public void displayPurchase(Purchasable item) {
		Bundle extra = new Bundle();
		extra.putString("item_id",item.getId());
		fragment = new InAppPurchaseFragment();
		fragment.setArguments(extra);
		changeFragment(fragment);
	}

	public void buyInapp(String username, Purchasable item){
		LinphonePreferences.instance().setInAppPurchasedItem(item);
		inAppPurchaseHelper.purchaseItemAsync(item.getId(), username);
	}


	public String getGmailAccount() {
		return inAppPurchaseHelper.getGmailAccount();
	}


	@Override
	protected void onDestroy() {
		instance = null;
		inAppPurchaseHelper.destroy();
		super.onDestroy();
	}

	public List<Purchasable> getPurchasedItems() {

		if (purchasedItems == null || purchasedItems.size() == 0) {
			Log.w("nul");
		}
		return purchasedItems;
	}

	public Purchasable getPurchasedItem(String id) {
		for(Purchasable item : purchasedItems){
			if (item.getId().equals(id)){
				return item;
			}
		}
		return null;
	}

	public static InAppPurchaseActivity instance() {
		return instance;
	}

	@Override
	public void onServiceAvailableForQueries() {
		//email.setText(inAppPurchaseHelper.getGmailAccount());
		//email.setEnabled(false);

		//inAppPurchaseHelper.getPurchasedItemsAsync();
		inAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
	}

	@Override
	public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {
		//purchasableItemsLayout.removeAllViews();
		inProgress.setVisibility(View.GONE);
		purchasedItems = new ArrayList<Purchasable>();
		for (Purchasable item : items) {
			purchasedItems.add(item);
		}
		displayInappList();
	}

	@Override
	public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {
		purchasedItems = items;

		if (items == null || items.size() == 0) {
			inAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
		} else {
			for (Purchasable purchasedItem : purchasedItems) {
				Log.d("[In-app purchase] Found already bought item, expires " + purchasedItem.getExpireDate());
				//displayRecoverAccountButton(purchasedItem);
			}
		}
	}

	@Override
	public void onPurchasedItemConfirmationQueryFinished(boolean success) {
		if (success) {
			XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();

			Purchasable item = LinphonePreferences.instance().getInAppPurchasedItem();

			xmlRpcHelper.updateAccountExpireAsync(new XmlRpcListenerBase() {
				@Override
				public void onAccountExpireUpdated(String result) {
					//TODO
				}
			}, LinphonePreferences.instance().getAccountUsername(0), LinphonePreferences.instance().getAccountHa1(0), getString(R.string.default_domain), item.getPayload(), item.getPayloadSignature());
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.cancel) {
			finish();
		} else if (id == R.id.back) {
			onBackPressed();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		inAppPurchaseHelper.parseAndVerifyPurchaseItemResultAsync(requestCode, resultCode, data);
	}

	@Override
	public void onRecoverAccountSuccessful(boolean success) {
	}

	@Override
	public void onError(final String error) {
		Log.e(error);
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				inProgress.setVisibility(View.GONE);
				Toast.makeText(InAppPurchaseActivity.this, error, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void onActivateAccountSuccessful(boolean success) {
		if (success) {
			Log.d("[In-app purchase] Account activated");
		}
	}
}
