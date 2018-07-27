package org.linphone.contacts;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ContactsListAdapter extends SelectableAdapter<ContactsListAdapter.ViewHolder> implements SectionIndexer {
//public class ContactsListAdapter extends RecyclerView.Adapter<ContactsListAdapter.ViewHolder> implements SectionIndexer {
//	class ContactsListAdapter extends BaseAdapter implements SectionIndexer {

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
        public CheckBox delete;
        public ImageView linphoneFriend;
        public TextView name;
        public LinearLayout separator;
        public TextView separatorText;
        public ImageView contactPicture;
        public TextView organization;
        //public ImageView friendStatus;
        private ClickListener listener;

        private ViewHolder(View view, ClickListener listener) {
            super(view);

            delete = (CheckBox) view.findViewById(R.id.delete);
            linphoneFriend = (ImageView) view.findViewById(R.id.friendLinphone);
            name = (TextView) view.findViewById(R.id.name);
            separator = (LinearLayout) view.findViewById(R.id.separator);
            separatorText = (TextView) view.findViewById(R.id.separator_text);
            contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
            organization = (TextView) view.findViewById(R.id.contactOrganization);
            //friendStatus = (ImageView) view.findViewById(R.id.friendStatus);
            this.listener= listener;
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);

        }

        @Override
        public void onClick(View view) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }

        }

        public boolean onLongClick(View v) {
            if (listener != null) {
                return listener.onItemLongClicked(getAdapterPosition());
            }
            return false;
        }

        public interface ClickListener {
            void onItemClicked(int position);
            boolean onItemLongClicked(int position);
        }

    }

    private List<LinphoneContact> contacts;
    String[] sections;
    ArrayList<String> sectionsList;
    Map<String, Integer> map = new LinkedHashMap<String, Integer>();
    private ViewHolder.ClickListener clickListener;
    private Context mContext;
    private boolean isSearchMode;

    ContactsListAdapter(Context context, List<LinphoneContact> contactsList, ViewHolder.ClickListener clickListener, SelectableHelper helper) {
        super(helper);
        this.mContext=context;
        updateDataSet(contactsList);
        this.clickListener = clickListener;
    }



    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_cell, parent, false);
        return new ViewHolder(v, clickListener);

    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        LinphoneContact contact = (LinphoneContact) getItem(position);

        holder.name.setText(contact.getFullName());

        if (!isSearchMode) {
                String fullName = contact.getFullName();
                if (fullName != null && !fullName.isEmpty()) {
                    holder.separatorText.setText(String.valueOf(fullName.charAt(0)));
                }
        }
        holder.separator.setVisibility(isSearchMode || (!isSearchMode && getPositionForSection(getSectionForPosition(position)) != position ) ? View.GONE:View.VISIBLE);
        holder.linphoneFriend.setVisibility(contact.isInFriendList() ? View.VISIBLE:View.GONE);

        holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
        if (contact.hasPhoto()) {
            LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
        }

        boolean isOrgVisible = mContext.getResources().getBoolean(R.bool.display_contact_organization);
        String org = contact.getOrganization();
        if (org != null && !org.isEmpty() && isOrgVisible) {
            holder.organization.setText(org);
            holder.organization.setVisibility(View.VISIBLE);
        } else {
            holder.organization.setVisibility(View.GONE);
        }

        holder.delete.setVisibility(this.isEditionEnabled() ? View.VISIBLE : View.INVISIBLE);
        holder.delete.setChecked(isSelected(position));

    }
    @Override
    public int getItemCount() {
        return contacts.size();
    }



    public Object getItem(int position) {
        if (position >= getItemCount()) return null;
        return contacts.get(position);
    }
    public boolean isSearchMode(){
        return this.isSearchMode;
    }

    public void setSearchMode(boolean set){
        isSearchMode = set;
    }
    public long getItemId(int position) {
        return position;
    }



    public void updateDataSet(List<LinphoneContact> contactsList) {
        contacts = contactsList;

        map = new LinkedHashMap<String, Integer>();
        String prevLetter = null;
        for (int i = 0; i < contacts.size(); i++) {
            LinphoneContact contact = contacts.get(i);
            String fullName = contact.getFullName();
            if (fullName == null || fullName.isEmpty()) {
                continue;
            }
            String firstLetter = fullName.substring(0, 1).toUpperCase(Locale.getDefault());
            if (!firstLetter.equals(prevLetter)) {
                prevLetter = firstLetter;
                map.put(firstLetter, i);
            }
        }
        sectionsList = new ArrayList<String>(map.keySet());
        sections = new String[sectionsList.size()];
        sectionsList.toArray(sections);

        notifyDataSetChanged();
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sectionIndex >= sections.length || sectionIndex < 0) {
            return 0;
        }
        return map.get(sections[sectionIndex]);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position >= contacts.size() || position < 0) {
            return 0;
        }
        LinphoneContact contact = contacts.get(position);
        String fullName = contact.getFullName();
        if (fullName == null || fullName.isEmpty()) {
            return 0;
        }
        String letter = fullName.substring(0, 1).toUpperCase(Locale.getDefault());
        return sectionsList.indexOf(letter);
    }
}