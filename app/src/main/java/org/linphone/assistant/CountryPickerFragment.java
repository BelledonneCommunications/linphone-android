package org.linphone.assistant;

/*
CountryPickerFragment.java
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.R;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;

public class CountryPickerFragment extends DialogFragment {
    private CountryAdapter mAdapter;
    private ListView mList;
    private EditText mSearch;

    static CountryPickerFragment instance() {
        CountryPickerFragment f = new CountryPickerFragment();
        return f;
    }

    private View createView() {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        return createView(inflater, null);
    }

    private View createView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.assistant_country_list, container, false);

        mList = view.findViewById(R.id.countryList);
        mAdapter = new CountryAdapter(inflater);
        mList.setAdapter(mAdapter);

        mSearch = view.findViewById(R.id.search_country);
        mSearch.setText("");
        mSearch.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mAdapter.getFilter().filter(s.toString());
                        mList.invalidate();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        return view;
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return createView(inflater, container);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = createView();
        return new AlertDialog.Builder(getActivity()).setView(view).create();
    }

    class CountryAdapter extends BaseAdapter implements Filterable {
        private LayoutInflater mInflater;
        private final DialPlan[] mAllCountries;
        private List<DialPlan> mFilteredCountries;

        public CountryAdapter(LayoutInflater inflater) {
            mInflater = inflater;
            mAllCountries = Factory.instance().getDialPlans();
            mFilteredCountries = new ArrayList<>(Arrays.asList(mAllCountries));
        }

        @Override
        public int getCount() {
            return mFilteredCountries.size();
        }

        @Override
        public DialPlan getItem(int position) {
            return mFilteredCountries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public DialPlan getCountryFromCountryCode(String countryCode) {
            countryCode = (countryCode.startsWith("+")) ? countryCode.substring(1) : countryCode;
            for (DialPlan c : mAllCountries) {
                if (c.getCountryCallingCode().compareTo(countryCode) == 0) return c;
            }
            return null;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView != null) {
                view = convertView;
            } else {
                view = mInflater.inflate(R.layout.country_cell, parent, false);
            }

            DialPlan c = mFilteredCountries.get(position);

            TextView name = view.findViewById(R.id.country_name);
            name.setText(c.getCountry());

            TextView dial_code = view.findViewById(R.id.country_prefix);
            dial_code.setText(
                    String.format(getString(R.string.country_code), c.getCountryCallingCode()));

            view.setTag(c);
            return view;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    ArrayList<DialPlan> filteredCountries = new ArrayList<>();
                    for (DialPlan c : mAllCountries) {
                        if (c.getCountry().toLowerCase().contains(constraint)
                                || c.getCountryCallingCode().contains(constraint)) {
                            filteredCountries.add(c);
                        }
                    }
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = filteredCountries;
                    return filterResults;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    mFilteredCountries = (List<DialPlan>) results.values;
                    notifyDataSetChanged();
                }
            };
        }
    }
}
