package org.linphone.xmlrpc;

public interface XmlRpcListener {
	public void onError(String error);
	public void onAccountCreated(String result);
	public void onAccountExpireFetched(String result);
	public void onAccountExpireUpdated(String result);
	public void onAccountActivated(String result);
	public void onAccountActivatedFetched(boolean isActivated);
	public void onTrialAccountFetched(boolean isTrial);
	public void onAccountFetched(boolean isExisting);
	public void onAccountEmailChanged(String result);
	public void onAccountPasswordChanged(String result);
	public void onRecoverPasswordLinkSent(String result);
	public void onActivateAccountLinkSent(String result);
	public void onSignatureVerified(boolean success);
	public void onUsernameSent(String result);
	public void onRemoteProvisioningFilenameSent(String result);
}
