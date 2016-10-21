package org.linphone.assistant;

import org.linphone.R;
import org.linphone.core.DialPlan;

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


public class CountryListFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnClickListener {
	private ListView list;
	private EditText search;
	private ImageView clearSearchField;
	private AssistantActivity.CountryListAdapter adapter;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.assistant_country_list, container, false);
		adapter = AssistantActivity.instance().getCountryListAdapter();
		adapter.setInflater(inflater);

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
		DialPlan c = (DialPlan) view.getTag();
		AssistantActivity.instance().country = c;
		AssistantActivity.instance().onBackPressed();
	}

	@Override
	public void onClick(View v) {
		if(v.getId() == R.id.clearSearchField) {
			search.setText("");
		}
	}
}
