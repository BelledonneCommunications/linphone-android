package org.linphone.utils;

/*
SelectableAdapter.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.util.SparseBooleanArray;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public abstract class SelectableAdapter<VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {
    private final SparseBooleanArray mSelectedItems;
    private boolean mIsEditionEnabled = false;
    private final SelectableHelper mListHelper;

    protected SelectableAdapter(SelectableHelper helper) {
        mSelectedItems = new SparseBooleanArray();
        mListHelper = helper;
    }

    public boolean isEditionEnabled() {
        return mIsEditionEnabled;
    }

    public void enableEdition(boolean set) {
        mIsEditionEnabled = set;

        mSelectedItems.clear();
        notifyDataSetChanged();
    }

    /**
     * Indicates if the item at position position is selected
     *
     * @param position Position of the item to check
     * @return true if the item is selected, false otherwise
     */
    protected boolean isSelected(int position) {
        return getSelectedItems().contains(position);
    }

    /**
     * Toggle the selection status of the item at a given position
     *
     * @param position Position of the item to toggle the selection status for
     */
    public void toggleSelection(int position) {
        if (mSelectedItems.get(position, false)) {
            mSelectedItems.delete(position);
        } else {
            mSelectedItems.put(position, true);
        }
        mListHelper.updateSelectionButtons(
                getSelectedItemCount() == 0, getSelectedItemCount() == getItemCount());

        notifyItemChanged(position);
    }

    /**
     * Count the selected items
     *
     * @return Selected items count
     */
    public int getSelectedItemCount() {
        return mSelectedItems.size();
    }

    /**
     * Indicates the list of selected items
     *
     * @return List of selected items ids
     */
    public List<Integer> getSelectedItems() {
        List<Integer> items = new ArrayList<>(mSelectedItems.size());
        for (int i = 0; i < mSelectedItems.size(); ++i) {
            items.add(mSelectedItems.keyAt(i));
        }
        return items;
    }

    public void selectAll() {
        for (Integer i = 0; i < getItemCount(); i++) {
            mSelectedItems.put(i, true);
            notifyDataSetChanged();
        }
        mListHelper.updateSelectionButtons(false, true);
    }

    public void deselectAll() {
        mSelectedItems.clear();
        mListHelper.updateSelectionButtons(true, false);
        notifyDataSetChanged();
    }

    public abstract Object getItem(int position);
}
