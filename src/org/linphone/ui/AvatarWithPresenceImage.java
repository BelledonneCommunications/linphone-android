package org.linphone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneContact;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.OnlineStatus;
import org.linphone.core.PresenceActivity;
import org.linphone.core.PresenceActivityType;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.mediastream.Log;

/**
 * Created by brieucviel on 15/04/2016.
 */


public class AvatarWithPresenceImage extends RelativeLayout implements onPresenceUpdated {

    private LinphoneContact contact;
    private ImageView friendStatusSmall, contactPictureSmall, friendStatusBig, contactPictureBig;

    public static final int AVATAR_SMALL = 0;
    public static final int AVATAR_BIG = 1;

    public AvatarWithPresenceImage(Context context) {
        super(context);
        init();
    }

    public AvatarWithPresenceImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarWithPresenceImage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.inflate(getContext(), R.layout.avatar_with_presence, this);
        friendStatusSmall = (ImageView) this.findViewById(R.id.friend_status_small);
        friendStatusSmall.setImageResource(R.drawable.presence_unregistered);
        contactPictureSmall = (ImageView) this.findViewById(R.id.contact_picture_small);

        friendStatusBig  = (ImageView) this.findViewById(R.id.friend_status_big);
        contactPictureBig = (ImageView) this.findViewById(R.id.contact_picture_big);

        friendStatusSmall.setVisibility(View.VISIBLE);
        contactPictureSmall.setVisibility(View.VISIBLE);
        friendStatusBig.setVisibility(View.GONE);
        contactPictureBig.setVisibility(View.GONE);
    }

    public void setFormatAvatarImage(int format){
        if(format == AVATAR_BIG){
            friendStatusSmall.setVisibility(View.GONE);
            contactPictureSmall.setVisibility(View.GONE);
            friendStatusBig.setVisibility(View.VISIBLE);
            contactPictureBig.setVisibility(View.VISIBLE);
        }
        else if(format == AVATAR_SMALL){
            friendStatusSmall.setVisibility(View.VISIBLE);
            contactPictureSmall.setVisibility(View.VISIBLE);
            friendStatusBig.setVisibility(View.GONE);
            contactPictureBig.setVisibility(View.GONE);
        }
    }

    public void setLinphoneContact(LinphoneContact mContact){
        this.contact = mContact;
        //this.contact.refresh();
        updatePresenceIcon(null, null);
    }

    public boolean isThisFriend(LinphoneFriend myFriend){
        return this.contact.compareFriend(myFriend);
    }

    public String getFriendName(){
        return this.contact.getFullName();
    }

    //TODO
    public boolean isThisFriendByName(LinphoneFriend myFriend){
        return this.contact.compareFriend(myFriend);
    }

    @Override
    public void updatePresenceIcon (LinphoneCore lc, LinphoneFriend friend){

        if (contact != null) {
            if (contact.isLinphoneFriend() && contact.getFriendPresenceModel() != null){
                friendStatusSmall = (ImageView) this.findViewById(R.id.friend_status_small);
                friendStatusBig = (ImageView) this.findViewById(R.id.friend_status_big);
                PresenceModel presenceModel = contact.getFriendPresenceModel();
                PresenceBasicStatus basicStatus = presenceModel.getBasicStatus();
                if (basicStatus == PresenceBasicStatus.Closed) {
                    if(friend.getPresenceModel() != null){
                        friendStatusSmall.setImageResource(R.drawable.presence_away);
                        friendStatusBig.setImageResource(R.drawable.presence_away);
                    }else {
                        friendStatusSmall.setImageResource(R.drawable.presence_unregistered);
                        friendStatusBig.setImageResource(R.drawable.presence_unregistered);
                    }
                } else if ((presenceModel.getActivity().getType() == PresenceActivityType.TV )) {
                    friendStatusSmall.setImageResource(R.drawable.presence_online);
                    friendStatusBig.setImageResource(R.drawable.presence_online);
                } else {
                    friendStatusSmall.setImageResource(R.drawable.presence_away);
                    friendStatusBig.setImageResource(R.drawable.presence_away);
                }
            /*} else if(contact.isLinphoneFriend()){
                friendStatusSmall.setImageResource(R.drawable.presence_away);
                friendStatusBig.setImageResource(R.drawable.presence_away);
            */
            }/* else{
                friendStatusSmall.setImageResource(R.drawable.presence_unregistered);
                friendStatusSmall.setImageResource(R.drawable.presence_unregistered);
            }*/
        }
    }
}


interface onPresenceUpdated {
    void updatePresenceIcon (LinphoneCore lc, LinphoneFriend friend);
}