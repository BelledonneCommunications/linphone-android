/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.assistant;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.R;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;

class CountryAdapter extends BaseAdapter implements Filterable {
    private final Context mContext;
    private final LayoutInflater mInflater;
    private final DialPlan[] mAllCountries;
    private List<DialPlan> mFilteredCountries;

    public CountryAdapter(Context context, LayoutInflater inflater) {
        mContext = context;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView != null) {
            view = convertView;
        } else {
            view = mInflater.inflate(R.layout.assistant_country_cell, parent, false);
        }

        DialPlan c = mFilteredCountries.get(position);

        TextView name = view.findViewById(R.id.country_name);
        name.setText(c.getCountry());

        TextView dial_code = view.findViewById(R.id.country_prefix);
        dial_code.setText(
                String.format(
                        mContext.getString(R.string.country_code), c.getCountryCallingCode()));

        view.setTag(c);
        return view;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<DialPlan> filteredCountries = new ArrayList<>();
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
