/*
ChatEventsAdapter.java
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

package org.linphone.chat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Buffer;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListener;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.mediastream.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class ChatEventsAdapter extends BaseAdapter implements ChatMessageListener {
	private Context mContext;
	GroupChatFragment mFragment;
    private List<EventLog> mHistory;
	private List<LinphoneContact> mParticipants;
    private LayoutInflater mLayoutInflater;
	private Bitmap mDefaultBitmap;

    public ChatEventsAdapter(Context context, GroupChatFragment fragment, LayoutInflater inflater, EventLog[] history, ArrayList<LinphoneContact> participants) {
	    mContext = context;
	    mFragment = fragment;
        mLayoutInflater = inflater;
        mHistory = new ArrayList<>(Arrays.asList(history));
	    mParticipants = participants;
    }

    public void updateHistory(EventLog[] history) {
	    mHistory = new ArrayList<>(Arrays.asList(history));
	    notifyDataSetChanged();
    }

    public void addToHistory(EventLog log) {
	    mHistory.add(log);
	    notifyDataSetChanged();
    }

    public void setContacts(ArrayList<LinphoneContact> participants) {
	    mParticipants = participants;
    }

    @Override
    public int getCount() {
        return mHistory.size();
    }

    @Override
    public Object getItem(int i) {
        return mHistory.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ChatBubbleViewHolder holder;
        if (view != null) {
            holder = (ChatBubbleViewHolder) view.getTag();
        } else {
            view = mLayoutInflater.inflate(R.layout.chat_bubble, null);
            holder = new ChatBubbleViewHolder(view);
            view.setTag(holder);
        }

	    holder.eventLayout.setVisibility(View.GONE);
	    holder.bubbleLayout.setVisibility(View.GONE);
	    holder.delete.setVisibility(View.GONE);
	    holder.messageText.setVisibility(View.GONE);
	    holder.messageImage.setVisibility(View.GONE);
	    holder.fileExtensionLabel.setVisibility(View.GONE);
	    holder.fileNameLabel.setVisibility(View.GONE);
	    holder.fileTransferLayout.setVisibility(View.GONE);
	    holder.fileTransferProgressBar.setProgress(0);
	    holder.fileTransferAction.setEnabled(true);
	    holder.messageStatus.setVisibility(View.INVISIBLE);
	    holder.messageSendingInProgress.setVisibility(View.GONE);
	    holder.imdmLayout.setVisibility(View.INVISIBLE);

	    EventLog event = (EventLog)getItem(i);
	    if (event.getType() == EventLog.Type.ConferenceChatMessage) {
		    holder.bubbleLayout.setVisibility(View.VISIBLE);

		    final ChatMessage message = event.getChatMessage();
		    holder.messageId = message.getMessageId();
		    message.setUserData(holder);

		    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

		    ChatMessage.State status = message.getState();
		    Address remoteSender = message.getFromAddress();
			String displayName;

		    if (message.isOutgoing()) {
			    displayName = remoteSender.getDisplayName();
			    if (displayName == null || displayName.isEmpty()) {
				    displayName = remoteSender.getUsername();
			    }

			    if (status == ChatMessage.State.InProgress) {
				    holder.messageSendingInProgress.setVisibility(View.VISIBLE);
			    }

			    if (!message.isSecured() && LinphoneManager.getLc().limeEnabled() == Core.LimeState.Mandatory && status != ChatMessage.State.InProgress) {
				    holder.messageStatus.setVisibility(View.VISIBLE);
				    holder.messageStatus.setImageResource(R.drawable.chat_unsecure);
			    }

			    if (status == ChatMessage.State.DeliveredToUser) {
				    holder.imdmLayout.setVisibility(View.VISIBLE);
				    holder.imdmIcon.setImageResource(R.drawable.chat_delivered);
				    holder.imdmLabel.setText(R.string.delivered);
				    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorD));
			    } else if (status == ChatMessage.State.Displayed) {
				    holder.imdmLayout.setVisibility(View.VISIBLE);
				    holder.imdmIcon.setImageResource(R.drawable.chat_read);
				    holder.imdmLabel.setText(R.string.displayed);
				    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorK));
			    } else if (status == ChatMessage.State.NotDelivered) {
				    holder.imdmLayout.setVisibility(View.VISIBLE);
				    holder.imdmIcon.setImageResource(R.drawable.chat_error);
				    holder.imdmLabel.setText(R.string.resend);
				    holder.imdmLabel.setTextColor(mContext.getResources().getColor(R.color.colorI));
			    }

			    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			    layoutParams.setMargins(100, 10, 10, 10);
			    holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
			    Compatibility.setTextAppearance(holder.contactName, mContext, R.style.font3);
			    Compatibility.setTextAppearance(holder.fileTransferAction, mContext, R.style.font15);
			    holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_confirm_delete_button);
			    holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask_outgoing);
		    } else {
			    LinphoneContact contact = null;
			    for (LinphoneContact c : mParticipants) {
				    if (contact.hasAddress(remoteSender.asStringUriOnly())) {
					    contact = c;
					    break;
				    }
			    }
			    if (contact != null) {
				    if (contact.getFullName() != null) {
					    displayName = contact.getFullName();
				    } else {
					    displayName = remoteSender.getDisplayName();
					    if (displayName == null || displayName.isEmpty()) {
						    displayName = remoteSender.getUsername();
					    }
				    }

				    holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
				    if (contact.hasPhoto()) {
					    LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
				    }
			    } else {
				    displayName = remoteSender.getDisplayName();
				    if (displayName == null || displayName.isEmpty()) {
					    displayName = remoteSender.getUsername();
				    }

				    holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
			    }

			    /*if (isEditMode) {
				    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				    layoutParams.setMargins(100, 10, 10, 10);
			    }*/
			    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			    layoutParams.setMargins(10, 10, 100, 10);

			    holder.background.setBackgroundResource(R.drawable.resizable_chat_bubble_incoming);
			    Compatibility.setTextAppearance(holder.contactName, mContext, R.style.font9);
			    Compatibility.setTextAppearance(holder.fileTransferAction, mContext, R.style.font8);
			    holder.fileTransferAction.setBackgroundResource(R.drawable.resizable_assistant_button);
			    holder.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask);
		    }
		    holder.contactName.setText(LinphoneUtils.timestampToHumanDate(mContext, message.getTime(), R.string.messages_date_format) + " - " + displayName);

		    Spanned text = null;
		    String msg = message.getText();

		    String externalBodyUrl = message.getExternalBodyUrl();
		    Content fileTransferContent = message.getFileTransferInformation();
		    String appData = message.getAppdata();
		    if (externalBodyUrl != null) { // Incoming file transfer
			    if (appData != null) { // Download already done, just display the result
				    holder.messageImage.setVisibility(View.VISIBLE);
				    loadBitmap(appData, holder.messageImage);
				    holder.messageImage.setTag(appData);
			    } else { // Attachment not yet downloaded
				    holder.fileTransferLayout.setVisibility(View.VISIBLE);
				    holder.fileTransferAction.setText(mContext.getString(R.string.accept));
				    holder.fileTransferAction.setOnClickListener(new View.OnClickListener() {
					    @Override
					    public void onClick(View v) {
						    if (mContext.getPackageManager().checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, mContext.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
							    v.setEnabled(false);
							    String filename = message.getFileTransferInformation().getName();
							    File file = new File(Environment.getExternalStorageDirectory(), filename);
							    message.setAppdata(file.getPath());
							    message.setListener(ChatEventsAdapter.this);
							    message.setFileTransferFilepath(file.getPath());
							    message.downloadFile();
						    } else {
							    Log.w("WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
							    LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
						    }
					    }
				    });
			    }
		    } else if (fileTransferContent != null) { // Outgoing file transfer
				if (appData != null) {
					holder.messageImage.setVisibility(View.VISIBLE);
					loadBitmap(appData, holder.messageImage);
					holder.messageImage.setTag(appData);

					if (message.getState() == ChatMessage.State.InProgress) {
						holder.messageSendingInProgress.setVisibility(View.GONE);
						holder.fileTransferLayout.setVisibility(View.VISIBLE);
					}
				} else {
					holder.fileTransferLayout.setVisibility(View.VISIBLE);

				}
		    } else if (msg != null) {
			    text = getTextWithHttpLinks(msg);
			    holder.messageText.setText(text);
			    holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
			    holder.messageText.setVisibility(View.VISIBLE);
		    }

		    holder.bubbleLayout.setLayoutParams(layoutParams);
	    } else {
		    holder.eventLayout.setVisibility(View.VISIBLE);

		    Log.e("Conference event type is " + event.getType().toString());
		    //TODO
		    switch (event.getType()) {
			    case ConferenceCreated:
				    holder.eventMessage.setText("Created");
			    	break;
			    case ConferenceDestroyed:
				    holder.eventMessage.setText("Destroyed");
			    	break;
			    case ConferenceParticipantAdded:
				    holder.eventMessage.setText("Participant added");
			    	break;
			    case ConferenceParticipantRemoved:
				    holder.eventMessage.setText("Participant removed");
			    	break;
			    case ConferenceSubjectChanged:
				    holder.eventMessage.setText("Subject changed");
			    	break;
			    case ConferenceParticipantSetAdmin:
				    holder.eventMessage.setText("Admin set");
			    	break;
			    case ConferenceParticipantUnsetAdmin:
				    holder.eventMessage.setText("Admin unset");
			    	break;
			    case None:
			    default:
			    	//TODO
			    	break;
		    }
		    holder.eventTime.setText(LinphoneUtils.timestampToHumanDate(mContext, event.getTime(), R.string.messages_date_format));
	    }

        return view;
    }

	private boolean isToday(Calendar cal) {
		return isSameDay(cal, Calendar.getInstance());
	}

	private boolean isSameDay(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			return false;
		}

		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
				cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
				cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
	}

	private Spanned getTextWithHttpLinks(String text) {
		if (text.contains("<")) {
			text = text.replace("<", "&lt;");
		}
		if (text.contains(">")) {
			text = text.replace(">", "&gt;");
		}
		if (text.contains("\n")) {
			text = text.replace("\n", "<br>");
		}
		if (text.contains("http://")) {
			int indexHttp = text.indexOf("http://");
			int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
			String link = text.substring(indexHttp, indexFinHttp);
			String linkWithoutScheme = link.replace("http://", "");
			text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
		}
		if (text.contains("https://")) {
			int indexHttp = text.indexOf("https://");
			int indexFinHttp = text.indexOf(" ", indexHttp) == -1 ? text.length() : text.indexOf(" ", indexHttp);
			String link = text.substring(indexHttp, indexFinHttp);
			String linkWithoutScheme = link.replace("https://", "");
			text = text.replaceFirst(Pattern.quote(link), "<a href=\"" + link + "\">" + linkWithoutScheme + "</a>");
		}

		return Compatibility.fromHtml(text);
	}

	public void loadBitmap(String path, ImageView imageView) {
		if (cancelPotentialWork(path, imageView)) {
			if (LinphoneUtils.isExtensionImage(path)) {
				mDefaultBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_attachment_over);
			} else {
				mDefaultBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_attachment);
			}

			BitmapWorkerTask task = new BitmapWorkerTask(imageView);
			final AsyncBitmap asyncBitmap = new AsyncBitmap(mContext.getResources(), mDefaultBitmap, task);
			imageView.setImageDrawable(asyncBitmap);
			task.execute(path);
		}
	}

	private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		private static final int SIZE_SMALL = 500;
		private final WeakReference<ImageView> imageViewReference;
		public String path;

		public BitmapWorkerTask(ImageView imageView) {
			path = null;
			// Use a WeakReference to ensure the ImageView can be garbage collected
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		// Decode image in background.
		@Override
		protected Bitmap doInBackground(String... params) {
			path = params[0];
			Bitmap bm = null;
			Bitmap thumbnail = null;
			if (LinphoneUtils.isExtensionImage(path)) {
				if (path.startsWith("content")) {
					try {
						bm = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(path));
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
					Bitmap bm_tmp;
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
					bm_tmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
					if (bm_tmp != bm) {
						bm.recycle();
						bm = bm_tmp;
					} else {
						bm_tmp = null;
					}
				} catch (Exception e) {
					Log.e(e);
				}

				if (bm != null) {
					thumbnail = ThumbnailUtils.extractThumbnail(bm, SIZE_SMALL, SIZE_SMALL);
					bm.recycle();
				}
				return thumbnail;
			} else {
				return mDefaultBitmap;
			}
		}

		// Once complete, see if ImageView is still around and set bitmap.
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) {
				bitmap = null;
			}
			if (imageViewReference != null && bitmap != null) {
				final ImageView imageView = imageViewReference.get();
				final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
				if (this == bitmapWorkerTask && imageView != null) {
					imageView.setImageBitmap(bitmap);

					//Force scroll too bottom with setSelection() after image loaded and last messages
					mFragment.scrollToBottom();

					imageView.setTag(path);
					imageView.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent intent = new Intent(Intent.ACTION_VIEW);
							File file = null;
							Uri contentUri = null;
							String imageUri = (String)v.getTag();
							if (imageUri.startsWith("file://")) {
								imageUri = imageUri.substring("file://".length());
								file = new File(imageUri);
								contentUri = FileProvider.getUriForFile(mContext, "org.linphone.provider", file);
							} else if (imageUri.startsWith("content://")) {
								contentUri = Uri.parse(imageUri);
							} else {
								file = new File(imageUri);
								contentUri = FileProvider.getUriForFile(mContext, "org.linphone.provider", file);
							}
							String type = null;
							String extension = MimeTypeMap.getFileExtensionFromUrl(contentUri.toString());
							if (extension != null) {
								type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
							}
							if(type != null) {
								intent.setDataAndType(contentUri, type);
							}else {
								intent.setDataAndType(contentUri, "*/*");
							}
							intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
							mContext.startActivity(intent);
						}
					});
				}
			}
		}
	}

	class AsyncBitmap extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncBitmap(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	private boolean cancelPotentialWork(String path, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final String bitmapData = bitmapWorkerTask.path;
			// If bitmapData is not yet set or it differs from the new data
			if (bitmapData == null || bitmapData != path) {
				// Cancel previous task
				bitmapWorkerTask.cancel(true);
			} else {
				// The same work is already in progress
				return false;
			}
		}
		// No task associated with the ImageView, or an existing task was cancelled
		return true;
	}

	private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncBitmap) {
				final AsyncBitmap asyncDrawable = (AsyncBitmap) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	@Override
	public void onFileTransferRecv(ChatMessage message, Content content, Buffer buffer) {

	}

	@Override
	public Buffer onFileTransferSend(ChatMessage message, Content content, int offset, int size) {
		return null;
	}

	@Override
	public void onFileTransferProgressIndication(ChatMessage message, Content content, int offset, int total) {
		ChatBubbleViewHolder holder = (ChatBubbleViewHolder)message.getUserData();
		if (holder == null) return;

		if (offset == total) {
			holder.fileTransferLayout.setVisibility(View.GONE);
			holder.messageImage.setVisibility(View.VISIBLE);
			loadBitmap(message.getAppdata(), holder.messageImage);
			holder.messageImage.setTag(message.getAppdata());
		} else {
			holder.fileTransferProgressBar.setProgress(offset * 100 / total);
		}
	}

	@Override
	public void onMsgStateChanged(ChatMessage msg, ChatMessage.State state) {

	}
}
