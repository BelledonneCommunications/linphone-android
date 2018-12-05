package org.linphone.contacts;

/*
SearchContactsAdapter.java
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.SearchResult;
import org.linphone.views.ContactAvatar;

public class SearchContactsAdapter extends RecyclerView.Adapter<SearchContactViewHolder> {
    @SuppressWarnings("unused")
    private static final String TAG = SearchContactsAdapter.class.getSimpleName();

    private List<ContactAddress> mContacts;
    private List<ContactAddress> mContactsSelected;
    private final ProgressBar mProgressBar;
    private boolean mOnlySipContact = false;
    private SearchContactViewHolder.ClickListener mListener;
    private final boolean mHideSelectionMark;
    private String mPreviousSearch;

    public SearchContactsAdapter(
            List<ContactAddress> contactsList,
            ProgressBar pB,
            SearchContactViewHolder.ClickListener clickListener,
            boolean hideSelectionMark) {
        mHideSelectionMark = hideSelectionMark;
        mListener = clickListener;
        mProgressBar = pB;
        setContactsSelectedList(null);
        setContactsList(contactsList);
        mPreviousSearch = null;
    }

    public List<ContactAddress> getContacts() {
        return mContacts;
    }

    public void setOnlySipContact(boolean enable) {
        mOnlySipContact = enable;
    }

    @NonNull
    @Override
    public SearchContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.search_contact_cell, parent, false);
        return new SearchContactViewHolder(v, mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchContactViewHolder holder, int position) {
        ContactAddress contact = getItem(position);
        final String a =
                (contact.getAddressAsDisplayableString().isEmpty())
                        ? contact.getPhoneNumber()
                        : contact.getAddressAsDisplayableString();
        LinphoneContact c = contact.getContact();

        String address = contact.getAddressAsDisplayableString();
        if (c != null && c.getFullName() != null) {
            if (address == null) address = c.getPresenceModelForUriOrTel(a);
            holder.name.setVisibility(View.VISIBLE);
            holder.name.setText(c.getFullName());
        } else if (contact.getAddress() != null) {
            if (contact.getAddress().getUsername() != null) {
                holder.name.setVisibility(View.VISIBLE);
                holder.name.setText(contact.getAddress().getUsername());
            } else if (contact.getAddress().getDisplayName() != null) {
                holder.name.setVisibility(View.VISIBLE);
                holder.name.setText(contact.getAddress().getDisplayName());
            }
        } else if (address != null) {
            Address tmpAddr = Factory.instance().createAddress(address);
            holder.name.setVisibility(View.VISIBLE);
            holder.name.setText(
                    (tmpAddr.getDisplayName() != null)
                            ? tmpAddr.getDisplayName()
                            : tmpAddr.getUsername());
        } else {
            holder.name.setVisibility(View.GONE);
        }

        if (c != null) {
            if (c.getFullName() == null && c.getFirstName() == null && c.getLastName() == null) {
                c.setFullName(holder.name.getText().toString());
            }
            ContactAvatar.displayAvatar(c, holder.avatarLayout);
            // TODO get if contact has security capabilities
        } else {
            ContactAvatar.displayAvatar(holder.name.getText().toString(), holder.avatarLayout);
        }

        holder.address.setText(a);
        if (holder.linphoneContact != null) {
            if (contact.isLinphoneContact() && c != null && c.isInFriendList() && address != null) {
                holder.linphoneContact.setVisibility(View.VISIBLE);
            } else {
                holder.linphoneContact.setVisibility(View.GONE);
            }
        }
        if (holder.isSelect != null) {
            if (contactIsSelected(contact)) {
                holder.isSelect.setVisibility(View.VISIBLE);
            } else {
                holder.isSelect.setVisibility(View.INVISIBLE);
            }
            if (mHideSelectionMark) {
                holder.isSelect.setVisibility(View.GONE);
            }
        }
    }

    public long getItemId(int position) {
        return position;
    }

    private boolean contactIsSelected(ContactAddress ca) {
        for (ContactAddress c : mContactsSelected) {
            Address addr = c.getAddress();
            if (addr.getUsername() != null && ca.getAddress() != null) {
                if (addr.asStringUriOnly().compareTo(ca.getAddress().asStringUriOnly()) == 0)
                    return true;
            } else {
                if (c.getPhoneNumber() != null && ca.getPhoneNumber() != null) {
                    if (c.getPhoneNumber().compareTo(ca.getPhoneNumber()) == 0) return true;
                }
            }
        }
        return false;
    }

    public List<ContactAddress> getContactsSelectedList() {
        return mContactsSelected;
    }

    public void setContactsSelectedList(List<ContactAddress> contactsList) {
        if (contactsList == null) {
            mContactsSelected = new ArrayList<>();
        } else {
            mContactsSelected = contactsList;
        }
    }

    public List<ContactAddress> getContactsList() {
        List<ContactAddress> list = new ArrayList<>();
        if (ContactsManager.getInstance().hasContacts()) {
            List<LinphoneContact> contacts =
                    mOnlySipContact
                            ? ContactsManager.getInstance().getSIPContacts()
                            : ContactsManager.getInstance().getContacts();
            for (LinphoneContact contact : contacts) {
                for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                    if (!mOnlySipContact
                            || (mOnlySipContact
                                    && (noa.isSIPAddress()
                                            || contact.getPresenceModelForUriOrTel(noa.getValue())
                                                    != null))) {
                        ContactAddress ca = null;
                        if (noa.isSIPAddress()) {
                            Address address = LinphoneManager.getLc().interpretUrl(noa.getValue());
                            if (address != null) {
                                ca =
                                        new ContactAddress(
                                                contact,
                                                address.asString(),
                                                "",
                                                contact.isFriend());
                            }
                        } else {
                            ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
                            String number =
                                    (prx != null)
                                            ? prx.normalizePhoneNumber(noa.getValue())
                                            : noa.getValue();
                            ca = new ContactAddress(contact, "", number, contact.isFriend());
                        }
                        if (ca != null) list.add(ca);
                    }
                }
            }
        }

        for (ContactAddress caS : mContactsSelected) {
            for (ContactAddress ca : list) {
                if (ca.equals(caS)) ca.setSelect(true);
            }
        }
        return list;
    }

    private void setContactsList(List<ContactAddress> contactsList) {
        if (contactsList == null) {
            mContacts = getContactsList();
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        } else {
            mContacts = contactsList;
        }
    }

    private ContactAddress getItem(int position) {
        return mContacts.get(position);
    }

    @Override
    public int getItemCount() {
        return mContacts.size();
    }

    public void searchContacts(String search, RecyclerView resultContactsSearch) {
        List<ContactAddress> result = new ArrayList<>();

        if (mPreviousSearch != null) {
            if (mPreviousSearch.length() > search.length()) {
                ContactsManager.getInstance().getMagicSearch().resetSearchCache();
            }
        }
        mPreviousSearch = search;

        String domain = "";
        ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
        if (prx != null) domain = prx.getDomain();
        SearchResult[] results =
                ContactsManager.getInstance()
                        .getMagicSearch()
                        .getContactListFromFilter(search, mOnlySipContact ? domain : "");
        for (SearchResult sr : results) {
            boolean found = false;
            LinphoneContact contact =
                    ContactsManager.getInstance().findContactFromAddress(sr.getAddress());
            if (contact == null) {
                contact = new LinphoneContact();
                if (sr.getFriend() != null) {
                    contact.setFriend(sr.getFriend());
                    contact.refresh();
                }
            }
            if (sr.getAddress() != null || sr.getPhoneNumber() != null) {
                for (ContactAddress ca : result) {
                    String normalizedPhoneNumber =
                            (ca != null && ca.getPhoneNumber() != null && prx != null)
                                    ? prx.normalizePhoneNumber(ca.getPhoneNumber())
                                    : null;
                    if ((sr.getAddress() != null
                                    && ca.getAddress() != null
                                    && ca.getAddress()
                                            .asStringUriOnly()
                                            .equals(sr.getAddress().asStringUriOnly()))
                            || (sr.getPhoneNumber() != null
                                    && normalizedPhoneNumber != null
                                    && sr.getPhoneNumber().equals(normalizedPhoneNumber))) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                if (LinphoneActivity.instance()
                        .getResources()
                        .getBoolean(R.bool.hide_sip_contacts_without_presence)) {
                    if (contact.getFriend() != null) {
                        for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                            PresenceModel pm =
                                    contact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                            if (pm != null
                                    && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                                result.add(
                                        new ContactAddress(
                                                contact,
                                                (sr.getAddress() != null)
                                                        ? sr.getAddress().asStringUriOnly()
                                                        : "",
                                                sr.getPhoneNumber(),
                                                contact.isFriend()));
                                break;
                            }
                        }
                    }
                } else {
                    result.add(
                            new ContactAddress(
                                    contact,
                                    (sr.getAddress() != null)
                                            ? sr.getAddress().asStringUriOnly()
                                            : "",
                                    sr.getPhoneNumber(),
                                    contact.isFriend()));
                }
            }
        }

        mContacts = result;
        resultContactsSearch.setAdapter(this);
        notifyDataSetChanged();
    }
}
