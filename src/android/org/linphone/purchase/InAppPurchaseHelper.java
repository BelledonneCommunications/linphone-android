package org.linphone.purchase;
/*
InAppPurchaseHelper.java
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
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphonePreferences;
import org.linphone.mediastream.Log;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Patterns;

import com.android.vending.billing.IInAppBillingService;

public class InAppPurchaseHelper {
	public static final int API_VERSION = 3;
	public static final int ACTIVITY_RESULT_CODE_PURCHASE_ITEM = 11089;

    public static final String SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
    public static final String SKU_DETAILS_LIST = "DETAILS_LIST";
    public static final String SKU_DETAILS_PRODUCT_ID = "productId";
    public static final String SKU_DETAILS_PRICE = "price";
    public static final String SKU_DETAILS_TITLE = "title";
    public static final String SKU_DETAILS_DESC = "description";

    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    public static final int RESPONSE_RESULT_OK = 0;
    public static final int RESULT_USER_CANCELED = 1;
    public static final int RESULT_SERVICE_UNAVAILABLE = 2;
    public static final int RESULT_BILLING_UNAVAILABLE = 3;
    public static final int RESULT_ITEM_UNAVAILABLE = 4;
    public static final int RESULT_DEVELOPER_ERROR = 5;
    public static final int RESULT_ERROR = 6;
    public static final int RESULT_ITEM_ALREADY_OWNED = 7;
    public static final int RESULT_ITEM_NOT_OWNED = 8;

    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    public static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
    public static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    public static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    public static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    public static final String RESPONSE_INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    public static final String PURCHASE_DETAILS_PRODUCT_ID = "productId";
    public static final String PURCHASE_DETAILS_ORDER_ID = "orderId";
    public static final String PURCHASE_DETAILS_AUTO_RENEWING = "autoRenewing";
    public static final String PURCHASE_DETAILS_PURCHASE_TIME = "purchaseTime";
    public static final String PURCHASE_DETAILS_PURCHASE_STATE = "purchaseState";
    public static final String PURCHASE_DETAILS_PAYLOAD = "developerPayload";
    public static final String PURCHASE_DETAILS_PURCHASE_TOKEN = "purchaseToken";

    public static final String CLIENT_ERROR_SUBSCRIPTION_PURCHASE_NOT_AVAILABLE = "SUBSCRIPTION_PURCHASE_NOT_AVAILABLE";
    public static final String CLIENT_ERROR_BIND_TO_BILLING_SERVICE_FAILED = "BIND_TO_BILLING_SERVICE_FAILED";
    public static final String CLIENT_ERROR_BILLING_SERVICE_UNAVAILABLE = "BILLING_SERVICE_UNAVAILABLE";

	private Context mContext;
	private InAppPurchaseListener mListener;
	private IInAppBillingService mService;
	private ServiceConnection mServiceConn;
	private Handler mHandler = new Handler();
	private String mGmailAccount;

	private String responseCodeToErrorMessage(int responseCode) {
		switch (responseCode) {
		case RESULT_USER_CANCELED:
			return "BILLING_RESPONSE_RESULT_USER_CANCELED";
		case RESULT_SERVICE_UNAVAILABLE:
			return "BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE";
		case RESULT_BILLING_UNAVAILABLE:
			return "BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE";
		case RESULT_ITEM_UNAVAILABLE:
			return "BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE";
		case RESULT_DEVELOPER_ERROR:
			return "BILLING_RESPONSE_RESULT_DEVELOPER_ERROR";
		case RESULT_ERROR:
			return "BILLING_RESPONSE_RESULT_ERROR";
		case RESULT_ITEM_ALREADY_OWNED:
			return "BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED";
		case RESULT_ITEM_NOT_OWNED:
			return "BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED";
		}
		return "UNKNOWN_RESPONSE_CODE";
	}

	public InAppPurchaseHelper(Activity context, InAppPurchaseListener listener) {
		mContext = context;
		mListener = listener;
		mGmailAccount = getGmailAccount();


		Log.d("[In-app purchase] creating InAppPurchaseHelper for context "+context.getLocalClassName());

		mServiceConn = new ServiceConnection() {
		   @Override
		   public void onServiceDisconnected(ComponentName name) {
			   Log.d("[In-app purchase] onServiceDisconnected!");
		       mService = null;
		   }

		   @Override
		   public void onServiceConnected(ComponentName name, IBinder service) {
			   Log.d("[In-app purchase] onServiceConnected!");
			   mService = IInAppBillingService.Stub.asInterface(service);
		       String packageName = mContext.getPackageName();
		       try {
		    	   int response = mService.isBillingSupported(API_VERSION, packageName, ITEM_TYPE_SUBS);
		    	   if (response != RESPONSE_RESULT_OK || mGmailAccount == null) {
		    		   Log.e("[In-app purchase] Error: Subscriptions aren't supported!");
		    		   mListener.onError(CLIENT_ERROR_SUBSCRIPTION_PURCHASE_NOT_AVAILABLE);
		    	   } else {
				       mListener.onServiceAvailableForQueries();
		    	   }
		       } catch (RemoteException e) {
		    	   Log.e(e);
		       }
		   }
		};

		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
        if (!mContext.getPackageManager().queryIntentServices(serviceIntent, 0).isEmpty()) {
            boolean ok = mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
            if (!ok) {
            	Log.e("[In-app purchase] Error: Bind service failed");
    		   mListener.onError(CLIENT_ERROR_BIND_TO_BILLING_SERVICE_FAILED);
            }
        } else {
        	Log.e("[In-app purchase] Error: Billing service unavailable on device.");
    		mListener.onError(CLIENT_ERROR_BILLING_SERVICE_UNAVAILABLE);
        }
	}

	private ArrayList<Purchasable> getAvailableItemsForPurchase() {
		ArrayList<Purchasable> products = new ArrayList<Purchasable>();
		ArrayList<String> skuList = LinphonePreferences.instance().getInAppPurchasables();
		Bundle querySkus = new Bundle();
		querySkus.putStringArrayList(SKU_DETAILS_ITEM_LIST, skuList);

		Bundle skuDetails = null;
		try {
			skuDetails = mService.getSkuDetails(API_VERSION, mContext.getPackageName(), ITEM_TYPE_SUBS, querySkus);
		} catch (RemoteException e) {
			Log.e(e);
		}

		if (skuDetails != null) {
			int response = skuDetails.getInt(RESPONSE_CODE);
			if (response == RESPONSE_RESULT_OK) {
				ArrayList<String> responseList = skuDetails.getStringArrayList(SKU_DETAILS_LIST);
				for (String thisResponse : responseList) {
					try {
						JSONObject object = new JSONObject(thisResponse);
						String id = object.getString(SKU_DETAILS_PRODUCT_ID);
						String price = object.getString(SKU_DETAILS_PRICE);
						String title = object.getString(SKU_DETAILS_TITLE);
						String desc = object.getString(SKU_DETAILS_DESC);

						Purchasable purchasable = new Purchasable(id).setTitle(title).setDescription(desc).setPrice(price);
						Log.w("Purchasable item " + purchasable.getDescription());
						products.add(purchasable);
					} catch (JSONException e) {
						Log.e(e);
					}
				}
			} else {
				Log.e("[In-app purchase] Error: responde code is not ok: " + responseCodeToErrorMessage(response));
	    		mListener.onError(responseCodeToErrorMessage(response));
			}
		}

		return products;
	}

	public void getAvailableItemsForPurchaseAsync() {
		new Thread(new Runnable() {
            public void run() {
            	final ArrayList<Purchasable> items = getAvailableItemsForPurchase();
            	if (mHandler != null && mListener != null) {
            		mHandler.post(new Runnable() {
                        public void run() {
                        	mListener.onAvailableItemsForPurchaseQueryFinished(items);
                        }
                    });
            	}
            }
		}).start();
	}

	public void getPurchasedItemsAsync() {
		new Thread(new Runnable() {
            public void run() {

            	final ArrayList<Purchasable> items = new ArrayList<Purchasable>();
            	String continuationToken = null;
        		do {
        			Bundle purchasedItems = null;
        			try {
        				purchasedItems = mService.getPurchases(API_VERSION, mContext.getPackageName(), ITEM_TYPE_SUBS, continuationToken);
        			} catch (RemoteException e) {
        				Log.e(e);
        			}

        			if (purchasedItems != null) {
        				int response = purchasedItems.getInt(RESPONSE_CODE);
        				if (response == RESPONSE_RESULT_OK) {
        					ArrayList<String>  purchaseDataList = purchasedItems.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
        					ArrayList<String>  signatureList = purchasedItems.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);
        					continuationToken = purchasedItems.getString(RESPONSE_INAPP_CONTINUATION_TOKEN);

				   			for (int i = 0; i < purchaseDataList.size(); ++i) {
				   				String purchaseData = purchaseDataList.get(i);
    				   			String signature = signatureList.get(i);
    							Log.d("[In-app purchase] " + purchaseData);

    				   			Purchasable item = verifySignature(purchaseData, signature);
    				   			if (item != null) {
    				   				items.add(item);
    				   			}
    				   		}
        				} else {
        					Log.e("[In-app purchase] Error: responde code is not ok: " + responseCodeToErrorMessage(response));
        		    		mListener.onError(responseCodeToErrorMessage(response));
        				}
        			}
        		} while (continuationToken != null);

            	if (mHandler != null && mListener != null) {
            		mHandler.post(new Runnable() {
                        public void run() {
                        	mListener.onPurchasedItemsQueryFinished(items);
                        }
                    });
            	}
            }
		}).start();
	}

	public void parseAndVerifyPurchaseItemResultAsync(int requestCode, int resultCode, Intent data) {
		if (requestCode == ACTIVITY_RESULT_CODE_PURCHASE_ITEM) {
			int responseCode = data.getIntExtra(RESPONSE_CODE, 0);

			if (resultCode == Activity.RESULT_OK && responseCode == RESPONSE_RESULT_OK) {
				String payload = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
				String signature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

				Purchasable item = LinphonePreferences.instance().getInAppPurchasedItem();
				item.setPayloadAndSignature(payload, signature);
				LinphonePreferences.instance().setInAppPurchasedItem(item);

				XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();
				xmlRpcHelper.verifySignatureAsync(new XmlRpcListenerBase() {
					@Override
					public void onSignatureVerified(boolean success) {
						mListener.onPurchasedItemConfirmationQueryFinished(success);
					}
				}, payload, signature);
			}
		}
	}

	private void purchaseItem(String productId, String sipIdentity) {
		Bundle buyIntentBundle = null;
		try {
			buyIntentBundle = mService.getBuyIntent(API_VERSION, mContext.getPackageName(), productId, ITEM_TYPE_SUBS, sipIdentity);
		} catch (RemoteException e) {
			Log.e(e);
		}

		if (buyIntentBundle != null) {
			PendingIntent pendingIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
			if (pendingIntent != null) {
				try {
					((Activity) mContext).startIntentSenderForResult(pendingIntent.getIntentSender(), ACTIVITY_RESULT_CODE_PURCHASE_ITEM, new Intent(), 0, 0, 0);
				} catch (SendIntentException e) {
					Log.e(e);
				}
			}
		}
	}

	public void purchaseItemAsync(final String productId, final String sipIdentity) {
		new Thread(new Runnable() {
            public void run() {
            	purchaseItem(productId, sipIdentity);
            }
		}).start();
	}

	public void destroy() {
		mContext.unbindService(mServiceConn);
	}

	public String getGmailAccount() {
		Account[] accounts = AccountManager.get(mContext).getAccountsByType("com.google");

	    for (Account account: accounts) {
	    	if (isEmailCorrect(account.name)) {
	            String possibleEmail = account.name;
	            return possibleEmail;
	        }
	    }

	    return null;
	}

	private boolean isEmailCorrect(String email) {
    	Pattern emailPattern = Patterns.EMAIL_ADDRESS;
    	return emailPattern.matcher(email).matches();
	}

	private Purchasable verifySignature(String payload, String signature) {
		// TODO FIXME rework to be async
		/*XmlRpcHelper helper = new XmlRpcHelper();
		if (helper.verifySignature(payload, signature)) {
			try {
				JSONObject json = new JSONObject(payload);
				String productId = json.getString(PURCHASE_DETAILS_PRODUCT_ID);
				Purchasable item = new Purchasable(productId);
				item.setPayloadAndSignature(payload, signature);
				return item;
			} catch (JSONException e) {
				Log.e(e);
			}
		}*/
		return null;
	}

	interface VerifiedSignatureListener {
		void onParsedAndVerifiedSignatureQueryFinished(Purchasable item);
	}
}
