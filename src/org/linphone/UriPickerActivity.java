/*
LinphoneActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone;


import org.linphone.mediastream.Version;
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TabWidget;
import android.widget.TabHost.TabSpec;

/**
 * @author Guillaume Beraudo
 */
public class UriPickerActivity extends TabActivity implements ContactPicked {
	private static final String DIALER_TAB = "dialer";
	public static final String EXTRA_CALLEE_NAME = "callee_name";
	public static final String EXTRA_CALLEE_URI = "callee_uri";
	public static final String EXTRA_CALLEE_PHOTO_URI = "callee_photo_uri";
	public static final String EXTRA_PICKER_TYPE = "picker_type";
	public static final String EXTRA_PICKER_TYPE_ADD = "picker_type_add";
	public static final String EXTRA_PICKER_TYPE_TRANSFER = "picker_type_transfer";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		fillTabHost();
	}


	private synchronized void fillTabHost() {
		if (((TabWidget) findViewById(android.R.id.tabs)).getChildCount() != 0) return;

		startActivityInTab("history",
				new Intent().setClass(this, HistoryActivity.class),
				R.string.tab_history, R.drawable.history_orange);


		startActivityInTab(DIALER_TAB,
				new Intent().setClass(this, DialerActivity.class).setData(getIntent().getData())
				.putExtra(EXTRA_PICKER_TYPE, getIntent().getStringExtra(EXTRA_PICKER_TYPE)),
				R.string.tab_dialer, R.drawable.dialer_orange);


		startActivityInTab("contact",
				new Intent().setClass(this, Version.sdkAboveOrEqual(Version.API05_ECLAIR_20) ?
						ContactPickerActivityNew.class : ContactPickerActivityOld.class),
						R.string.tab_contact, R.drawable.contact_orange);


		selectDialerTab();
	}


	private void selectDialerTab() {
		getTabHost().setCurrentTabByTag(DIALER_TAB);
	}

	private void startActivityInTab(String tag, Intent intent, int indicatorId, int drawableId) {
		Drawable tabDrawable = getResources().getDrawable(drawableId);
		TabSpec spec = getTabHost().newTabSpec(tag)
		.setIndicator(getString(indicatorId), tabDrawable)
		.setContent(intent);
		getTabHost().addTab(spec);
	}


	
	void terminate(String number, String name, Uri photo) {
		Intent intent = new Intent()
		.putExtra(EXTRA_CALLEE_NAME, name)
		.putExtra(EXTRA_CALLEE_URI, number)
		.putExtra(EXTRA_CALLEE_PHOTO_URI, photo);
		setResult(Activity.RESULT_OK, intent);
		finish();
	}

	
	
	
	
	public static class DialerActivity extends Activity implements OnClickListener {

		private AddressText mAddress;
		private Button addButton;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			setContentView(R.layout.simplified_dialer);
			mAddress = (AddressText) findViewById(R.id.SipUri);

			addButton = (Button) findViewById(R.id.AddCallButton);
//			addButton.setCompoundDrawablePadding(100);
			addButton.setOnClickListener(this);
			String type = getIntent().getStringExtra(EXTRA_PICKER_TYPE);
			if (EXTRA_PICKER_TYPE_ADD.equals(type)) {
				addButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.incall_add_small, 0, 0);
				addButton.setText(getString(R.string.AddCallButtonText));
			} else if (EXTRA_PICKER_TYPE_TRANSFER.equals(type)) {
				addButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.picker_transfer, 0, 0);
				addButton.setText(getString(R.string.TransferCallButtonText));
			} else {
				throw new RuntimeException("unknown type");
			}

//			findViewById(R.id.AddCallCancelButton).setOnClickListener(this);

			((AddressAware)findViewById(R.id.Dialer)).setAddressWidget(mAddress);
			((AddressAware)findViewById(R.id.Erase)).setAddressWidget(mAddress);
			super.onCreate(savedInstanceState);
		}

		public void setContactAddress(String number, String name, Uri photo) {
			mAddress.setText(number);
			mAddress.setDisplayedName(name);
			mAddress.setPictureUri(photo);
		}

		@Override
		public void onClick(View v) {
			if (v == addButton) {
			UriPickerActivity parent = (UriPickerActivity) getParent();
			parent.terminate(mAddress.getText().toString(), mAddress.getDisplayedName(), mAddress.getPictureUri());
			} else {
				// propagate finish to parent through finishFromChild
				finish();
			}
		}
	}


	public void setAddressAndGoToDialer(String number, String name, Uri photo) {
		DialerActivity dialer = (DialerActivity) getLocalActivityManager().getActivity(DIALER_TAB);
		dialer.setContactAddress(number, name, photo);
		selectDialerTab();
	}


	@Override
	public void goToDialer() {
		selectDialerTab();
	}
}
