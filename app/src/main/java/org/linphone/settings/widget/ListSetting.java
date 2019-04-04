package org.linphone.settings.widget;

/*
ListSetting.java
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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;

public class ListSetting extends BasicSetting implements AdapterView.OnItemSelectedListener {
    protected Spinner mSpinner;
    protected List<String> mItems;
    protected List<String> mItemsValues;

    public ListSetting(Context context) {
        super(context);
    }

    public ListSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ListSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ListSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView = LayoutInflater.from(mContext).inflate(R.layout.settings_widget_list, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);
        mItems = new ArrayList<>();
        mItemsValues = new ArrayList<>();

        mSpinner = mView.findViewById(R.id.setting_spinner);
        mSpinner.setOnItemSelectedListener(this);

        if (attrs != null) {
            TypedArray a =
                    mContext.getTheme()
                            .obtainStyledAttributes(
                                    attrs, R.styleable.Settings, defStyleAttr, defStyleRes);
            try {
                CharSequence[] names = a.getTextArray(R.styleable.Settings_list_items_names);
                CharSequence[] values = a.getTextArray(R.styleable.Settings_list_items_values);
                if (values != null && names != null) {
                    for (CharSequence cs : names) {
                        mItems.add(cs.toString());
                    }
                    for (CharSequence cs : values) {
                        mItemsValues.add(cs.toString());
                    }
                    setItems(mItems, mItemsValues);
                }
            } finally {
                a.recycle();
            }
        }
    }

    public void setItems(List<String> list, List<String> valuesList) {
        mItems = list;
        mItemsValues = valuesList;
        ArrayAdapter<String> dataAdapter =
                new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(dataAdapter);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mListener != null && position < mItems.size()) {
            String itemValue = null;
            if (mItemsValues != null && position < mItemsValues.size()) {
                itemValue = mItemsValues.get(position);
            }
            mListener.onListValueChanged(position, mItems.get(position), itemValue);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    public void setValue(String value) {
        int index = mItemsValues.indexOf(value);
        if (index == -1) {
            index = mItems.indexOf(value);
        }
        if (index != -1) {
            mSpinner.setSelection(index);
        }
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mSpinner.setEnabled(enabled);
    }

    public void setValue(int value) {
        setValue(String.valueOf(value));
    }

    public void setValue(float value) {
        setValue(String.valueOf(value));
    }

    public void setValue(double value) {
        setValue(String.valueOf(value));
    }
}
