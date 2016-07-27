package org.linphone.assistant;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.R;
import org.linphone.mediastream.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class CountryListFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnClickListener {
	private LayoutInflater mInflater;
	private ListView list;
	private EditText search;
	private ImageView clearSearchField;
	private CountryListAdapter adapter;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		mInflater = inflater;

		View view = inflater.inflate(R.layout.assistant_country_list, container, false);
		adapter = new CountryListAdapter(R.raw.countries, getActivity().getApplicationContext());

		search = (EditText)view.findViewById(R.id.search_country);
		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);

		list = (ListView)view.findViewById(R.id.countryList);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);

		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.getFilter().filter(s);
			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});

		return view;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Country c = (Country)view.getTag();
		AssistantActivity.instance().country = c;
		AssistantActivity.instance().displayCreateAccount();
	}

	@Override
	public void onClick(View v) {
		if(v.getId() == R.id.clearSearchField) {
			search.setText("");
		}
	}

	/**
	 * This class represents a Country. There's a name, dial_code, code and max number of digits.
	 * It is constructed from a JSON object containing all these parameters.
	 */
	public class Country {
		public String name;
		public String dial_code;
		public String code;
		public int maxNum;

		public Country(JSONObject obj ){
			try {
				name = obj.getString("name");
				dial_code = obj.getString("dial_code");
				code = obj.getString("code");
				maxNum = obj.getInt("maxNum");
			} catch (JSONException e){
				e.printStackTrace();
			}
		}
	}

	/**
	 * This class reads a JSON file containing Country-specific phone number description,
	 * and allows to present them into a ListView
	 */
	private class CountryListAdapter extends BaseAdapter implements Filterable {

		private List<Country> allCountries;
		private List<Country> filteredCountries;
		private Context context;

		public CountryListAdapter(int jsonId, Context ctx) {
			context = ctx;
			allCountries = new ArrayList<Country>();
			String jsonString = loadJSONFromAsset(R.raw.countries);
			try {
				JSONArray c = new JSONArray(jsonString);
				for( int i = 0; i < c.length(); i++) {
					allCountries.add(new Country(c.getJSONObject(i)));
				}
				filteredCountries = allCountries;
			} catch (JSONException e){
				e.printStackTrace();
			}
		}

		public String loadJSONFromAsset(int id) {
			String json = null;
			try {
				InputStream is = getResources().openRawResource(id);
				int size = is.available();
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();
				json = new String(buffer, "UTF-8");
			} catch (IOException ex) {
				ex.printStackTrace();
				return null;
			}
			return json;
		}

		@Override
		public int getCount() {
			return filteredCountries.size();
		}

		@Override
		public Country getItem(int position) {
			return filteredCountries.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent){
			View view = null;

			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.country_cell, parent, false);
			}

			Country c = filteredCountries.get(position);

			TextView name = (TextView) view.findViewById(R.id.country_name);
			name.setText(c.name);

			TextView dial_code = (TextView) view.findViewById(R.id.country_prefix);
			dial_code.setText(String.format(getString(R.string.country_code),c.dial_code));

			view.setTag(c);
			return view;
		}

		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence constraint) {
					ArrayList<Country> filteredCountries = new ArrayList<Country>();
					for (Country c : allCountries) {
						if (c.name.toLowerCase().contains(constraint) || c.dial_code.contains(constraint)){
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
					filteredCountries = (List<Country>) results.values;
					CountryListAdapter.this.notifyDataSetChanged();
				}
			};
		}
	}
}
