package org.linphone.xmlrpc;

/*
XmlRpcHelper.java
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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.XmlRpcArgType;
import org.linphone.core.XmlRpcRequest;
import org.linphone.core.XmlRpcRequestListener;
import org.linphone.core.XmlRpcSession;
import org.linphone.core.XmlRpcStatus;
import org.linphone.mediastream.Log;

public class XmlRpcHelper {
    public static final String SERVER_ERROR_INVALID_ACCOUNT = "ERROR_INVALID_ACCOUNT";
    public static final String SERVER_RESPONSE_OK = "OK";
    public static final String SERVER_ERROR_INCORRECT_PHONE_NUMBER = "ERROR_PHONE_ISNT_E164";
    public static final String SERVER_ERROR_ACCOUNT_DOESNT_EXIST = "ERROR_ACCOUNT_DOESNT_EXIST";
    public static final String SERVER_ERROR_PURCHASE_CANCELLED = "ERROR_PURCHASE_CANCELLED";
    public static final String SERVER_ERROR_RECEIPT_PARSING_FAILED = "ERROR_RECEIPT_PARSING_FAILED";
    public static final String SERVER_ERROR_UID_ALREADY_IN_USE = "ERROR_UID_ALREADY_IN_USE";
    public static final String SERVER_ERROR_SIGNATURE_VERIFICATION_FAILED = "ERROR_SIGNATURE_VERIFICATION_FAILED";
    public static final String SERVER_ERROR_ACCOUNT_ALREADY_EXISTS = "ERROR_ACCOUNT_ALREADY_EXISTS";
    public static final String SERVER_ERROR_UNKNOWN_ERROR = "ERROR_UNKNOWN_ERROR";

    public static final String CLIENT_ERROR_INVALID_SERVER_URL = "INVALID_SERVER_URL";
    public static final String CLIENT_ERROR_SERVER_NOT_REACHABLE = "SERVER_NOT_REACHABLE";

    private XmlRpcSession xmlRpcSession;

    public XmlRpcHelper() {
        xmlRpcSession = LinphoneManager.getLcIfManagerNotDestroyedOrNull().createXmlRpcSession(LinphonePreferences.instance().getInAppPurchaseValidatingServerUrl());
    }

    public void createAccountAsync(final XmlRpcListener listener, String username, String email, String password) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "create_account");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }
                    listener.onAccountCreated(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(email);
        xmlRpcRequest.addStringArg(password == null ? "" : password);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void getAccountExpireAsync(final XmlRpcListener listener, String username, String password) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "get_account_expiration");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }
                    listener.onAccountExpireFetched(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(password);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void updateAccountExpireAsync(final XmlRpcListener listener, String username, String password, String domain, String payload, String signature) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "update_expiration_date");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }
                    listener.onAccountExpireUpdated(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(password);
        xmlRpcRequest.addStringArg(domain);
        xmlRpcRequest.addStringArg(payload);
        xmlRpcRequest.addStringArg(signature);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void activateAccountAsync(final XmlRpcListener listener, String username, String password) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "activate_account");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }
                    listener.onAccountActivated(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(password);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void isAccountActivatedAsync(final XmlRpcListener listener, String username) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "check_account_activated");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if ("OK".equals(result)) {
                        listener.onAccountActivatedFetched(true);
                        return;
                    } else if (!"ERROR_ACCOUNT_NOT_ACTIVATED".equals(result)) {
                        Log.e(result);
                        listener.onError(result);
                    }
                    listener.onAccountActivatedFetched(false);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void isTrialAccountAsync(final XmlRpcListener listener, String username, String password) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "is_account_trial");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (!"NOK".equals(result) && !"OK".equals(result)) {
                        listener.onError(result);
                    }
                    listener.onTrialAccountFetched("OK".equals(result));
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(password);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void isAccountAsync(final XmlRpcListener listener, String username) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "check_account_activated");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if ("OK".equals(result)) {
                        listener.onAccountFetched(true);
                        return;
                    } else if (!"ERROR_ACCOUNT_DOESNT_EXIST".equals(result)) {
                        Log.e(result);
                        listener.onError(result);
                    }
                    listener.onAccountFetched(false);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void changeAccountEmailAsync(final XmlRpcListener listener, String username, String password, String newEmail) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "change_email");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onAccountEmailChanged(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(password);
        xmlRpcRequest.addStringArg(newEmail);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void changeAccountPasswordAsync(final XmlRpcListener listener, String username, String oldPassword, String newPassword) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "change_password");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onAccountPasswordChanged(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(oldPassword);
        xmlRpcRequest.addStringArg(newPassword);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void changeAccountHashPasswordAsync(final XmlRpcListener listener, String username, String oldPassword, String newPassword) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "change_hash");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onAccountPasswordChanged(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(oldPassword);
        xmlRpcRequest.addStringArg(newPassword);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void sendRecoverPasswordLinkByEmailAsync(final XmlRpcListener listener, String usernameOrEmail) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "send_reset_account_password_email");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onRecoverPasswordLinkSent(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(usernameOrEmail);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void sendActivateAccountLinkByEmailAsync(final XmlRpcListener listener, String usernameOrEmail) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "resend_activation_email");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onActivateAccountLinkSent(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(usernameOrEmail);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void sendUsernameByEmailAsync(final XmlRpcListener listener, String email) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "recover_username_from_email");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onUsernameSent(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(email);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void verifySignatureAsync(final XmlRpcListener listener, String payload, String signature) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "check_payload_signature");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    Log.w(result);
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onSignatureVerified("OK".equals(result));
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(payload);
        xmlRpcRequest.addStringArg(signature);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }

    public void getRemoteProvisioningFilenameAsync(final XmlRpcListener listener, String username, String domain, String password) {
        XmlRpcRequest xmlRpcRequest = xmlRpcSession.createRequest(XmlRpcArgType.String, "get_remote_provisioning_filename");
        xmlRpcRequest.setListener(new XmlRpcRequestListener() {
            @Override
            public void onResponse(XmlRpcRequest request) {
                String result = request.getStringResponse();
                if (request.getStatus() == XmlRpcStatus.Ok) {
                    if (result.startsWith("ERROR_")) {
                        Log.e(result);
                        listener.onError(result);
                        return;
                    }

                    listener.onRemoteProvisioningFilenameSent(result);
                } else if (request.getStatus() == XmlRpcStatus.Failed) {
                    Log.e(result);
                    listener.onError(result);
                }
            }
        });
        xmlRpcRequest.addStringArg(username);
        xmlRpcRequest.addStringArg(domain);
        xmlRpcRequest.addStringArg(password);
        xmlRpcSession.sendRequest(xmlRpcRequest);
    }
}
