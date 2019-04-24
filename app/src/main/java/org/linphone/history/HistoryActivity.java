package org.linphone.history;

/*
HistoryActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.main.MainActivity;
import org.linphone.utils.LinphoneUtils;

public class HistoryActivity extends MainActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HistoryFragment fragment = new HistoryFragment();
        changeFragment(fragment, "History", false);
        if (isTablet()) {
            fragment.displayFirstLog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHistorySelected.setVisibility(View.VISIBLE);
        LinphoneManager.getLc().resetMissedCallsCount();
        displayMissedCalls(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isTablet() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void goBack() {
        if (!isTablet()) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate();
                return;
            }
        }
        super.goBack();
    }

    public void showHistoryDetails(Address address) {
        Bundle extras = new Bundle();
        if (address != null) {
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
            String displayName =
                    contact != null
                            ? contact.getFullName()
                            : LinphoneUtils.getAddressDisplayName(address.asStringUriOnly());
            String pictureUri =
                    contact != null && contact.getPhotoUri() != null
                            ? contact.getPhotoUri().toString()
                            : null;

            extras.putString("SipUri", address.asStringUriOnly());
            extras.putString("DisplayName", displayName);
            extras.putString("PictureUri", pictureUri);
        }
        HistoryDetailFragment fragment = new HistoryDetailFragment();
        fragment.setArguments(extras);
        changeFragment(fragment, "History detail", true);
    }

    private void changeFragment(Fragment fragment, String name, boolean isChild) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        if (isChild) {
            transaction.addToBackStack(name);
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        if (isChild && isTablet()) {
            transaction.replace(R.id.fragmentContainer2, fragment, name);
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, name);
        }
        transaction.commitAllowingStateLoss();
        fm.executePendingTransactions();
    }
}
