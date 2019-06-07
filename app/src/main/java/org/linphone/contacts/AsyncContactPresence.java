package org.linphone.contacts;

import android.os.AsyncTask;

public class AsyncContactPresence extends AsyncTask<Void, AndroidContact, Void> {

    private LinphoneContact linphoneContact;

    public AsyncContactPresence(LinphoneContact linphoneContact) {
        this.linphoneContact = linphoneContact;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        linphoneContact.updateNativeContactWithPresenceInfo(linphoneContact);
        return null;
    }
}
