package org.linphone.assistant_old;

/*
CountryListFragment.java
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

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import org.linphone.R;
import org.linphone.core.DialPlan;

public class CountryListFragment extends Fragment
        implements AdapterView.OnItemClickListener, View.OnClickListener {
    private ListView mList;
    private EditText mSearch;
    private ImageView mClearSearchField;
    private AssistantActivity.CountryListAdapter mAdapter;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.assistant_country_list, container, false);
        mAdapter = AssistantActivity.instance().getCountryListAdapter();
        mAdapter.setInflater(inflater);

        mSearch = view.findViewById(R.id.search_country);
        mClearSearchField = view.findViewById(R.id.clearSearchField);
        mClearSearchField.setOnClickListener(this);

        mList = view.findViewById(R.id.countryList);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(this);

        mSearch.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mAdapter.getFilter().filter(s);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
        mSearch.setText("");

        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AssistantActivity.instance().country = (DialPlan) view.getTag();
        AssistantActivity.instance().onBackPressed();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.clearSearchField) {
            mSearch.setText("");
        }
    }
}
