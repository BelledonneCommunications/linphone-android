package org.linphone.settings;

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
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.Nullable;
import java.util.List;
import org.linphone.R;

public class ListSetting extends BasicSetting implements AdapterView.OnItemSelectedListener {
    protected int mLayout = R.layout.settings_list_preference;
    protected Spinner mSpinner;
    protected List<String> mItems;

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

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mSpinner = mView.findViewById(R.id.setting_spinner);
        mSpinner.setOnItemSelectedListener(this);
    }

    public void setItems(List<String> list) {
        mItems = list;
        ArrayAdapter<String> dataAdapter =
                new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(dataAdapter);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mListener != null && position < mItems.size()) {
            mListener.onValueChanged(mItems.get(position));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        if (mListener != null) {
            mListener.onValueChanged(null);
        }
    }
}
