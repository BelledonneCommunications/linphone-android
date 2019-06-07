package org.linphone.contacts;

import android.os.AsyncTask;

public class AsyncContactPresence extends AsyncTask<AndroidContact, Void, Void> {

    private LinphoneContact linphoneContact;

    public AsyncContactPresence(LinphoneContact linphoneContact) {
        this.linphoneContact = linphoneContact;
    }

    @Override
    protected void onPreExecute() {
        linphoneContact.updateNativeContactWithPresenceInfo(linphoneContact);
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(AndroidContact... androidContacts) {
        return null;
    }
}
