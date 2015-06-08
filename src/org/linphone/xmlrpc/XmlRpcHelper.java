package org.linphone.xmlrpc;

import java.net.MalformedURLException;
import java.net.URL;

import org.linphone.LinphonePreferences;
import org.linphone.mediastream.Log;

import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;

public class XmlRpcHelper {
    public static final String OS = "google";
    public static final String SERVER_ERROR_INVALID_ACCOUNT = "ERROR_INVALID_ACCOUNT";
    public static final String SERVER_ERROR_PURCHASE_CANCELLED = "ERROR_PURCHASE_CANCELLED";
    public static final String SERVER_ERROR_RECEIPT_PARSING_FAILED = "ERROR_RECEIPT_PARSING_FAILED";
    public static final String SERVER_ERROR_UID_ALREADY_IN_USE = "ERROR_UID_ALREADY_IN_USE";
    public static final String SERVER_ERROR_SIGNATURE_VERIFICATION_FAILED = "ERROR_SIGNATURE_VERIFICATION_FAILED";
    public static final String SERVER_ERROR_ACCOUNT_ALREADY_EXISTS = "ERROR_ACCOUNT_ALREADY_EXISTS";
    public static final String SERVER_ERROR_UNKNOWN_ERROR = "ERROR_UNKNOWN_ERROR";
    
    public static final String CLIENT_ERROR_INVALID_SERVER_URL = "INVALID_SERVER_URL";
    public static final String CLIENT_ERROR_SERVER_NOT_REACHABLE = "SERVER_NOT_REACHABLE";
    
    private XMLRPCClient mXmlRpcClient;
    
    public XmlRpcHelper() {
    	try {
    		mXmlRpcClient = new XMLRPCClient(new URL(LinphonePreferences.instance().getInAppPurchaseValidatingServerUrl()));
		} catch (MalformedURLException e) {
			Log.e(e);
		}
    }
	
	public void createAccountAsync(final XmlRpcListener listener, String gmailAccount, String username, String payload, String signature, String email, String password) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("createAccountAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountCreated(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "create_account_from_in_app_purchase", gmailAccount, username, payload, signature, OS, email, password == null ? "" : password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String createAccount(String gmailAccount, String username, String payload, String signature, String email, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("create_account_from_in_app_purchase", gmailAccount, username, payload, signature, OS, email, password == null ? "" : password);
				String result = (String)object;
				Log.d("createAccount: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void getAccountExpireAsync(final XmlRpcListener listener, String gmailAccount, String payload, String signature) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("getAccountExpireAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountExpireFetched(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "get_expiration_date", gmailAccount, payload, signature, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String getAccountExpire(String gmailAccount, String payload, String signature) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("get_expiration_date", gmailAccount, payload, signature, OS);
				String result = (String)object;
				Log.d("getAccountExpire: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void getAccountExpireAsync(final XmlRpcListener listener, String username, String password) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("getAccountExpireAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountExpireFetched(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "get_expiration_for_account", username, password, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String getAccountExpire(String username, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("get_expiration_for_account", username, password, OS);
				String result = (String)object;
				Log.d("getAccountExpire: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void activateAccountAsync(final XmlRpcListener listener, String gmailAccount, String username, String payload, String signature) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("activateAccountAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountActivated(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "activate_account", gmailAccount, username, payload, signature, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String activateAccount(String gmailAccount, String username, String payload, String signature) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("activate_account", gmailAccount, username, payload, signature, OS);
				String result = (String)object;
				Log.d("activateAccount: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void isAccountActivatedAsync(final XmlRpcListener listener, String username) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("isAccountActivatedAsync: " + result);
					
					if ("OK".equals(result)) {
						listener.onAccountActivatedFetched(true);
					} else if (!"ERROR_ACCOUNT_NOT_ACTIVATED".equals(result)) {
						Log.e(result);
					}
					listener.onAccountActivatedFetched(false);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "check_account_activated", username);
		}
	}
	
	public boolean isAccountActivated(String username) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("check_account_activated", username);
				String result = (String)object;
				Log.d("isAccountActivated: " + result);
				
				if ("OK".equals(result)) {
					return true;
				} else if (!"ERROR_ACCOUNT_NOT_ACTIVATED".equals(result)) {
					Log.e(result);
				}
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return false;
	}
	
	public void isTrialAccountAsync(final XmlRpcListener listener, String username, String password) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("isTrialAccountAsync: " + result);
					
					if (!"ERROR_TOKEN_NOT_FOUND".equals(result) && !"OK".equals(result)) {
						listener.onError(result);
					}
					listener.onAccountFetched("ERROR_TOKEN_NOT_FOUND".equals(result));
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "is_account_paid", username, password, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public boolean isTrialAccount(String username, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("is_account_paid", username, password, OS);
				String result = (String)object;
				Log.d("isTrialAccount: " + result);
				
				return "ERROR_TOKEN_NOT_FOUND".equals(result);
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return false;
	}
	
	public void isAccountAsync(final XmlRpcListener listener, String username) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("isAccountAsync: " + result);
					
					if ("OK".equals(result)) {
						listener.onAccountFetched(true);
					} else if (!"ERROR_ACCOUNT_DOESNT_EXIST".equals(result)) {
						Log.e(result);
					}
					listener.onAccountFetched(false);
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "check_account_activated", username);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public boolean isAccount(String username) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("check_account_activated", username);
				String result = (String)object;
				Log.d("isAccount: " + result);
				
				if ("OK".equals(result)) {
					return true;
				} else if (!"ERROR_ACCOUNT_DOESNT_EXIST".equals(result)) {
					Log.e(result);
				}
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return false;
	}
	
	public void changeAccountEmailAsync(final XmlRpcListener listener, String username, String password, String newEmail) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("changeAccountEmailAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountEmailChanged(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "change_email", username, password, newEmail, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String changeAccountEmail(String username, String password, String newEmail) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("change_email", username, password, newEmail, OS);
				String result = (String)object;
				Log.d("changeAccountEmail: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void changeAccountPasswordAsync(final XmlRpcListener listener, String username, String oldPassword, String newPassword) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("changeAccountPasswordAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onAccountPasswordChanged(result);
						return;
					}
					
					listener.onAccountPasswordChanged(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "change_password", username, oldPassword, newPassword, OS);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String changeAccountPassword(String username, String oldPassword, String newPassword) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("change_password", username, oldPassword, newPassword, OS);
				String result = (String)object;
				Log.d("changeAccountPassword: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void sendRecoverPasswordLinkByEmailAsync(final XmlRpcListener listener, String usernameOrEmail) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("sendRecoverPasswordLinkByEmailAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onRecoverPasswordLinkSent(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "send_reset_account_password_email", usernameOrEmail);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String sendRecoverPasswordLinkByEmail(String usernameOrEmail) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("send_reset_account_password_email", usernameOrEmail);
				String result = (String)object;
				Log.d("sendRecoverPasswordLinkByEmail: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
	
	public void sendActivateAccountLinkByEmailAsync(final XmlRpcListener listener, String usernameOrEmail) {
		if (mXmlRpcClient != null) {
			mXmlRpcClient.callAsync(new XMLRPCCallback() {
				@Override
				public void onServerError(long id, XMLRPCServerException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
				
				@Override
				public void onResponse(long id, Object object) {
					String result = (String)object;
					Log.d("sendActivateAccountLinkByEmailAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onActivateAccountLinkSent(result);
			    	return;
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "resend_activation_email", usernameOrEmail);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String sendActivateAccountLinkByEmail(String usernameOrEmail) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("resend_activation_email", usernameOrEmail);
				String result = (String)object;
				Log.d("sendActivateAccountLinkByEmail: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return null;
				}
				return result;
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return null;
	}
}
