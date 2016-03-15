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
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.List;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.app.Fragment;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
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
	private EditText firstName, lastName;
	private LayoutInflater inflater;
	private static final int ADD_PHOTO = 1337;
	
	private boolean isNewContact = true;
	private Contact contact;
	private int contactID;
	private List<NewOrUpdatedNumberOrAddress> numbersAndAddresses;
	private ArrayList<ContentProviderOperation> ops;
	private int firstSipAddressIndex = -1;
	private LinearLayout sipAddresses, numbers;
	private String newSipOrNumberToAdd;
	private ContactsManager contactsManager;
	private Uri imageToUploadUri;
	private String fileToUploadPath;
	private Bitmap imageToUpload;
	private Bitmap bitmapUnknown;
	byte[] photoToAdd;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.inflater = inflater;
		
		contact = null;
		if (getArguments() != null) {
			if (getArguments().getSerializable("Contact") != null) {
				contact = (Contact) getArguments().getSerializable("Contact");
				isNewContact = false;
				contactID = Integer.parseInt(contact.getID());
				contact.refresh(getActivity().getContentResolver());
				if (getArguments().getString("NewSipAdress") != null) {
					newSipOrNumberToAdd = getArguments().getString("NewSipAdress");
				}

			} else if (getArguments().getString("NewSipAdress") != null) {
				newSipOrNumberToAdd = getArguments().getString("NewSipAdress");
				isNewContact = true;
			}
		}

		contactsManager = ContactsManager.getInstance();
		
		view = inflater.inflate(R.layout.contact_edit, container, false);
		
		phoneNumbersSection = (LinearLayout) view.findViewById(R.id.phone_numbers);
		if (getResources().getBoolean(R.bool.hide_phone_numbers_in_editor)) {
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
						for (NewOrUpdatedNumberOrAddress nounoa : numbersAndAddresses) {
							if (nounoa.newNumberOrAddress != null && !nounoa.newNumberOrAddress.equals("")) {
								areAllFielsEmpty = false;
								break;
							}
						}
						if (areAllFielsEmpty) {
							getFragmentManager().popBackStackImmediate();
							return;
						}
					contactsManager.createNewContact(ops, firstName.getText().toString(), lastName.getText().toString());
					setContactPhoto();
				} else {
					contactsManager.updateExistingContact(ops, contact, firstName.getText().toString(), lastName.getText().toString());
					setContactPhoto();
				}

				for (NewOrUpdatedNumberOrAddress numberOrAddress : numbersAndAddresses) {
					numberOrAddress.save();
				}

		        try {
					getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
					addLinphoneFriendIfNeeded();
					removeLinphoneTagIfNeeded();
					contactsManager.prepareContactsInBackground();
		        } catch (Exception e) {
		        	e.printStackTrace();
		        }

				if(isNewContact) {
					getFragmentManager().popBackStackImmediate();
				} else {
					if (LinphoneActivity.instance().getResources().getBoolean(R.bool.isTablet)) {
						if(ContactsListFragment.isInstanciated()) {
							ContactsListFragment.instance().invalidate();
						}
						getFragmentManager().popBackStackImmediate();
					} else {
						Contact updatedContact = contactsManager.findContactWithDisplayName(contactsManager.getDisplayName(firstName.getText().toString(), lastName.getText().toString()));
						if (updatedContact != null) {
							LinphoneActivity.instance().displayContact(updatedContact, false);
						} else {
							LinphoneActivity.instance().displayContacts(false);
						}
					}
				}
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

		if (!isNewContact) {
			String fn = findContactFirstName(String.valueOf(contactID));
			String ln = findContactLastName(String.valueOf(contactID));
			if (fn != null || ln != null) {
				firstName.setText(fn);
				lastName.setText(ln);
			} else {
				lastName.setText(contact.getName());
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
							deleteExistingContact();
							ContactsManager.getInstance().removeContactFromLists(getActivity().getContentResolver(), contact);
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
		if (contact != null && contact.getPhotoUri() != null) {
			InputStream input = Compatibility.getContactPictureInputStream(getActivity().getContentResolver(), contact.getID());
			contactPicture.setImageBitmap(BitmapFactory.decodeStream(input));
        } else {
        	contactPicture.setImageResource(R.drawable.avatar);
        }

		contactPicture.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				pickImage();
			}
		});

		numbersAndAddresses = new ArrayList<NewOrUpdatedNumberOrAddress>();
		sipAddresses = initSipAddressFields(contact);
		numbers = initNumbersFields(contact);

		addSipAddress = (ImageView) view.findViewById(R.id.add_address_field);
		addSipAddress.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addEmptyRowToAllowNewNumberOrAddress(sipAddresses,true);
			}
		});

		addNumber = (ImageView) view.findViewById(R.id.add_number_field);
		addNumber.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addEmptyRowToAllowNewNumberOrAddress(numbers,false);
			}
		});

		ops = new ArrayList<ContentProviderOperation>();
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
		imageToUploadUri = null;
		final List<Intent> cameraIntents = new ArrayList<Intent>();
		final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
		imageToUploadUri = Uri.fromFile(file);
		captureIntent.putExtra("crop", "true");
		captureIntent.putExtra("outputX",256);
		captureIntent.putExtra("outputY", 256);
		captureIntent.putExtra("aspectX", 0);
		captureIntent.putExtra("aspectY", 0);
		captureIntent.putExtra("scale", true);
		captureIntent.putExtra("return-data", false);
		captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
		cameraIntents.add(captureIntent);

		final Intent galleryIntent = new Intent();
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

		final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

		startActivityForResult(chooserIntent, ADD_PHOTO);
	}

	public String getRealPathFromURI(Uri contentUri) {
		String[] proj = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
		Cursor cursor = loader.loadInBackground();
		if (cursor != null && cursor.moveToFirst()) {
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			String result = cursor.getString(column_index);
			cursor.close();
			return result;
		}
		return null;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
			if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
				Bitmap bm = (Bitmap) data.getExtras().get("data");
				showPopupMenuAskingImageSize(null, bm);
			}
			else if (data != null && data.getData() != null) {
				Uri selectedImageUri = data.getData();
				try {
					Bitmap selectedImage = MediaStore.Images.Media.getBitmap(LinphoneManager.getInstance().getContext().getContentResolver(), selectedImageUri);
					selectedImage = Bitmap.createScaledBitmap(selectedImage, 256, 256, false);
					showPopupMenuAskingImageSize(null, selectedImage);
				} catch (IOException e) { e.printStackTrace(); }
			}
			else if (imageToUploadUri != null) {
				String filePath = imageToUploadUri.getPath();
				showPopupMenuAskingImageSize(filePath, null);
			}
			else {
				File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
				if (file.exists()) {
					imageToUploadUri = Uri.fromFile(file);
					String filePath = imageToUploadUri.getPath();
					showPopupMenuAskingImageSize(filePath, null);
				}
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void deleteExistingContact() {
		String select = ContactsContract.Data.CONTACT_ID + " = ?";
		String[] args = new String[] { contact.getID() };

		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
						.withSelection(select, args)
						.build()
		);

		try {
			getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
			ContactsManager.getInstance().removeAllFriends(contact);
		} catch (Exception e) {
			Log.w(e.getMessage() + ":" + e.getStackTrace());
		}
	}

	private void showPopupMenuAskingImageSize(final String filePath, final Bitmap image) {
		fileToUploadPath = filePath;
		imageToUpload = image;
		editContactPicture(fileToUploadPath,imageToUpload);
	}

	private void editContactPicture(final String filePath, final Bitmap image) {
		int SIZE_SMALL = 256;
		int COMPRESSOR_QUALITY = 100;
		Bitmap bitmapUnknown = BitmapFactory.decodeResource(getResources(), R.drawable.avatar);
		Bitmap bm = null;

		if(filePath != null){
			int pixelsMax = SIZE_SMALL;
			//Resize image
			bm = BitmapFactory.decodeFile(filePath);
			if (bm != null) {
				if (bm.getWidth() > bm.getHeight() && bm.getWidth() > pixelsMax) {
					bm = Bitmap.createScaledBitmap(bm, 256, 256, false);
				}
			}
		} else if (image != null) {
			bm = image;
		}

		// Rotate the bitmap if possible/needed, using EXIF data
		try {
			if (imageToUploadUri != null && filePath != null) {
				ExifInterface exif = new ExifInterface(filePath);
				int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
				Matrix matrix = new Matrix();
				if (pictureOrientation == 6) {
					matrix.postRotate(90);
				} else if (pictureOrientation == 3) {
					matrix.postRotate(180);
				} else if (pictureOrientation == 8) {
					matrix.postRotate(270);
				}
				bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		Bitmap bitmapRounded;
		if(bm != null)
		{
			bitmapRounded = Bitmap.createScaledBitmap(bm,bitmapUnknown.getWidth(), bitmapUnknown.getWidth(), false);

			Canvas canvas = new Canvas(bitmapRounded);
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setShader(new BitmapShader(bitmapRounded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
			canvas.drawCircle(bitmapRounded.getWidth() / 2+0.7f, bitmapRounded.getHeight() / 2+0.7f,bitmapRounded.getWidth() / 2+0.1f, paint);
			contactPicture.setImageBitmap(bitmapRounded);

			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			bm.compress(Bitmap.CompressFormat.PNG,COMPRESSOR_QUALITY, outStream);
			photoToAdd = outStream.toByteArray();
		}
	}


	private void setContactPhoto(){
		ContentResolver cr = getActivity().getContentResolver();
		Uri updateUri = ContactsContract.Data.CONTENT_URI;

		if(photoToAdd != null){
			//New contact
			if(isNewContact){
				ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
								.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, contactID)
								.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
								.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoToAdd)
								.build()
				);
			} else { //update contact picture
				String w = ContactsContract.Data.CONTACT_ID + "='"
						+ contact.getID() + "' AND "
						+ ContactsContract.Data.MIMETYPE + " = '"
						+ ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";

				Cursor queryCursor = cr.query(updateUri,new String[] { ContactsContract.Data._ID}, w, null, null);
				if (queryCursor == null) {
					try {
						throw new SyncFailedException("EE");
					} catch (SyncFailedException e) {
						e.printStackTrace();
					}
				} else {
					if(contact.getPhoto() == null) {
						String rawContactId = ContactsManager.getInstance().findRawContactID(cr,String.valueOf(contactID));
						ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
										.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
										.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
										.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoToAdd)
										.build()
						);
					}

					if (queryCursor.moveToFirst()) { // otherwise no photo
						int colIdx = queryCursor.getColumnIndex(ContactsContract.Data._ID);
						long id = queryCursor.getLong(colIdx);


						ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
								.withSelection(ContactsContract.Data._ID + "= ?",new String[] { String.valueOf(id) })
								.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
								.withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoToAdd)
								.build());
					}
					queryCursor.close();
				}
			}
		}
	}
	
	private LinearLayout initNumbersFields(final Contact contact) {
		LinearLayout controls = (LinearLayout) view.findViewById(R.id.controls_numbers);
		controls.removeAllViews();
		
		if (contact != null) {
			for (String numberOrAddress : contact.getNumbersOrAddresses()) {
				boolean isSip = LinphoneUtils.isStrictSipAddress(numberOrAddress) || !LinphoneUtils.isNumberAddress(numberOrAddress);
				if(!isSip) {
					View view = displayNumberOrAddress(controls, numberOrAddress);
					if (view != null)
						controls.addView(view);
				}
			}
		}

		if (newSipOrNumberToAdd != null) {
			boolean isSip = LinphoneUtils.isStrictSipAddress(newSipOrNumberToAdd) || !LinphoneUtils.isNumberAddress(newSipOrNumberToAdd);
			if(!isSip) {
				View view = displayNumberOrAddress(controls, newSipOrNumberToAdd);
				if (view != null)
					controls.addView(view);
			}
		}

		if (controls.getChildCount() == 0) {
			addEmptyRowToAllowNewNumberOrAddress(controls,false);
		}

		return controls;
	}

	private LinearLayout initSipAddressFields(final Contact contact) {
		LinearLayout controls = (LinearLayout) view.findViewById(R.id.controls_sip_address);
		controls.removeAllViews();

		if (contact != null) {
			for (String numberOrAddress : contact.getNumbersOrAddresses()) {
				boolean isSip = LinphoneUtils.isStrictSipAddress(numberOrAddress) || !LinphoneUtils.isNumberAddress(numberOrAddress);
				if(isSip) {
					View view = displayNumberOrAddress(controls, numberOrAddress);
					if (view != null)
						controls.addView(view);
				}
			}
		}

		if (newSipOrNumberToAdd != null) {
			boolean isSip = LinphoneUtils.isStrictSipAddress(newSipOrNumberToAdd) || !LinphoneUtils.isNumberAddress(newSipOrNumberToAdd);
			if(isSip) {
				View view = displayNumberOrAddress(controls, newSipOrNumberToAdd);
				if (view != null)
					controls.addView(view);
			}
		}

		if (controls.getChildCount() == 0) {
			addEmptyRowToAllowNewNumberOrAddress(controls,true);
		}

		return controls;
	}
	
	private View displayNumberOrAddress(final LinearLayout controls, String numberOrAddress) {
		return displayNumberOrAddress(controls, numberOrAddress, false);
	}
	
	@SuppressLint("InflateParams")
	private View displayNumberOrAddress(final LinearLayout controls, String numberOrAddress, boolean forceAddNumber) {
		boolean isSip = LinphoneUtils.isStrictSipAddress(numberOrAddress) || !LinphoneUtils.isNumberAddress(numberOrAddress);
		
		if (isSip) {
			if (firstSipAddressIndex == -1) {
				firstSipAddressIndex = controls.getChildCount();
			}
			numberOrAddress = numberOrAddress.replace("sip:", "");
		}
		if ((getResources().getBoolean(R.bool.hide_phone_numbers_in_editor) && !isSip) || (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor) && isSip)) {
			if (forceAddNumber)
				isSip = !isSip; // If number can't be displayed because we hide a sort of number, change that category
			else
				return null;
		}
		
		NewOrUpdatedNumberOrAddress tempNounoa;
		if (forceAddNumber) {
			tempNounoa = new NewOrUpdatedNumberOrAddress(isSip);
		} else {
			if(isNewContact || newSipOrNumberToAdd != null) {
				tempNounoa = new NewOrUpdatedNumberOrAddress(isSip, numberOrAddress);
			} else {
				tempNounoa = new NewOrUpdatedNumberOrAddress(numberOrAddress, isSip);
			}
		}
		final NewOrUpdatedNumberOrAddress nounoa = tempNounoa;
		numbersAndAddresses.add(nounoa);
		
		final View view = inflater.inflate(R.layout.contact_edit_row, null);

		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		noa.setInputType(isSip ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS : InputType.TYPE_CLASS_PHONE);
		noa.setText(numberOrAddress);
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setNewNumberOrAddress(noa.getText().toString());
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		if (forceAddNumber) {
			nounoa.setNewNumberOrAddress(noa.getText().toString());
		}
		
		ImageView delete = (ImageView) view.findViewById(R.id.delete_field);
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				nounoa.delete();
				numbersAndAddresses.remove(nounoa);
				view.setVisibility(View.GONE);

			}
		});
		return view;
	}
	
	@SuppressLint("InflateParams")
	private void addEmptyRowToAllowNewNumberOrAddress(final LinearLayout controls, final boolean isSip) {
		final View view = inflater.inflate(R.layout.contact_edit_row, null);
		final NewOrUpdatedNumberOrAddress nounoa = new NewOrUpdatedNumberOrAddress(isSip);
		
		final EditText noa = (EditText) view.findViewById(R.id.numoraddr);
		numbersAndAddresses.add(nounoa);
		noa.setHint(isSip ? getString(R.string.sip_address) : getString(R.string.phone_number));
		noa.setInputType(isSip ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS : InputType.TYPE_CLASS_PHONE);
		noa.requestFocus();
		noa.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				nounoa.setNewNumberOrAddress(noa.getText().toString());
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		final ImageView delete = (ImageView) view.findViewById(R.id.delete_field);
		delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				nounoa.delete();
				numbersAndAddresses.remove(nounoa);
				view.setVisibility(View.GONE);
			}

		});


		controls.addView(view);
	}
	
	private String findContactFirstName(String contactID) {
		Cursor c = getActivity().getContentResolver().query(ContactsContract.Data.CONTENT_URI,
		          new String[]{ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME},
		          ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
		          new String[]{contactID, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
			}
			c.close();
			return result;
		}
		return null;
	}
	
	private String findContactLastName(String contactID) {
		Cursor c = getActivity().getContentResolver().query(ContactsContract.Data.CONTENT_URI,
		          new String[]{ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME},
		          ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
		          new String[]{contactID, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
		if (c != null) {
			String result = null;
			if (c.moveToFirst()) {
				result = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
			}
			c.close();
			return result;
		}
		return null;
	}

	private void addLinphoneFriendIfNeeded(){
		for (NewOrUpdatedNumberOrAddress numberOrAddress : numbersAndAddresses) {
			if(numberOrAddress.newNumberOrAddress != null && numberOrAddress.isSipAddress) {
				if(isNewContact){
					Contact c = contactsManager.findContactWithDisplayName(ContactsManager.getInstance().getDisplayName(firstName.getText().toString(), lastName.getText().toString()));
					if (c != null && !contactsManager.isContactHasAddress(c, numberOrAddress.newNumberOrAddress)) {
						contactsManager.createNewFriend(c, numberOrAddress.newNumberOrAddress);
					}
				} else {
					if (!contactsManager.isContactHasAddress(contact, numberOrAddress.newNumberOrAddress)){
						if (numberOrAddress.oldNumberOrAddress == null) {
							contactsManager.createNewFriend(contact, numberOrAddress.newNumberOrAddress);
						} else {
							if (contact.hasFriends())
								contactsManager.updateFriend(numberOrAddress.oldNumberOrAddress, numberOrAddress.newNumberOrAddress);
						}
					}
				}
			}
		}
	}

	private void removeLinphoneTagIfNeeded(){
		if(!isNewContact) {
			boolean areAllSipFielsEmpty = true;
			for (NewOrUpdatedNumberOrAddress nounoa : numbersAndAddresses) {
				if (!nounoa.isSipAddress && (nounoa.oldNumberOrAddress != null && !nounoa.oldNumberOrAddress.equals("") || nounoa.newNumberOrAddress != null && !nounoa.newNumberOrAddress.equals(""))) {
					areAllSipFielsEmpty = false;
					break;
				}
			}
			if (areAllSipFielsEmpty && contactsManager.findRawLinphoneContactID(contact.getID()) != null) {
				contactsManager.removeLinphoneContactTag(contact);
			}
		}
	}
	
	class NewOrUpdatedNumberOrAddress {
		private String oldNumberOrAddress;
		private String newNumberOrAddress;
		private boolean isSipAddress;
		
		public NewOrUpdatedNumberOrAddress() {
			oldNumberOrAddress = null;
			newNumberOrAddress = null;
			isSipAddress = false;
		}
		
		public NewOrUpdatedNumberOrAddress(boolean isSip) {
			oldNumberOrAddress = null;
			newNumberOrAddress = null;
			isSipAddress = isSip;
		}
		
		public NewOrUpdatedNumberOrAddress(String old, boolean isSip) {
			oldNumberOrAddress = old;
			newNumberOrAddress = null;
			isSipAddress = isSip;
		}
		
		public NewOrUpdatedNumberOrAddress(boolean isSip, String newSip) {
			oldNumberOrAddress = null;
			newNumberOrAddress = newSip;
			isSipAddress = isSip;
		}
		
		public void setNewNumberOrAddress(String newN) {
			newNumberOrAddress = newN;
		}
		
		public void save() {
			if (newNumberOrAddress == null || newNumberOrAddress.equals(oldNumberOrAddress))
				return;

			if (oldNumberOrAddress == null) {
				// New number to add
				addNewNumber();
			} else {
				// Old number to update
				updateNumber();
			}
		}
		
		public void delete() {
			if(contact != null) {
				if (isSipAddress) {
					if (contact.hasFriends()) {
						ContactsManager.getInstance().removeFriend(oldNumberOrAddress);
					} else {
						Compatibility.deleteSipAddressFromContact(ops, oldNumberOrAddress, String.valueOf(contactID));
					}
					if (getResources().getBoolean(R.bool.use_linphone_tag)) {
						Compatibility.deleteLinphoneContactTag(ops, oldNumberOrAddress, contactsManager.findRawLinphoneContactID(String.valueOf(contactID)));
					}
				} else {
					String select = ContactsContract.Data.CONTACT_ID + "=? AND "
							+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND "
							+ ContactsContract.CommonDataKinds.Phone.NUMBER + "=?";
					String[] args = new String[]{String.valueOf(contactID), oldNumberOrAddress};

					ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
									.withSelection(select, args)
									.build()
					);
				}
			}
		}
		
		private void addNewNumber() {
			if (newNumberOrAddress == null || newNumberOrAddress.length() == 0) {
				return;
			}
			
			if (isNewContact) {
				if (isSipAddress) {
					if (newNumberOrAddress.startsWith("sip:"))
						newNumberOrAddress = newNumberOrAddress.substring(4);
					if(!newNumberOrAddress.contains("@")) {
						//Use default proxy config domain if it exists
						LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
						if(lpc != null){
							newNumberOrAddress = newNumberOrAddress + "@" + lpc.getDomain();
						} else {
							newNumberOrAddress = newNumberOrAddress + "@" + getResources().getString(R.string.default_domain);
						}
					}
					Compatibility.addSipAddressToContact(getActivity(), ops, newNumberOrAddress);
				} else {
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
				        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,  ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
						.withValue(ContactsContract.CommonDataKinds.Phone.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
				}
			} else {
				String rawContactId = contactsManager.findRawContactID(getActivity().getContentResolver(),String.valueOf(contactID));
				if (isSipAddress) {
					if (newNumberOrAddress.startsWith("sip:"))
						newNumberOrAddress = newNumberOrAddress.substring(4);
					if(!newNumberOrAddress.contains("@")) {
						//Use default proxy config domain if it exists
						LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
						if(lpc != null){
							newNumberOrAddress = newNumberOrAddress + "@" + lpc.getDomain();
						} else {
							newNumberOrAddress = newNumberOrAddress + "@" + getResources().getString(R.string.default_domain);
						}
					}

					Compatibility.addSipAddressToContact(getActivity(), ops, newNumberOrAddress, rawContactId);
					if (getResources().getBoolean(R.bool.use_linphone_tag)) {
						Compatibility.addLinphoneContactTag(getActivity(), ops, newNumberOrAddress, contactsManager.findRawLinphoneContactID(String.valueOf(contactID)));
					}
				} else {
					ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)         
					    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)       
				        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
				        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
				        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,  ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
				        .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, getString(R.string.addressbook_label))
				        .build()
				    );
				}
			}
		}
		
		private void updateNumber() {
			if (newNumberOrAddress == null || newNumberOrAddress.length() == 0) {
				return;
			}
			
			if (isSipAddress) {
				if (newNumberOrAddress.startsWith("sip:"))
					newNumberOrAddress = newNumberOrAddress.substring(4);
				if(!newNumberOrAddress.contains("@")) {
					//Use default proxy config domain if it exists
					LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
					if(lpc != null){
						newNumberOrAddress = newNumberOrAddress + "@" + lpc.getDomain();
					} else {
						newNumberOrAddress = newNumberOrAddress + "@" + getResources().getString(R.string.default_domain);
					}
				}
				Compatibility.updateSipAddressForContact(ops, oldNumberOrAddress, newNumberOrAddress, String.valueOf(contactID));
				if (getResources().getBoolean(R.bool.use_linphone_tag)) {
					Compatibility.updateLinphoneContactTag(getActivity(), ops, newNumberOrAddress, oldNumberOrAddress, contactsManager.findRawLinphoneContactID(String.valueOf(contactID)));
				}
			} else {
				String select = ContactsContract.Data.CONTACT_ID + "=? AND " 
						+ ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE +  "' AND " 
						+ ContactsContract.CommonDataKinds.Phone.NUMBER + "=?"; 
				String[] args = new String[] { String.valueOf(contactID), oldNumberOrAddress };   
				
	            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI) 
	        		.withSelection(select, args) 
	                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
	                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumberOrAddress)
	                .build()
	            );
			}
		}
	}
}