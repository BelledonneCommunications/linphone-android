package org.linphone;
/*
LinphonePreferencesActivity.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import org.linphone.compatibility.Compatibility;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class LinphonePreferencesActivity extends PreferenceActivity implements OnClickListener {
	private ImageView history, contacts, dialer, settings, chat;
	private TextView missedCalls;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Hack to allow custom view in preferences, in this case the bottom menu
		setContentView(R.layout.settings);
		
		initButtons();
		
		int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
		displayMissedCalls(missedCalls);
	}
	
	private void initButtons() {
		history = (ImageView) findViewById(R.id.history);
        history.setOnClickListener(this);
        contacts  = (ImageView) findViewById(R.id.contacts);
        contacts.setOnClickListener(this);
        dialer = (ImageView) findViewById(R.id.dialer);
        dialer.setOnClickListener(this);
		dialer.setSelected(true);
        settings = (ImageView) findViewById(R.id.settings);
        settings.setOnClickListener(this);
        chat = (ImageView) findViewById(R.id.chat);
		chat.setOnClickListener(this);
		missedCalls = (TextView) findViewById(R.id.missedCalls);
		
		history.setSelected(false);
		contacts.setSelected(false);
		dialer.setSelected(false);
		settings.setSelected(true);
		chat.setSelected(false);
	}
	
	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		FragmentsAvailable newFragment = FragmentsAvailable.SETTINGS;
		if (id == R.id.history) {
			newFragment = FragmentsAvailable.HISTORY;
		}
		else if (id == R.id.contacts) {
			newFragment = FragmentsAvailable.CONTACTS;
		}
		else if (id == R.id.dialer) {
			newFragment = FragmentsAvailable.DIALER;
		}
		else if (id == R.id.chat) {
			newFragment = FragmentsAvailable.CHATLIST;
		}
		
		if (newFragment != FragmentsAvailable.SETTINGS) {
			Intent intent = new Intent();
			intent.putExtra("FragmentToDisplay", newFragment);
			setResult(RESULT_FIRST_USER, intent);
			finishWithCustomAnimation(newFragment);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (resultCode == RESULT_FIRST_USER) {
			// If we were on a LinphonePreferences sub activity, and we came back because of a change of tab, we propagate the event
			setResult(RESULT_FIRST_USER, data);
			finish();
			return;
		}
	}
	
	private void displayMissedCalls(final int missedCallsCount) {
		if (missedCallsCount > 0) {
			missedCalls.setText(missedCallsCount + "");
			missedCalls.setVisibility(View.VISIBLE);
		} else {
			missedCalls.setVisibility(View.GONE);
		}
	}
	
	private void finishWithCustomAnimation(FragmentsAvailable newFragment) {
		finish();
		if (FragmentsAvailable.SETTINGS.isRightOf(newFragment)) {
			Compatibility.overridePendingTransition(this, R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right);
		} else {
			Compatibility.overridePendingTransition(this, R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left);
		}
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			finishWithCustomAnimation(LinphoneActivity.instance().getCurrentFragment());
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
