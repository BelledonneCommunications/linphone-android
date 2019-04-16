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
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

public class InAppPurchaseActivity extends Activity
        implements InAppPurchaseListener, OnClickListener {
    private static InAppPurchaseActivity sInstance;

    private InAppPurchaseHelper mInAppPurchaseHelper;
    private ImageView mCancel, mBack;
    private ProgressBar mInProgress;

    private List<Purchasable> mPurchasedItems;
    private Fragment mFragment;
    private final Handler mHandler = new Handler();

    public static InAppPurchaseActivity instance() {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInAppPurchaseHelper = new InAppPurchaseHelper(this, this);
        setContentView(R.layout.in_app);

        mInProgress = findViewById(R.id.purchaseItemsFetchInProgress);
        mInProgress.setVisibility(View.VISIBLE);

        mBack = findViewById(R.id.back);
        mBack.setOnClickListener(this);
        mBack.setVisibility(View.INVISIBLE);
        mCancel = findViewById(R.id.cancel);
        mCancel.setOnClickListener(this);

        sInstance = this;
    }

    private void changeFragment(Fragment newFragment) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.commitAllowingStateLoss();
    }

    private void displayInappList() {
        mFragment = new InAppPurchaseListFragment();
        changeFragment(mFragment);
    }

    public void displayPurchase(Purchasable item) {
        Bundle extra = new Bundle();
        extra.putString("item_id", item.getId());
        mFragment = new InAppPurchaseFragment();
        mFragment.setArguments(extra);
        changeFragment(mFragment);
    }

    public void buyInapp(String username, Purchasable item) {
        LinphonePreferences.instance().setInAppPurchasedItem(item);
        mInAppPurchaseHelper.purchaseItemAsync(item.getId(), username);
    }

    public String getGmailAccount() {
        return mInAppPurchaseHelper.getGmailAccount();
    }

    @Override
    protected void onDestroy() {
        sInstance = null;
        mInAppPurchaseHelper.destroy();
        super.onDestroy();
    }

    public List<Purchasable> getPurchasedItems() {

        if (mPurchasedItems == null || mPurchasedItems.size() == 0) {
            Log.w("nul");
        }
        return mPurchasedItems;
    }

    public Purchasable getPurchasedItem(String id) {
        for (Purchasable item : mPurchasedItems) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void onServiceAvailableForQueries() {
        // email.setText(mInAppPurchaseHelper.getGmailAccount());
        // email.setEnabled(false);

        // mInAppPurchaseHelper.getPurchasedItemsAsync();
        mInAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
    }

    @Override
    public void onAvailableItemsForPurchaseQueryFinished(ArrayList<Purchasable> items) {
        // purchasableItemsLayout.removeAllViews();
        mInProgress.setVisibility(View.GONE);
        mPurchasedItems = new ArrayList<>();
        mPurchasedItems.addAll(items);
        displayInappList();
    }

    @Override
    public void onPurchasedItemsQueryFinished(ArrayList<Purchasable> items) {
        mPurchasedItems = items;

        if (items == null || items.size() == 0) {
            mInAppPurchaseHelper.getAvailableItemsForPurchaseAsync();
        } else {
            for (Purchasable purchasedItem : mPurchasedItems) {
                Log.d(
                        "[In-app purchase] Found already bought item, expires "
                                + purchasedItem.getExpireDate());
                // displayRecoverAccountButton(purchasedItem);
            }
        }
    }

    @Override
    public void onPurchasedItemConfirmationQueryFinished(boolean success) {
        if (success) {
            XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();

            Purchasable item = LinphonePreferences.instance().getInAppPurchasedItem();

            xmlRpcHelper.updateAccountExpireAsync(
                    new XmlRpcListenerBase() {
                        @Override
                        public void onAccountExpireUpdated() {
                            // TODO
                        }
                    },
                    LinphonePreferences.instance().getAccountUsername(0),
                    LinphonePreferences.instance().getAccountHa1(0),
                    getString(R.string.default_domain),
                    item.getPayload(),
                    item.getPayloadSignature());
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
        mInAppPurchaseHelper.parseAndVerifyPurchaseItemResultAsync(requestCode, resultCode, data);
    }

    @Override
    public void onRecoverAccountSuccessful() {}

    @Override
    public void onError(final String error) {
        Log.e(error);
        mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mInProgress.setVisibility(View.GONE);
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
