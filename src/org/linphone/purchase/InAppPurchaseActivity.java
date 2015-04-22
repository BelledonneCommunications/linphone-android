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

import org.linphone.R;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class InAppPurchaseActivity extends Activity implements InAppPurchaseListener, OnClickListener {
	private InAppPurchaseHelper inAppPurchaseHelper;
	private LinearLayout purchasableItemsLayout;
	private ArrayList<Purchasable> purchasedItems;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		inAppPurchaseHelper = new InAppPurchaseHelper(this, this);
		
		setContentView(R.layout.in_app_store);
		purchasableItemsLayout = (LinearLayout) findViewById(R.id.purchasable_items);
	}
	
	@Override
	protected void onDestroy() {
		inAppPurchaseHelper.destroy();
		super.onDestroy();
	}
	
	@Override
	public void onServiceAvailableForQueries() {
		inAppPurchaseHelper.getPurchasedItemsAsync();
	}

	@Override
	public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {
		purchasableItemsLayout.removeAllViews();
		
		for (Purchasable item : items) {
			View layout = LayoutInflater.from(this).inflate(R.layout.in_app_purchasable, purchasableItemsLayout);
			TextView text = (TextView) layout.findViewById(R.id.text);
			text.setText(item.getTitle() + " " + item.getPrice());
			ImageView image = (ImageView) layout.findViewById(R.id.image);
			image.setTag(item);
			image.setOnClickListener(this);
			
			for (Purchasable purchasedItem : purchasedItems) {
				Log.d("[In-app purchase] Found already bought item");
				if (purchasedItem.getId().equals(item.getId())) {
					image.setEnabled(false);
					text.setEnabled(false);
				}
			}
		}
	}

	@Override
	public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {
		purchasedItems = items;
		inAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
	}

	@Override
	public void onPurchasedItemConfirmationQueryFinished(Purchasable item) {
		
	}

	@Override
	public void onClick(View v) {
		Purchasable item = (Purchasable) v.getTag();
		inAppPurchaseHelper.purchaseItemAsync(item.getId(), "sylvain@sip.linphone.org");
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		inAppPurchaseHelper.parseAndVerifyPurchaseItemResultAsync(requestCode, resultCode, data);
	}
}
