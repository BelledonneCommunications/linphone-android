package org.linphone;
/*
 ContactEditorFragment.java
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
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ContactEditorFragment extends Fragment {
	private View view;
	private ImageView cancel, deleteContact, ok;
	private ImageView addNumber, addSipAddress, contactPicture;
	private LinearLayout phoneNumbersSection, sipAddressesSection;
	private EditText firstName, lastName, organization;
	private LayoutInflater inflater;

	private static final int ADD_PHOTO = 1337;
	private static final int PHOTO_SIZE = 128;

	private boolean isNewContact;
	private LinphoneContact contact;
	private List<LinphoneNumberOrAddress> numbersAndAddresses;
	private int firstSipAddressIndex = -1;
	private LinearLayout sipAddresses, numbers;
	private String newSipOrNumberToAdd;
	private Uri pickedPhotoForContactUri;
	private byte[] photoToAdd;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.inflater = inflater;

		contact = null;
		isNewContact = true;

		if (getArguments() != null) {
			Serializable obj = getArguments().getSerializable("Contact");
			if (obj != null) {
				contact = (LinphoneContact) obj;
				isNewContact = false;
				if (getArguments().getString("NewSipAdress") != null) {
					newSipOrNumberToAdd = getArguments().getString("NewSipAdress");
				}
			} else if (getArguments().getString("NewSipAdress") != null) {
				newSipOrNumberToAdd = getArguments().getString("NewSipAdress");
			}
		}

		view = inflater.inflate(R.layout.contact_edit, container, false);

		phoneNumbersSection = (LinearLayout) view.findViewById(R.id.phone_numbers);
		if (getResources().getBoolean(R.bool.hide_phone_numbers_in_editor) || !ContactsManager.getInstance().hasContactsAccess()) {
			//Currently linphone friends don't support phone numbers, so hide them
			phoneNumbersSection.setVisibility(View.GONE);
		}

		sipAddressesSection = (LinearLayout) view.findViewById(R.id.sip_addresses);
		if (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor)) {
			sipAddressesSection.setVisibility(View.GONE);
		}


		deleteContact = (ImageView) view.findViewById(R.id.delete_contact);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					getFragmentManager().popBackStackImmediate();
				}
			});

		ok = (ImageView) view.findViewById(R.id.ok);
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isNewContact) {
					boolean areAllFielsEmpty = true;
					for (LinphoneNumberOrAddress nounoa : numbersAndAddresses) {
						if (nounoa.getValue() != null && !nounoa.getValue().equals("")) {
							areAllFielsEmpty = false;
							break;
						}
					}
					if (areAllFielsEmpty) {
						getFragmentManager().popBackStackImmediate();
						return;
					}
					contact = LinphoneContact.createContact();
				}
				contact.setFirstNameAndLastName(firstName.getText().toString(), lastName.getText().toString());
				if (photoToAdd != null) {
					contact.setPhoto(photoToAdd);
				}
				for (LinphoneNumberOrAddress noa : numbersAndAddresses) {
					if (noa.isSIPAddress() && noa.getValue() != null) {
						noa.setValue(LinphoneUtils.getFullAddressFromUsername(noa.getValue()));
					}
					contact.addOrUpdateNumberOrAddress(noa);
				}
				contact.setOrganization(organization.getText().toString());
				contact.save();
				getFragmentManager().popBackStackImmediate();
			}
		});

		lastName = (EditText) view.findViewById(R.id.contactLastName);
		// Hack to display keyboard when touching focused edittext on Nexus One
		if (Version.sdkStrictlyBelow(Version.API11_HONEYCOMB_30)) {
			lastName.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					InputMethodManager imm = (InputMethodManager) LinphoneActivity.instance().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
				}
			});
		}
		lastName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (lastName.getText().length() > 0 || firstName.getText().length() > 0) {
					ok.setEnabled(true);
				} else {
					ok.setEnabled(false);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		firstName = (EditText) view.findViewById(R.id.contactFirstName);
		firstName.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (firstName.getText().length() > 0 || lastName.getText().length() > 0) {
					ok.setEnabled(true);
				} else {
					ok.setEnabled(false);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});


		organization = (EditText) view.findViewById(R.id.contactOrganization);
		boolean isOrgVisible = getResources().getBoolean(R.bool.display_contact_organization);
		if (!isOrgVisible) {
			organization.setVisibility(View.GONE);
			view.findViewById(R.id.contactOrganizationTitle).setVisibility(View.GONE);
		} else {
			if (!isNewContact) {
				organization.setText(contact.getOrganization());
			}
		}

		if (!isNewContact) {
			String fn = contact.getFirstName();
			String ln = contact.getLastName();
			if (fn != null || ln != null) {
				firstName.setText(fn);
				lastName.setText(ln);
			} else {
				lastName.setText(contact.getFullName());
				firstName.setText("");
			}

			deleteContact.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
					Button delete = (Button) dialog.findViewById(R.id.delete_button);
					Button cancel = (Button) dialog.findViewById(R.id.cancel);

					delete.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							contact.delete();
							LinphoneActivity.instance().displayContacts(false);
							dialog.dismiss();
						}
					});

					cancel.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							dialog.dismiss();

						}
					});
					dialog.show();
				}
			});
		} else {
			deleteContact.setVisibility(View.INVISIBLE);
		}

		contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
		if (contact != null) {
			LinphoneUtils.setImagePictureFromUri(getActivity(), contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
		} else {
			contactPicture.setImageResource(R.drawable.avatar);
		}

		contactPicture.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				pickImage();
				LinphoneActivity.instance().checkAndRequestCameraPermission();
			}
		});

		numbersAndAddresses = new ArrayList<LinphoneNumberOrAddress>();
		sipAddresses = initSipAddressFields(contact);
		numbers = initNumbersFields(contact);

		addSipAddress = (ImageView) view.findViewById(R.id.add_address_field);
		if (getResources().getBoolean(R.bool.allow_only_one_sip_address)) {
			addSipAddress.setVisibility(View.GONE);
		}
		addSipAddress.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addEmptyRowToAllowNewNumberOrAddress(sipAddresses,true);
			}
		});

		addNumber = (ImageView) view.findViewById(R.id.add_number_field);
		if (getResources().getBoolean(R.bool.allow_only_one_phone_number)) {
			addNumber.setVisibility(View.GONE);
		}
		addNumber.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addEmptyRowToAllowNewNumberOrAddress(numbers,false);
			}
		});

		lastName.requestFocus();

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		if(LinphoneActivity.isInstanciated()){
			LinphoneActivity.instance().hideTabBar(false);
		}

		// Force hide keyboard
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
	}

	@Override
	public void onPause() {
		// Force hide keyboard
		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		View view = getActivity().getCurrentFocus();
		if (imm != null && view != null) {
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}

		super.onPause();
	}

	private void pickImage() {
		pickedPhotoForContactUri = null;
		final List<Intent> cameraIntents = new ArrayList<Intent>();
		final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
		pickedPhotoForContactUri = Uri.fromFile(file);
		captureIntent.putExtra("outputX", PHOTO_SIZE);
		captureIntent.putExtra("outputY", PHOTO_SIZE);
		captureIntent.putExtra("aspectX", 0);
		captureIntent.putExtra("aspectY", 0);
		captureIntent.putExtra("scale", true);
		captureIntent.putExtra("return-data", false);
		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pickedPhotoForContactUri);
		cameraIntents.add(captureIntent);

		final Intent galleryIntent = new Intent();
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

		final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

		startActivityForResult(chooserIntent, ADD_PHOTO);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
			if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
				Bitmap bm = (Bitmap) data.getExtras().get("data");
				editContactPicture(null, bm);
			}
			else if (data != null && data.getData() != null) {
				Uri selectedImageUri = data.getData();
				try {
					Bitmap selectedImage = MediaStore.Images.Media.getBitmap(LinphoneManager.getInstance().getContext().getContentResolver(), selectedImageUri);
					selectedImage = Bitmap.createScaledBitmap(selectedImage, PHOTO_SIZE, PHOTO_SIZE, false);
					editContactPicture(null, selectedImage);
				} catch (IOException e) { Log.e(e); }
			}
			else if (pickedPhotoForContactUri != null) {
				String filePath = pickedPhotoForContactUri.getPath();
				editContactPicture(filePath, null);
			}
			else {
				File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
				if (file.exists()) {
					pickedPhotoForContactUri = Uri.fromFile(file);
					String filePath = pickedPhotoForContactUri.getPath();
					editContactPicture(filePath, null);
				}
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void editContactPicture(String filePath, Bitmap image) {
		if (image == null) {
			image = BitmapFactory.decodeFile(filePath);
		}

		Bitmap scaledPhoto;
		int size = getThumbnailSize();
		if (size > 0) {
			scaledPhoto = Bitmap.createScaledBitmap(image, size, size, false);
		} else {
			scaledPhoto = Bitmap.createBitmap(image);
		}
		image.recycle();

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		scaledPhoto.compress(Bitmap.CompressFormat.PNG , 0, stream);
		contactPicture.setImageBitmap(scaledPhoto);
		photoToAdd = stream.toByteArray();
	}

	private int getThumbnailSize() {
		int value = -1;
		Cursor c = LinphoneActivity.instance().getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI, new String[] { DisplayPhoto.THUMBNAIL_MAX_DIM }, null, null, null);
		try {
			c.moveToFirst();
			value = c.getInt(0);
		} catch (Exception e) {
			Log.e(e);
		}
		return value;
	}

	private LinearLayout initNumbersFields(final LinphoneContact contact) {
		LinearLayout controls = (LinearLayout) view.findViewById(R.id.controls_numbers);
		controls.removeAllViews();

		if (contact != null) {
			for (LinphoneNumberOrAddress numberOrAddress : contact.getNumbersOrAddresses()) {
				if (!numberOrAddress.isSIPAddress()) {
					View view = displayNumberOrAddress(controls, numberOrAddress.getValue(), false);
					if (view != null)
						controls.addView(view);
				}
			}
		}

		if (newSipOrNumberToAdd != null) {
			boolean isSip = LinphoneUtils.isStrictSipAddress(newSipOrNumberToAdd) || !LinphoneUtils.isNumberAddress(newSipOrNumberToAdd);
			if(!isSip) {
				View view = displayNumberOrAddress(controls, newSipOrNumberToAdd, false);
				if (view != null)
					controls.addView(view);
			}
		}

		if (controls.getChildCount() == 0) {
			addEmptyRowToAllowNewNumberOrAddress(controls,false);
		}

		return controls;
	}

	private LinearLayout initSipAddressFields(final LinphoneContact contact) {
		LinearLayout controls = (LinearLayout) view.findViewById(R.id.controls_sip_address);
		controls.removeAllViews();

		if (contact != null) {
			for (LinphoneNumberOrAddress numberOrAddress : contact.getNumbersOrAddresses()) {
				if (numberOrAddress.isSIPAddress()) {
					View view = displayNumberOrAddress(controls, numberOrAddress.getValue(), true);
					if (view != null)
						controls.addView(view);
				}
			}
		}

		if (newSipOrNumberToAdd != null) {
			boolean isSip = LinphoneUtils.isStrictSipAddress(newSipOrNumberToAdd) || !LinphoneUtils.isNumberAddress(newSipOrNumberToAdd);
			if (isSip) {
				View view = displayNumberOrAddress(controls, newSipOrNumberToAdd, true);
				if (view != null)
					controls.addView(view);
			}
		}

		if (controls.getChildCount() == 0) {
			addEmptyRowToAllowNewNumberOrAddress(controls,true);
		}

		return controls;
	}

	private View displayNumberOrAddress(final LinearLayout controls, String numberOrAddress, boolean isSIP) {
		return displayNumberOrAddress(controls, numberOrAddress, isSIP, false);
	}

	@SuppressLint("InflateParams")
	private View displayNumberOrAddress(final LinearLayout controls, String numberOrAddress, boolean isSIP, boolean forceAddNumber) {
		String displayNumberOrAddress = numberOrAddress;
		if (isSIP) {
			if (firstSipAddressIndex == -1) {
				firstSipAddressIndex = controls.getChildCount();
			}
			displayNumberOrAddress = LinphoneUtils.getDisplayableUsernameFromAddress(numberOrAddress);
		}
		if ((getResources().getBoolean(R.bool.hide_phone_numbers_in_editor) && !isSIP) || (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor) && isSIP)) {
			if (forceAddNumber)
				isSIP = !isSIP; // If number can't be displayed because we hide a sort of number, change that category
			else
				return null;
		}

		LinphoneNumberOrAddress tempNounoa;
		if (forceAddNumber) {
			tempNounoa = new LinphoneNumberOrAddress(null, isSIP);
		} else {
			if(isNewContact || newSipOrNumberToAdd != null) {
				tempNounoa = new LinphoneNumberOrAddress(numberOrAddress, isSIP);
			} else {
				tempNounoa = new LinphoneNumberOrAddress(null, isSIP, numberOrAddress);
			}
		}
		final LinphoneNumberOrAddress nounoa = tempNounoa;
		numbersAndAddresses.add(nounoa);

		final View view = inflater.inflate(R.layout.contact_edit_row, null);

		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		if (!isSIP) {
			noa.setInputType(InputType.TYPE_CLASS_PHONE);
		}
		noa.setText(displayNumberOrAddress);
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setValue(noa.getText().toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		if (forceAddNumber) {
			nounoa.setValue(noa.getText().toString());
		}

		ImageView delete = (ImageView) view.findViewById(R.id.delete_field);
		if ((getResources().getBoolean(R.bool.allow_only_one_phone_number) && !isSIP) || (getResources().getBoolean(R.bool.allow_only_one_sip_address) && isSIP)) {
			delete.setVisibility(View.GONE);
		}
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (contact != null) {
					contact.removeNumberOrAddress(nounoa);
				}
				numbersAndAddresses.remove(nounoa);
				view.setVisibility(View.GONE);

			}
		});
		return view;
	}

	@SuppressLint("InflateParams")
	private void addEmptyRowToAllowNewNumberOrAddress(final LinearLayout controls, final boolean isSip) {
		final View view = inflater.inflate(R.layout.contact_edit_row, null);
		final LinphoneNumberOrAddress nounoa = new LinphoneNumberOrAddress(null, isSip);

		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		numbersAndAddresses.add(nounoa);
		noa.setHint(isSip ? getString(R.string.sip_address) : getString(R.string.phone_number));
		if (!isSip) {
			noa.setInputType(InputType.TYPE_CLASS_PHONE);
		}
		noa.requestFocus();
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setValue(noa.getText().toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		final ImageView delete = (ImageView) view.findViewById(R.id.delete_field);
		if ((getResources().getBoolean(R.bool.allow_only_one_phone_number) && !isSip) || (getResources().getBoolean(R.bool.allow_only_one_sip_address) && isSip)) {
			delete.setVisibility(View.GONE);
		}
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				numbersAndAddresses.remove(nounoa);
				view.setVisibility(View.GONE);
			}
		});

		controls.addView(view);
	}
}