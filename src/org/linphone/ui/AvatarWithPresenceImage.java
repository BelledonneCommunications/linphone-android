package org.linphone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneContact;
import org.linphone.R;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.OnlineStatus;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.mediastream.Log;

/**
 * Created by brieucviel on 15/04/2016.
 */


public class AvatarWithPresenceImage extends RelativeLayout implements onPresenceUpdated {

    private LinphoneContact contact;
    private ImageView friendStatus;

    public AvatarWithPresenceImage(Context context, AttributeSet attrs) {
        super(context);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.avatar_with_presence, this);
        ImageView friendStatus = (ImageView) this.findViewById(R.id.friendStatus);
        friendStatus.setImageResource(R.drawable.presence_unregistered);
        LinphoneActivity.instance().addPresenceUpdatedListener(this);
    }

    public void setLinphoneContact(LinphoneContact mContact){
        this.contact = mContact;
    }

    public boolean isThisFriend(LinphoneFriend myFriend){
        return this.contact.compareFriend(myFriend);
    }

    @Override
    public void updatePresenceIcon (LinphoneCore lc, LinphoneFriend friend){

        if (contact != null) {
            if (contact.isLinphoneFriend() && contact.getFriendPresenceModel() != null){
                PresenceModel presenceModel = contact.getFriendPresenceModel();
                PresenceBasicStatus basicStatus = presenceModel.getBasicStatus();
                String presenceStatus = "";
                if (basicStatus == PresenceBasicStatus.Open || basicStatus == PresenceBasicStatus.Open)
                    presenceStatus = basicStatus.toString();
                Log.e("===>>> updateAvatarPresence status = " + presenceStatus + " - vs basicStatus = " + basicStatus);

                if (basicStatus == PresenceBasicStatus.Closed) {
                    friendStatus.setImageResource(R.drawable.presence_unregistered);
                } else if (presenceStatus == OnlineStatus.Online.toString()) {
                    friendStatus.setImageResource(R.drawable.presence_online);
                } else {
                    friendStatus.setImageResource(R.drawable.presence_offline);
                }
            } else{
                Log.e("===>>> updateAvatarPresence friend is null ");
                friendStatus.setImageResource(R.drawable.presence_unregistered);
            }
        }
    }
}


interface onPresenceUpdated {
    void updatePresenceIcon (LinphoneCore lc, LinphoneFriend friend);
}