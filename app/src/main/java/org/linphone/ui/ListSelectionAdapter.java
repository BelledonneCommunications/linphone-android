/*
ListSelectionAdapter.java
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

package org.linphone.ui;

import android.widget.BaseAdapter;
import android.widget.CompoundButton;

import java.util.ArrayList;
import java.util.List;

public abstract class ListSelectionAdapter extends BaseAdapter {
    private ListSelectionHelper mListHelper;
    private List<Integer> mSelectedItems;
    private boolean mIsEditionEnabled;

    private CompoundButton.OnCheckedChangeListener mDeleteCheckboxListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            Integer position = (Integer) compoundButton.getTag();
            if (checked) {
                mSelectedItems.add(position);
            } else {
                mSelectedItems.remove(position);
            }
            mListHelper.updateSelectionButtons(mSelectedItems.size() == 0, mSelectedItems.size() == getCount());
        }
    };

    public ListSelectionAdapter(ListSelectionHelper helper) {
        mListHelper = helper;
        mSelectedItems = new ArrayList<>();
    }

    public boolean isEditionEnabled() {
        return mIsEditionEnabled;
    }

    public void selectAll() {
        for (Integer i = 0; i < getCount(); i++) {
            mSelectedItems.add(i);
        }
        mListHelper.updateSelectionButtons(false, true);
        notifyDataSetInvalidated();
    }

    public void deselectAll() {
        mSelectedItems.clear();
        mListHelper.updateSelectionButtons(true, false);
        notifyDataSetInvalidated();
    }

    public void enableEdition(boolean enable) {
        mIsEditionEnabled = enable;
        notifyDataSetInvalidated();
        mSelectedItems.clear();
    }

    public List<Integer> getSelectedItemsPosition() {
        return mSelectedItems;
    }
}
