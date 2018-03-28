/*
ImdnFragment.java
Copyright (C) 2010-2018  Belledonne Communications, Grenoble, France

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

package org.linphone.chat;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.ChatMessage;
import org.linphone.mediastream.Log;

import java.io.FileNotFoundException;
import java.io.IOException;

public class ImdnFragment extends Fragment {
	private LayoutInflater mInflater;
	private LinearLayout mRead, mDelivered, mUndelivered;
	private ImageView mBackButton;
	private ChatBubbleViewHolder mBubble;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mInflater = inflater;
		View view = mInflater.inflate(R.layout.chat_imdn, container, false);

		mBackButton = view.findViewById(R.id.back);
		mBackButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LinphoneActivity.instance().onBackPressed();
			}
		});

		mRead = view.findViewById(R.id.read_layout);
		mDelivered = view.findViewById(R.id.delivered_layout);
		mUndelivered = view.findViewById(R.id.undelivered_layout);

		mBubble = new ChatBubbleViewHolder(view.findViewById(R.id.bubble));
		mBubble.eventLayout.setVisibility(View.GONE);
		mBubble.bubbleLayout.setVisibility(View.VISIBLE);
		mBubble.delete.setVisibility(View.GONE);
		mBubble.messageText.setVisibility(View.GONE);
		mBubble.messageImage.setVisibility(View.GONE);
		mBubble.fileTransferLayout.setVisibility(View.GONE);
		mBubble.fileName.setVisibility(View.GONE);
		mBubble.openFileButton.setVisibility(View.GONE);
		mBubble.messageStatus.setVisibility(View.INVISIBLE);
		mBubble.messageSendingInProgress.setVisibility(View.GONE);
		mBubble.imdmLayout.setVisibility(View.INVISIBLE);
		mBubble.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());

		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		layoutParams.setMargins(100, 10, 10, 10);
		mBubble.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
		Compatibility.setTextAppearance(mBubble.contactName, getActivity(), R.style.font3);
		Compatibility.setTextAppearance(mBubble.fileTransferAction, getActivity(), R.style.font15);
		mBubble.fileTransferAction.setBackgroundResource(R.drawable.resizable_confirm_delete_button);
		mBubble.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask_outgoing);

		ChatMessage message = null; //TODO
		if (message.hasTextContent()) {
			String msg = message.getTextContent();
			Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
			mBubble.messageText.setText(text);
			mBubble.messageText.setMovementMethod(LinkMovementMethod.getInstance());
			mBubble.messageText.setVisibility(View.VISIBLE);
		}

		String appData = message.getAppdata();
		if (appData != null) { // Something to display
			mBubble.fileName.setVisibility(View.VISIBLE);
			mBubble.fileName.setText(LinphoneUtils.getNameFromFilePath(appData));
			if (LinphoneUtils.isExtensionImage(appData)) {
				mBubble.messageImage.setVisibility(View.VISIBLE);
				mBubble.messageImage.setImageBitmap(loadBitmap(appData));
				mBubble.messageImage.setTag(appData);
			} else {
				mBubble.openFileButton.setVisibility(View.VISIBLE);
				mBubble.openFileButton.setTag(appData);
			}
		}

		// TODO: real values
		View v = mInflater.inflate(R.layout.chat_imdn_cell, container, false);
		v.findViewById(R.id.separator).setVisibility(View.GONE);
		((TextView)v.findViewById(R.id.time)).setText("Aujourd'hui - 17h58");
		((TextView)v.findViewById(R.id.name)).setText("Albert");
		mRead.addView(v);
		v = mInflater.inflate(R.layout.chat_imdn_cell, container, false);
		((TextView)v.findViewById(R.id.time)).setText("Aujourd'hui - 17h52");
		((TextView)v.findViewById(R.id.name)).setText("Charlotte");
		mRead.addView(v);
		v = mInflater.inflate(R.layout.chat_imdn_cell, container, false);
		v.findViewById(R.id.separator).setVisibility(View.GONE);
		((TextView)v.findViewById(R.id.time)).setText("Aujourd'hui - 17h36");
		((TextView)v.findViewById(R.id.name)).setText("Fabrice");
		mDelivered.addView(v);
		v = mInflater.inflate(R.layout.chat_imdn_cell, container, false);
		v.findViewById(R.id.separator).setVisibility(View.GONE);
		((TextView)v.findViewById(R.id.name)).setText("Heloïse");
		mUndelivered.addView(v);

		mBubble.contactName.setText("10/07/2017 - 17h35 - Violaine");
		mBubble.messageText.setText("Lorem ipsum dolor sit aet patetris");
		mBubble.messageText.setMovementMethod(LinkMovementMethod.getInstance());
		mBubble.messageText.setVisibility(View.VISIBLE);
		// End of todo

		return view;
	}

	private Bitmap loadBitmap(String path) {
		Bitmap bm = null;
		Bitmap thumbnail = null;
		if (LinphoneUtils.isExtensionImage(path)) {
			if (path.startsWith("content")) {
				try {
					bm = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), Uri.parse(path));
				} catch (FileNotFoundException e) {
					Log.e(e);
				} catch (IOException e) {
					Log.e(e);
				}
			} else {
				bm = BitmapFactory.decodeFile(path);
			}

			// Rotate the bitmap if possible/needed, using EXIF data
			try {
				android.graphics.Bitmap bm_tmp;
				ExifInterface exif = new ExifInterface(path);
				int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
				Matrix matrix = new Matrix();
				if (pictureOrientation == 6) {
					matrix.postRotate(90);
				} else if (pictureOrientation == 3) {
					matrix.postRotate(180);
				} else if (pictureOrientation == 8) {
					matrix.postRotate(270);
				}
				bm_tmp = android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
				if (bm_tmp != bm) {
					bm.recycle();
					bm = bm_tmp;
				}
			} catch (Exception e) {
				Log.e(e);
			}

			if (bm != null) {
				thumbnail = ThumbnailUtils.extractThumbnail(bm, 500, 500);
				bm.recycle();
			}
			return thumbnail;
		} else {
			return BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.avatar);
		}
	}
}
