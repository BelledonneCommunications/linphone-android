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
package org.linphone.contacts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;
import org.linphone.contacts.views.ContactAvatar;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.FileUtils;
import org.linphone.utils.ImageUtils;
import org.linphone.utils.LinphoneUtils;

public class ContactEditorFragment extends Fragment {
    private static final int ADD_PHOTO = 1337;
    private static final int PHOTO_SIZE = 128;

    private View mView;
    private ImageView mOk;
    private ImageView mContactPicture;
    private EditText mFirstName, mLastName, mOrganization;
    private LayoutInflater mInflater;
    private boolean mIsNewContact;
    private LinphoneContact mContact;
    private List<LinphoneNumberOrAddress> mNumbersAndAddresses;
    private int mFirstSipAddressIndex = -1;
    private LinearLayout mSipAddresses, mNumbers;
    private String mNewSipOrNumberToAdd, mNewDisplayName;
    private Uri mPickedPhotoForContactUri;
    private byte[] mPhotoToAdd;

    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mInflater = inflater;

        mContact = null;
        mIsNewContact = true;

        if (getArguments() != null) {
            mContact = (LinphoneContact) getArguments().getSerializable("Contact");
            if (getArguments().containsKey("SipUri")) {
                mNewSipOrNumberToAdd = getArguments().getString("SipUri");
            }
            if (getArguments().containsKey("DisplayName")) {
                mNewDisplayName = getArguments().getString("DisplayName");
            }
        } else if (savedInstanceState != null) {
            mContact = (LinphoneContact) savedInstanceState.get("Contact");
            mNewSipOrNumberToAdd = savedInstanceState.getString("SipUri");
            mNewDisplayName = savedInstanceState.getString("DisplayName");
        }
        if (mContact != null) {
            mContact.createRawLinphoneContactFromExistingAndroidContactIfNeeded();
            mIsNewContact = false;
        }

        mView = inflater.inflate(R.layout.contact_edit, container, false);

        LinearLayout phoneNumbersSection = mView.findViewById(R.id.phone_numbers);
        if (getResources().getBoolean(R.bool.hide_phone_numbers_in_editor)
                || !ContactsManager.getInstance().hasReadContactsAccess()) {
            // Currently linphone friends don't support phone mNumbers, so hide them
            phoneNumbersSection.setVisibility(View.GONE);
        }

        LinearLayout sipAddressesSection = mView.findViewById(R.id.sip_addresses);
        if (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor)) {
            sipAddressesSection.setVisibility(View.GONE);
        }

        ImageView deleteContact = mView.findViewById(R.id.delete_contact);

        ImageView cancel = mView.findViewById(R.id.cancel);
        cancel.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ContactsActivity) getActivity()).goBack();
                    }
                });

        mOk = mView.findViewById(R.id.ok);
        mOk.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mIsNewContact) {
                            boolean areAllFielsEmpty = true;
                            for (LinphoneNumberOrAddress nounoa : mNumbersAndAddresses) {
                                String value = nounoa.getValue();
                                if (value != null && !value.trim().isEmpty()) {
                                    areAllFielsEmpty = false;
                                    break;
                                }
                            }
                            if (areAllFielsEmpty) {
                                Log.i(
                                        "[Contact Editor] All SIP and phone fields are empty, aborting");
                                getFragmentManager().popBackStackImmediate();
                                return;
                            }
                            mContact = LinphoneContact.createContact();
                        }

                        mContact.setFirstNameAndLastName(
                                mFirstName.getText().toString(),
                                mLastName.getText().toString(),
                                true);

                        if (mPhotoToAdd != null) {
                            Log.i("[Contact Editor] Found picture to set to contact");
                            mContact.setPhoto(mPhotoToAdd);
                        }

                        for (LinphoneNumberOrAddress noa : mNumbersAndAddresses) {
                            String value = noa.getValue();
                            String oldValue = noa.getOldValue();

                            if (value == null || value.trim().isEmpty()) {
                                if (oldValue != null && !oldValue.isEmpty()) {
                                    Log.i("[Contact Editor] Removing number: ", oldValue);
                                    mContact.removeNumberOrAddress(noa);
                                }
                            } else {
                                if (oldValue != null && oldValue.equals(value)) {
                                    Log.i("[Contact Editor] Keeping existing number: ", value);
                                    continue;
                                }

                                if (noa.isSIPAddress()) {
                                    noa.setValue(LinphoneUtils.getFullAddressFromUsername(value));
                                }
                                Log.i("[Contact Editor] Adding new number: ", value);

                                mContact.addOrUpdateNumberOrAddress(noa);
                            }
                        }

                        if (!mOrganization.getText().toString().isEmpty() || !mIsNewContact) {
                            Log.i("[Contact Editor] Setting organization field: ", mOrganization);
                            mContact.setOrganization(mOrganization.getText().toString(), true);
                        }

                        mContact.save();

                        if (mIsNewContact) {
                            // Ensure fetch will be done so the new contact appears in the contacts
                            // list: contacts content observer may not be notified if contacts sync
                            // is disabled at system level
                            Log.i(
                                    "[Contact Editor] New contact created, starting fetch contacts task");
                            ContactsManager.getInstance().fetchContactsAsync();
                        }

                        getFragmentManager().popBackStack();
                        if (mIsNewContact || getResources().getBoolean(R.bool.isTablet)) {
                            ((ContactsActivity) getActivity()).showContactDetails(mContact);
                        }
                    }
                });

        mLastName = mView.findViewById(R.id.contactLastName);
        // Hack to display keyboard when touching focused edittext on Nexus One
        if (Version.sdkStrictlyBelow(Version.API11_HONEYCOMB_30)) {
            mLastName.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            InputMethodManager imm =
                                    (InputMethodManager)
                                            getActivity()
                                                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                        }
                    });
        }
        mLastName.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mOk.setEnabled(
                                mLastName.getText().length() > 0
                                        || mFirstName.getText().length() > 0);
                    }

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        mFirstName = mView.findViewById(R.id.contactFirstName);
        mFirstName.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mOk.setEnabled(
                                mFirstName.getText().length() > 0
                                        || mLastName.getText().length() > 0);
                    }

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        mOrganization = mView.findViewById(R.id.contactOrganization);

        if (!mIsNewContact) {
            String fn = mContact.getFirstName();
            String ln = mContact.getLastName();
            if (fn != null || ln != null) {
                mFirstName.setText(fn);
                mLastName.setText(ln);
            } else {
                mLastName.setText(mContact.getFullName());
                mFirstName.setText("");
            }

            deleteContact.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final Dialog dialog =
                                    ((ContactsActivity) getActivity())
                                            .displayDialog(getString(R.string.delete_text));
                            Button delete = dialog.findViewById(R.id.dialog_delete_button);
                            Button cancel = dialog.findViewById(R.id.dialog_cancel_button);

                            delete.setOnClickListener(
                                    new OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            mContact.delete();
                                            ((ContactsActivity) getActivity()).goBack();
                                            dialog.dismiss();
                                        }
                                    });

                            cancel.setOnClickListener(
                                    new OnClickListener() {
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

        mContactPicture = mView.findViewById(R.id.contact_picture);
        mContactPicture.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ContactsActivity contactsActivity = ((ContactsActivity) getActivity());
                        if (contactsActivity != null) {
                            String[] permissions = {
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA
                            };
                            if (contactsActivity.checkPermissions(permissions)) {
                                pickImage();
                            } else {
                                contactsActivity.requestPermissionsIfNotGranted(permissions);
                            }
                        }
                    }
                });

        mNumbersAndAddresses = new ArrayList<>();

        ImageView addSipAddress = mView.findViewById(R.id.add_address_field);
        if (getResources().getBoolean(R.bool.allow_only_one_sip_address)) {
            addSipAddress.setVisibility(View.GONE);
        }
        addSipAddress.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        addEmptyRowToAllowNewNumberOrAddress(mSipAddresses, true);
                    }
                });

        ImageView addNumber = mView.findViewById(R.id.add_number_field);
        if (getResources().getBoolean(R.bool.allow_only_one_phone_number)) {
            addNumber.setVisibility(View.GONE);
        }
        addNumber.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        addEmptyRowToAllowNewNumberOrAddress(mNumbers, false);
                    }
                });

        mLastName.requestFocus();

        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("Contact", mContact);
        outState.putString("SipUri", mNewSipOrNumberToAdd);
        outState.putString("DisplayName", mNewDisplayName);
    }

    @Override
    public void onResume() {
        super.onResume();

        displayContact();

        // Force hide keyboard
        getActivity()
                .getWindow()
                .setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override
    public void onPause() {
        // Force hide keyboard
        InputMethodManager imm =
                (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getActivity().getCurrentFocus();
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                Bitmap bm = (Bitmap) data.getExtras().get("data");
                editContactPicture(null, bm);
            } else if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                String filePath = FileUtils.getRealPathFromURI(getActivity(), selectedImageUri);
                if (filePath != null) {
                    editContactPicture(filePath, null);
                } else {
                    try {
                        Bitmap selectedImage =
                                MediaStore.Images.Media.getBitmap(
                                        getActivity().getContentResolver(), selectedImageUri);
                        editContactPicture(null, selectedImage);
                    } catch (IOException e) {
                        Log.e("[Contact Editor] IO error: ", e);
                    }
                }
            } else if (mPickedPhotoForContactUri != null) {
                String filePath = mPickedPhotoForContactUri.getPath();
                editContactPicture(filePath, null);
            } else {
                File file =
                        new File(
                                FileUtils.getStorageDirectory(getActivity()),
                                getString(R.string.temp_photo_name));
                if (file.exists()) {
                    mPickedPhotoForContactUri = Uri.fromFile(file);
                    String filePath = mPickedPhotoForContactUri.getPath();
                    editContactPicture(filePath, null);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void displayContact() {
        boolean isOrgVisible = LinphonePreferences.instance().isDisplayContactOrganization();
        if (!isOrgVisible) {
            mOrganization.setVisibility(View.GONE);
            mView.findViewById(R.id.contactOrganizationTitle).setVisibility(View.GONE);
        } else {
            if (!mIsNewContact) {
                mOrganization.setText(mContact.getOrganization());
            }
        }

        if (mPhotoToAdd == null) {
            if (mContact != null) {
                ContactAvatar.displayAvatar(mContact, mView.findViewById(R.id.avatar_layout));
            } else {
                ContactAvatar.displayAvatar("", mView.findViewById(R.id.avatar_layout));
            }
        }

        mSipAddresses = initSipAddressFields(mContact);
        mNumbers = initNumbersFields(mContact);
    }

    private void pickImage() {
        mPickedPhotoForContactUri = null;
        List<Intent> cameraIntents = new ArrayList<>();

        // Handles image & video picking
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");

        // Allows to capture directly from the camera
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file =
                new File(
                        FileUtils.getStorageDirectory(getActivity()),
                        getString(R.string.temp_photo_name_with_date)
                                .replace("%s", System.currentTimeMillis() + ".jpeg"));
        mPickedPhotoForContactUri = Uri.fromFile(file);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPickedPhotoForContactUri);
        cameraIntents.add(captureIntent);

        Intent chooserIntent =
                Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
        chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[] {}));

        startActivityForResult(chooserIntent, ADD_PHOTO);
    }

    private void editContactPicture(String filePath, Bitmap image) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;

        if (image == null) {
            Log.i(
                    "[Contact Editor] Bitmap is null, trying to decode image from file [",
                    filePath,
                    "]");
            image = BitmapFactory.decodeFile(filePath);

            try {
                ExifInterface ei = new ExifInterface(filePath);
                orientation =
                        ei.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                Log.i("[Contact Editor] Exif rotation is ", orientation);
            } catch (IOException e) {
                Log.e("[Contact Editor] Failed to get Exif rotation, error is ", e);
            }
        } else {

        }
        if (image == null) {
            Log.e(
                    "[Contact Editor] Couldn't get bitmap from either filePath [",
                    filePath,
                    "] nor image");
            return;
        }

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                image = ImageUtils.rotateImage(image, 90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                image = ImageUtils.rotateImage(image, 180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                image = ImageUtils.rotateImage(image, 270);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
                // Nothing to do
                break;
            default:
                Log.w("[Contact Editor] Unexpected orientation ", orientation);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        mPhotoToAdd = stream.toByteArray();

        Bitmap roundPicture = ImageUtils.getRoundBitmap(image);
        ContactAvatar.displayAvatar(roundPicture, mView.findViewById(R.id.avatar_layout));
        image.recycle();
    }

    private LinearLayout initNumbersFields(final LinphoneContact contact) {
        LinearLayout controls = mView.findViewById(R.id.controls_numbers);
        controls.removeAllViews();

        if (contact != null) {
            for (LinphoneNumberOrAddress numberOrAddress : contact.getNumbersOrAddresses()) {
                if (!numberOrAddress.isSIPAddress()) {
                    View view = displayNumberOrAddress(controls, numberOrAddress.getValue(), false);
                    if (view != null) controls.addView(view);
                }
            }
        }

        if (mNewSipOrNumberToAdd != null) {
            boolean isSip =
                    LinphoneUtils.isStrictSipAddress(mNewSipOrNumberToAdd)
                            || !LinphoneUtils.isNumberAddress(mNewSipOrNumberToAdd);
            if (!isSip) {
                View view = displayNumberOrAddress(controls, mNewSipOrNumberToAdd, false);
                if (view != null) controls.addView(view);
            }
        }

        if (mNewDisplayName != null) {
            EditText lastNameEditText = mView.findViewById(R.id.contactLastName);
            if (mView != null) lastNameEditText.setText(mNewDisplayName);
        }

        if (controls.getChildCount() == 0) {
            addEmptyRowToAllowNewNumberOrAddress(controls, false);
        }

        return controls;
    }

    private LinearLayout initSipAddressFields(final LinphoneContact contact) {
        LinearLayout controls = mView.findViewById(R.id.controls_sip_address);
        controls.removeAllViews();

        if (contact != null) {
            for (LinphoneNumberOrAddress numberOrAddress : contact.getNumbersOrAddresses()) {
                if (numberOrAddress.isSIPAddress()) {
                    View view = displayNumberOrAddress(controls, numberOrAddress.getValue(), true);
                    if (view != null) controls.addView(view);
                }
            }
        }

        if (mNewSipOrNumberToAdd != null) {
            boolean isSip =
                    LinphoneUtils.isStrictSipAddress(mNewSipOrNumberToAdd)
                            || !LinphoneUtils.isNumberAddress(mNewSipOrNumberToAdd);
            if (isSip) {
                View view = displayNumberOrAddress(controls, mNewSipOrNumberToAdd, true);
                if (view != null) controls.addView(view);
            }
        }

        if (controls.getChildCount() == 0) {
            addEmptyRowToAllowNewNumberOrAddress(controls, true);
        }

        return controls;
    }

    private View displayNumberOrAddress(
            final LinearLayout controls, String numberOrAddress, boolean isSIP) {
        String displayedNumberOrAddress = numberOrAddress;
        if (isSIP) {
            if (mFirstSipAddressIndex == -1) {
                mFirstSipAddressIndex = controls.getChildCount();
            }

            if (getResources()
                    .getBoolean(R.bool.only_show_address_username_if_matches_default_domain)) {
                displayedNumberOrAddress =
                        LinphoneUtils.getDisplayableUsernameFromAddress(numberOrAddress);
            }
        }
        if ((getResources().getBoolean(R.bool.hide_phone_numbers_in_editor) && !isSIP)
                || (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor) && isSIP)) {
            return null;
        }

        LinphoneNumberOrAddress tempNounoa;
        if (mIsNewContact || mNewSipOrNumberToAdd != null) {
            tempNounoa = new LinphoneNumberOrAddress(numberOrAddress, isSIP);
        } else {
            tempNounoa = new LinphoneNumberOrAddress(numberOrAddress, isSIP, numberOrAddress);
        }
        final LinphoneNumberOrAddress nounoa = tempNounoa;
        mNumbersAndAddresses.add(nounoa);

        final View view = mInflater.inflate(R.layout.contact_edit_cell, null);

        final EditText noa = view.findViewById(R.id.numoraddr);
        if (!isSIP) {
            noa.setInputType(InputType.TYPE_CLASS_PHONE);
        }
        noa.setText(displayedNumberOrAddress);
        noa.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        nounoa.setValue(noa.getText().toString());
                    }

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        ImageView delete = view.findViewById(R.id.delete_field);
        if ((getResources().getBoolean(R.bool.allow_only_one_phone_number) && !isSIP)
                || (getResources().getBoolean(R.bool.allow_only_one_sip_address) && isSIP)) {
            delete.setVisibility(View.GONE);
        }
        delete.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mContact != null) {
                            mContact.removeNumberOrAddress(nounoa);
                        }
                        mNumbersAndAddresses.remove(nounoa);
                        view.setVisibility(View.GONE);
                    }
                });
        return view;
    }

    @SuppressLint("InflateParams")
    private void addEmptyRowToAllowNewNumberOrAddress(
            final LinearLayout controls, final boolean isSip) {
        final View view = mInflater.inflate(R.layout.contact_edit_cell, null);
        final LinphoneNumberOrAddress nounoa = new LinphoneNumberOrAddress(null, isSip);

        final EditText noa = view.findViewById(R.id.numoraddr);
        mNumbersAndAddresses.add(nounoa);
        noa.setHint(isSip ? getString(R.string.sip_address) : getString(R.string.phone_number));
        if (!isSip) {
            noa.setInputType(InputType.TYPE_CLASS_PHONE);
            noa.setHint(R.string.phone_number);
        } else {
            noa.setHint(R.string.sip_address);
        }
        noa.requestFocus();
        noa.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        nounoa.setValue(noa.getText().toString());
                    }

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        final ImageView delete = view.findViewById(R.id.delete_field);
        if ((getResources().getBoolean(R.bool.allow_only_one_phone_number) && !isSip)
                || (getResources().getBoolean(R.bool.allow_only_one_sip_address) && isSip)) {
            delete.setVisibility(View.GONE);
        }
        delete.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mNumbersAndAddresses.remove(nounoa);
                        view.setVisibility(View.GONE);
                    }
                });

        controls.addView(view);
    }
}
