/*
SearchContactsListAdapter.java
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

package org.linphone.contacts;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.Address;
import org.linphone.core.Factory;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.SearchResult;
import org.linphone.mediastream.Log;

import java.util.ArrayList;
import java.util.List;

public class SearchContactsListAdapter extends RecyclerView.Adapter<SearchContactsListAdapter.ViewHolder> {
    @SuppressWarnings("unused")
    private static final String TAG = SearchContactsListAdapter.class.getSimpleName();

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView name;
        public TextView address;
        public ImageView linphoneContact;
        public ImageView isSelect;
        public ImageView avatar;

        private ClickListener mListener;

        public ViewHolder(View view, ClickListener listener) {
            super(view);
            name = view.findViewById(R.id.contact_name);
            address = view.findViewById(R.id.contact_address);
            linphoneContact = view.findViewById(R.id.contact_linphone);
            isSelect = view.findViewById(R.id.contact_is_select);
            avatar = view.findViewById(R.id.contact_picture);
            mListener = listener;
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mListener != null) {
                mListener.onItemClicked(getAdapterPosition());
            }

        }

        public interface ClickListener {
            void onItemClicked(int position);
        }
    }

    private List<ContactAddress> contacts;
    private List<ContactAddress> contactsSelected;
    private ProgressBar progressBar;
    private boolean mOnlySipContact = false;
    private ViewHolder.ClickListener mListener;

    public List<ContactAddress> getContacts() {
        return contacts;
    }

    public void setOnlySipContact(boolean enable) {
        mOnlySipContact = enable;
    }

    public void setListener(ViewHolder.ClickListener listener) {
        mListener = listener;
    }

    public SearchContactsListAdapter(List<ContactAddress> contactsList, ProgressBar pB, ViewHolder.ClickListener clickListener) {
        mListener = clickListener;
        progressBar = pB;
        setContactsSelectedList(null);
        setContactsList(contactsList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.search_contact_cell, parent, false);
        return new ViewHolder(v, mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactAddress contact = getItem(position);
        final String a = (contact.getAddressAsDisplayableString().isEmpty()) ? contact.getPhoneNumber() : contact.getAddressAsDisplayableString();
        LinphoneContact c = contact.getContact();

        holder.avatar.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
        if (c != null && c.hasPhoto()) {
            LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.avatar, c.getThumbnailUri());
        }

        String address = contact.getAddressAsDisplayableString();
        if (c != null && c.getFullName() != null) {
            if (address == null)
                address = c.getPresenceModelForUriOrTel(a);
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
            holder.name.setText((tmpAddr.getDisplayName() != null) ? tmpAddr.getDisplayName() : tmpAddr.getUsername());
        } else {
            holder.name.setVisibility(View.GONE);
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
        }
    }

    public long getItemId(int position) {
        return position;
    }

    private boolean contactIsSelected(ContactAddress ca) {
        for (ContactAddress c : contactsSelected) {
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

    public void setContactsList(List<ContactAddress> contactsList) {
        if (contactsList == null) {
            contacts = getContactsList();
            if (contacts.size() > 0 && progressBar != null)
                progressBar.setVisibility(View.GONE);
        } else {
            contacts = contactsList;
        }
    }

    public void setContactsSelectedList(List<ContactAddress> contactsList) {
        if (contactsList == null) {
            contactsSelected = new ArrayList<>();
        } else {
            contactsSelected = contactsList;
        }
    }

    public List<ContactAddress> getContactsSelectedList() {
        return contactsSelected;
    }

    public List<ContactAddress> getContactsList() {
        List<ContactAddress> list = new ArrayList<>();
        if (ContactsManager.getInstance().hasContacts()) {
            List<LinphoneContact> contacts = mOnlySipContact ? ContactsManager.getInstance().getSIPContacts() : ContactsManager.getInstance().getContacts();
            for (LinphoneContact contact : contacts) {
                for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                    if (!mOnlySipContact || (mOnlySipContact && (noa.isSIPAddress() || contact.getPresenceModelForUriOrTel(noa.getValue()) != null))) {
                        ContactAddress ca = null;
                        if (noa.isSIPAddress()) {
                            Address address = LinphoneManager.getLc().interpretUrl(noa.getValue());
                            if (address != null) {
                                ca = new ContactAddress(contact, address.asString(), "", contact.isFriend());
                            }
                        } else {
                            ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
                            String number = (prx != null) ? prx.normalizePhoneNumber(noa.getValue()) : noa.getValue();
                            ca = new ContactAddress(contact, "", number, contact.isFriend());
                        }
                        if (ca != null) list.add(ca);
                    }
                }
            }
        }

        for (ContactAddress caS : contactsSelected) {
            for (ContactAddress ca : list) {
                if (ca.equals(caS)) ca.setSelect(true);
            }
        }
        return list;
    }

    public int getCount() {
        return contacts.size();
    }

    public ContactAddress getItem(int position) {
        return contacts.get(position);
    }


    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void searchContacts(String search, RecyclerView resultContactsSearch) {
        List<ContactAddress> result = new ArrayList<>();

        String domain = "";
        ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
        if (prx != null) domain = prx.getDomain();
        SearchResult[] results = ContactsManager.getInstance().getMagicSearch().getContactListFromFilter(search, mOnlySipContact ? domain : "");
        for (SearchResult sr : results) {
            boolean found = false;
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(sr.getAddress());
            if (contact == null) {
                contact = new LinphoneContact();
                if (sr.getFriend() != null) {
                    contact.setFriend(sr.getFriend());
                    contact.refresh();
                }
            }
            if (sr.getAddress() != null || sr.getPhoneNumber() != null) {
                for (ContactAddress ca : result) {
                    String normalizedPhoneNumber = (ca != null && ca.getPhoneNumber() != null && prx != null) ? prx.normalizePhoneNumber(ca.getPhoneNumber()) : null;
                    if ((sr.getAddress() != null && ca.getAddress() != null
                            && ca.getAddress().asStringUriOnly().equals(sr.getAddress().asStringUriOnly()))
                            || (sr.getPhoneNumber() != null && normalizedPhoneNumber != null
                            && sr.getPhoneNumber().equals(normalizedPhoneNumber))) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                if (LinphoneActivity.instance().getResources().getBoolean(R.bool.hide_sip_contacts_without_presence)) {
                    if (contact.getFriend() != null) {
                        for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
                            PresenceModel pm = contact.getFriend().getPresenceModelForUriOrTel(noa.getValue());
                            if (pm != null && pm.getBasicStatus().equals(PresenceBasicStatus.Open)) {
                                result.add(new ContactAddress(contact,
                                        (sr.getAddress() != null) ? sr.getAddress().asStringUriOnly() : "",
                                        sr.getPhoneNumber(),
                                        contact.isFriend()));
                                break;
                            }
                        }
                    }
                } else {
                    result.add(new ContactAddress(contact,
                            (sr.getAddress() != null) ? sr.getAddress().asStringUriOnly() : "",
                            sr.getPhoneNumber(),
                            contact.isFriend()));
                }
            }
        }

        contacts = result;
        resultContactsSearch.setAdapter(this);
        notifyDataSetChanged();
    }
}

