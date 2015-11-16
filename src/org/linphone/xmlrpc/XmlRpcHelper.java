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
    
    public XmlRpcHelper(String serverUrl) {
    	try {
			if(serverUrl != null) {
				mXmlRpcClient = new XMLRPCClient(new URL(serverUrl));
			} else {
				mXmlRpcClient = new XMLRPCClient(new URL(LinphonePreferences.instance().getXmlRpcServerUrl()));
			}
		} catch (MalformedURLException e) {
			Log.e(e);
		}
    }
	
	public void createAccountAsync(final XmlRpcListener listener, String username, String email, String password) {
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
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "create_account", username, email, password == null ? "" : password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String createAccount(String username, String email, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("create_account", username, email, password == null ? "" : password);
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
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "get_expiration_for_account", username, password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String getAccountExpire(String username, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("get_expiration_for_account", username, password);
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
	
	public void updateAccountExpireAsync(final XmlRpcListener listener, String username, String password, String payload, String signature) {
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
					Log.d("updateAccountExpireAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountExpireUpdated(result);
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "update_expiration_date", username, password, payload, signature);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String updateAccountExpire(String username, String password, String payload, String signature) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("update_expiration_date", username, password, payload, signature);
				String result = (String)object;
				Log.d("updateAccountExpire: " + result);
				
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
	
	public void activateAccountAsync(final XmlRpcListener listener, String username, String password) {
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
			}, "activate_account", username, password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String activateAccount(String gmailAccount, String username, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("activate_account", username, password);
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
					String result = (String) object;
					Log.d("isAccountActivatedAsync: " + result);

					if ("OK".equals(result)) {
						listener.onAccountActivatedFetched(true);
						return;
					} else if (!"ERROR_ACCOUNT_NOT_ACTIVATED".equals(result)) {
						Log.e(result);
						listener.onError(result);
					}
					listener.onAccountActivatedFetched(false);
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
					
					if (!"NOK".equals(result) && !"OK".equals(result)) {
						listener.onError(result);
					}
					listener.onAccountFetched("OK".equals(result));
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "check_account_trial", username, password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public boolean isTrialAccount(String username, String password) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("check_account_trial", username, password);
				String result = (String)object;
				Log.d("isTrialAccount: " + result);
				
				return "OK".equals(result);
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
						return;
					} else if (!"ERROR_ACCOUNT_DOESNT_EXIST".equals(result)) {
						Log.e(result);
						listener.onError(result);
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
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "change_email", username, password, newEmail);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String changeAccountEmail(String username, String password, String newEmail) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("change_email", username, password, newEmail);
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
						listener.onError(result);
						return;
					}
					
					listener.onAccountPasswordChanged(result);
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "change_password", username, oldPassword, newPassword);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String changeAccountPassword(String username, String oldPassword, String newPassword) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("change_password", username, oldPassword, newPassword);
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
	
	public void changeAccountHashPasswordAsync(final XmlRpcListener listener, String username, String oldPassword, String newPassword) {
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
					Log.d("changeAccountHashPasswordAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onAccountPasswordChanged(result);
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "change_hash", username, oldPassword, newPassword);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String changeAccountHashPassword(String username, String oldPassword, String newPassword) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("change_hash", username, oldPassword, newPassword);
				String result = (String)object;
				Log.d("changeAccountHashPassword: " + result);
				
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
	
	public void sendUsernameByEmailAsync(final XmlRpcListener listener, String email) {
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
					Log.d("sendUsernameByEmailAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onUsernameSent(result);
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "recover_username_from_email", email);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public String sendUsernameByEmail(String email) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("recover_username_from_email", email);
				String result = (String)object;
				Log.d("sendUsernameByEmail: " + result);
				
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
	
	public void verifySignatureAsync(final XmlRpcListener listener, String payload, String signature) {
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
					Log.d("verifySignatureAsync: " + result);
					
					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}
					
					listener.onSignatureVerified("OK".equals(result));
				}
				
				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "verify_payload_signature", payload, signature);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}
	
	public boolean verifySignature(String payload, String signature) {
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("verify_payload_signature", payload, signature);
				String result = (String)object;
				Log.d("verifySignature: " + result);
				
				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return false;
				}
				return "OK".equals(result);
				
			} catch (XMLRPCException e) {
				Log.e(e);
			}
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
		}
		return false;
	}

	public void getRemoteProvisioningFilenameAsync(final XmlRpcListener listener,String username, String domain, String password){
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
					Log.d("getRemoteProvisioningFilenameAsync: " + result);

					if (result.startsWith("ERROR_")) {
						Log.e(result);
						listener.onError(result);
						return;
					}

					listener.onRemoteProvisioningFilenameSent(result);
				}

				@Override
				public void onError(long id, XMLRPCException error) {
					Log.e(error);
					listener.onError(error.toString());
				}
			}, "get_remote_provisioning_filename", username, domain, password);
		} else {
			Log.e(CLIENT_ERROR_INVALID_SERVER_URL);
			listener.onError(CLIENT_ERROR_INVALID_SERVER_URL);
		}
	}

	public String getRemoteProvisioningFilename(String username, String domain, String password){
		if (mXmlRpcClient != null) {
			try {
				Object object = mXmlRpcClient.call("get_remote_provisioning_filename", username, domain, password);
				String result = (String)object;
				Log.d("getRemoteProvisioningFilename:: " + result);

				if (result.startsWith("ERROR_")) {
					Log.e(result);
					return result;
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
