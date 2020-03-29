/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import kotlin.collections.ArrayList
import org.linphone.R
import org.linphone.core.DialPlan
import org.linphone.core.Factory

class CountryPickerAdapter : BaseAdapter(), Filterable {
    private var countries: ArrayList<DialPlan>

    init {
        val dialPlans = Factory.instance().dialPlans
        countries = arrayListOf()
        countries.addAll(dialPlans)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.assistant_country_picker_cell, parent, false)
        val dialPlan: DialPlan = countries[position]

        val name = view.findViewById<TextView>(R.id.country_name)
        name.text = dialPlan.country

        val dialCode = view.findViewById<TextView>(R.id.country_prefix)
        dialCode.text = String.format("(%s)", dialPlan.countryCallingCode)

        view.tag = dialPlan
        return view
    }

    override fun getItem(position: Int): DialPlan {
        return countries[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return countries.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val filteredCountries = arrayListOf<DialPlan>()
                for (dialPlan in Factory.instance().dialPlans) {
                    if (dialPlan.country.contains(constraint, ignoreCase = true) ||
                        dialPlan.countryCallingCode.contains(constraint)
                    ) {
                        filteredCountries.add(dialPlan)
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = filteredCountries
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(
                constraint: CharSequence,
                results: FilterResults
            ) {
                countries = results.values as ArrayList<DialPlan>
                notifyDataSetChanged()
            }
        }
    }
}
