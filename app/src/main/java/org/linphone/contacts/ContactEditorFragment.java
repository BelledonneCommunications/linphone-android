package org.linphone.contacts;

/*
ContactEditorFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class ContactEditorFragment extends Fragment {
    private static final int ADD_PHOTO = 1337;
    private static final int PHOTO_SIZE = 128;

    private View mView;
    private ImageView mCancel, mDeleteContact, mOk;
    private ImageView mAddNumber, mAddSipAddress, mContactPicture;
    private LinearLayout mPhoneNumbersSection, mSipAddressesSection;
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
            Serializable obj = getArguments().getSerializable("Contact");
            if (obj != null) {
                mContact = (LinphoneContact) obj;
                mContact.createRawLinphoneContactFromExistingAndroidContactIfNeeded(
                        mContact.getFullName());
                mIsNewContact = false;
                if (getArguments().getString("NewSipAdress") != null) {
                    mNewSipOrNumberToAdd = getArguments().getString("NewSipAdress");
                }
                if (getArguments().getString("NewDisplayName") != null) {
                    mNewDisplayName = getArguments().getString("NewDisplayName");
                }
            } else if (getArguments().getString("NewSipAdress") != null) {
                mNewSipOrNumberToAdd = getArguments().getString("NewSipAdress");
                if (getArguments().getString("NewDisplayName") != null) {
                    mNewDisplayName = getArguments().getString("NewDisplayName");
                }
            }
        }

        mView = inflater.inflate(R.layout.contact_edit, container, false);

        mPhoneNumbersSection = mView.findViewById(R.id.phone_numbers);
        if (getResources().getBoolean(R.bool.hide_phone_numbers_in_editor)
                || !ContactsManager.getInstance().hasReadContactsAccess()) {
            // Currently linphone friends don't support phone mNumbers, so hide them
            mPhoneNumbersSection.setVisibility(View.GONE);
        }

        mSipAddressesSection = mView.findViewById(R.id.sip_addresses);
        if (getResources().getBoolean(R.bool.hide_sip_addresses_in_editor)) {
            mSipAddressesSection.setVisibility(View.GONE);
        }

        mDeleteContact = mView.findViewById(R.id.delete_contact);

        mCancel = mView.findViewById(R.id.cancel);
        mCancel.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getFragmentManager().popBackStackImmediate();
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
                                if (nounoa.getValue() != null && !nounoa.getValue().equals("")) {
                                    areAllFielsEmpty = false;
                                    break;
                                }
                            }
                            if (areAllFielsEmpty) {
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
                            mContact.setPhoto(mPhotoToAdd);
                        }

                        for (LinphoneNumberOrAddress noa : mNumbersAndAddresses) {
                            if (noa.getValue() == null || noa.getValue().isEmpty()) {
                                if (noa.getOldValue() != null && !noa.getOldValue().isEmpty()) {
                                    Log.i("[Contact Editor] Removing number " + noa.getOldValue());
                                    mContact.removeNumberOrAddress(noa);
                                }
                            } else {
                                if (noa.getOldValue() != null
                                        && noa.getOldValue().equals(noa.getValue())) {
                                    Log.i(
                                            "[Contact Editor] Keeping existing number "
                                                    + noa.getValue());
                                    continue;
                                }

                                if (noa.isSIPAddress()) {
                                    noa.setValue(
                                            LinphoneUtils.getFullAddressFromUsername(
                                                    noa.getValue()));
                                }
                                Log.i("[Contact Editor] Adding new number " + noa.getValue());
                                mContact.addOrUpdateNumberOrAddress(noa);
                            }
                        }

                        if (!mOrganization.getText().toString().isEmpty() || !mIsNewContact) {
                            mContact.setOrganization(mOrganization.getText().toString(), true);
                        }

                        mContact.save();

                        if (mIsNewContact) {
                            // Ensure fetch will be done so the new contact appears in the contacts
                            // list: contacts content observer may not be notified if contacts sync
                            // is disabled at system level
                            ContactsManager.getInstance().fetchContactsAsync();
                        }

                        getFragmentManager().popBackStackImmediate();
                        if (mIsNewContact || LinphoneActivity.instance().isTablet()) {
                            LinphoneActivity.instance().displayContact(mContact, false);
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
                                            LinphoneActivity.instance()
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
        boolean isOrgVisible = getResources().getBoolean(R.bool.display_contact_organization);
        if (!isOrgVisible) {
            mOrganization.setVisibility(View.GONE);
            mView.findViewById(R.id.contactOrganizationTitle).setVisibility(View.GONE);
        } else {
            if (!mIsNewContact) {
                mOrganization.setText(mContact.getOrganization());
            }
        }

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

            mDeleteContact.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final Dialog dialog =
                                    LinphoneActivity.instance()
                                            .displayDialog(getString(R.string.delete_text));
                            Button delete = dialog.findViewById(R.id.dialog_delete_button);
                            Button cancel = dialog.findViewById(R.id.dialog_cancel_button);

                            delete.setOnClickListener(
                                    new OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            mContact.delete();
                                            LinphoneActivity.instance().displayContacts(false);
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
            mDeleteContact.setVisibility(View.INVISIBLE);
        }

        mContactPicture = mView.findViewById(R.id.contact_picture);
        if (mContact != null) {
            ContactAvatar.displayAvatar(mContact, mView.findViewById(R.id.avatar_layout));
        } else {
            ContactAvatar.displayAvatar("", mView.findViewById(R.id.avatar_layout));
        }

        mContactPicture.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        pickImage();
                        LinphoneActivity.instance().checkAndRequestCameraPermission();
                    }
                });

        mNumbersAndAddresses = new ArrayList<>();
        mSipAddresses = initSipAddressFields(mContact);
        mNumbers = initNumbersFields(mContact);

        mAddSipAddress = mView.findViewById(R.id.add_address_field);
        if (getResources().getBoolean(R.bool.allow_only_one_sip_address)) {
            mAddSipAddress.setVisibility(View.GONE);
        }
        mAddSipAddress.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        addEmptyRowToAllowNewNumberOrAddress(mSipAddresses, true);
                    }
                });

        mAddNumber = mView.findViewById(R.id.add_number_field);
        if (getResources().getBoolean(R.bool.allow_only_one_phone_number)) {
            mAddNumber.setVisibility(View.GONE);
        }
        mAddNumber.setOnClickListener(
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
    public void onResume() {
        super.onResume();

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

    private void pickImage() {
        mPickedPhotoForContactUri = null;
        final List<Intent> cameraIntents = new ArrayList<>();
        final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file =
                new File(
                        FileUtils.getStorageDirectory(LinphoneActivity.instance()),
                        getString(R.string.temp_photo_name));
        mPickedPhotoForContactUri = Uri.fromFile(file);
        captureIntent.putExtra("outputX", PHOTO_SIZE);
        captureIntent.putExtra("outputY", PHOTO_SIZE);
        captureIntent.putExtra("aspectX", 0);
        captureIntent.putExtra("aspectY", 0);
        captureIntent.putExtra("scale", true);
        captureIntent.putExtra("return-data", false);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPickedPhotoForContactUri);
        cameraIntents.add(captureIntent);

        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        final Intent chooserIntent =
                Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
        chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[] {}));

        startActivityForResult(chooserIntent, ADD_PHOTO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                Bitmap bm = (Bitmap) data.getExtras().get("data");
                editContactPicture(null, bm);
            } else if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                try {
                    Bitmap selectedImage =
                            MediaStore.Images.Media.getBitmap(
                                    LinphoneManager.getInstance().getContext().getContentResolver(),
                                    selectedImageUri);
                    selectedImage =
                            Bitmap.createScaledBitmap(selectedImage, PHOTO_SIZE, PHOTO_SIZE, false);
                    editContactPicture(null, selectedImage);
                } catch (IOException e) {
                    Log.e(e);
                }
            } else if (mPickedPhotoForContactUri != null) {
                String filePath = mPickedPhotoForContactUri.getPath();
                editContactPicture(filePath, null);
            } else {
                File file =
                        new File(
                                FileUtils.getStorageDirectory(LinphoneActivity.instance()),
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
        scaledPhoto.compress(Bitmap.CompressFormat.PNG, 0, stream);
        mContactPicture.setImageBitmap(scaledPhoto);
        mPhotoToAdd = stream.toByteArray();
    }

    private int getThumbnailSize() {
        int value = -1;
        Cursor c =
                LinphoneActivity.instance()
                        .getContentResolver()
                        .query(
                                DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                                new String[] {DisplayPhoto.THUMBNAIL_MAX_DIM},
                                null,
                                null,
                                null);
        try {
            c.moveToFirst();
            value = c.getInt(0);
        } catch (Exception e) {
            Log.e(e);
        }
        c.close();
        return value;
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
        String displayNumberOrAddress = numberOrAddress;
        if (isSIP) {
            if (mFirstSipAddressIndex == -1) {
                mFirstSipAddressIndex = controls.getChildCount();
            }
            displayNumberOrAddress =
                    LinphoneUtils.getDisplayableUsernameFromAddress(numberOrAddress);
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

        final View view = mInflater.inflate(R.layout.contact_edit_row, null);

        final EditText noa = view.findViewById(R.id.numoraddr);
        if (!isSIP) {
            noa.setInputType(InputType.TYPE_CLASS_PHONE);
        }
        noa.setText(displayNumberOrAddress);
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
        final View view = mInflater.inflate(R.layout.contact_edit_row, null);
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
