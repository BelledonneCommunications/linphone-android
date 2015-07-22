/*
AcceptCallUpdateDialogFragment.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

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

import org.linphone.mediastream.Log;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;



@SuppressLint("ValidFragment")
public class AcceptCallUpdateDialogFragment extends DialogFragment {

    public AcceptCallUpdateDialogFragment() {
        // Empty constructor required for DialogFragment
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.accept_call_update_dialog, container);

        getDialog().setTitle(R.string.call_update_title);
        
        Button yes = (Button) view.findViewById(R.id.yes);
        yes.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (InCallActivity.isInstanciated()) {
			    	Log.d("Call Update Accepted");
			    	InCallActivity.instance().acceptCallUpdate(true);
				}
		    	dismiss();
			}
		});
        
        Button no = (Button) view.findViewById(R.id.no);
        no.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (InCallActivity.isInstanciated()) {
					Log.d("Call Update Denied");
			    	InCallActivity.instance().acceptCallUpdate(false);
				}
		    	dismiss();
			}
		});
        
        return view;
    }
    
    @Override
    public void onCancel(DialogInterface dialog) {
    	super.onCancel(dialog);
    	InCallActivity.instance().acceptCallUpdate(false);
    }
}