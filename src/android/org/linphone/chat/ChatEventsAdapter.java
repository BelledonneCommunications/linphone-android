/*
GroupChatFragment.java
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

import android.content.Context;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.Core;
import org.linphone.core.EventLog;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

public class ChatEventsAdapter extends BaseAdapter {
	private Context mContext;
    private List<EventLog> mHistory;
	private List<LinphoneContact> mParticipants;
    private LayoutInflater mLayoutInflater;

    public ChatEventsAdapter(Context context, LayoutInflater inflater, EventLog[] history, List<LinphoneContact> participants) {
	    mContext = context;
        mLayoutInflater = inflater;
        mHistory = Arrays.asList(history);
	    mParticipants = participants;
    }

    public void updateHistory(EventLog[] history) {
	    mHistory = Arrays.asList(history);
	    notifyDataSetChanged();
    }

    public void addToHistory(EventLog log) {
	    mHistory.add(log);
	    notifyDataSetChanged();
    }

    public void setContacts(List<LinphoneContact> participants) {
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

		    ChatMessage message = null;//event.getChatMessage();
		    holder.messageId = message.getMessageId();
		    message.setUserData(holder);

		    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

		    ChatMessage.State status = message.getState();
		    Address remoteSender = message.getFromAddress();
			String displayName;

		    if (message.isOutgoing()) {
			    displayName = remoteSender.getDisplayName();

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
		    holder.contactName.setText(timestampToHumanDate(mContext, message.getTime()) + " - " + displayName);

		    Spanned text = null;
		    String msg = message.getText();
		    if (msg != null) {
			    text = getTextWithHttpLinks(msg);
			    holder.messageText.setText(text);
			    holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
			    holder.messageText.setVisibility(View.VISIBLE);
		    }

		    holder.bubbleLayout.setLayoutParams(layoutParams);
	    } else {
		    holder.eventLayout.setVisibility(View.VISIBLE);

		    holder.eventMessage.setText(""); //TODO
		    holder.eventTime.setText(timestampToHumanDate(mContext, event.getTime()));
	    }

        return view;
    }

	private String timestampToHumanDate(Context context, long timestamp) {
		try {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(timestamp);

			SimpleDateFormat dateFormat;
			if (isToday(cal)) {
				dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.today_date_format));
			} else {
				dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.messages_date_format));
			}

			return dateFormat.format(cal.getTime());
		} catch (NumberFormatException nfe) {
			return String.valueOf(timestamp);
		}
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
}
