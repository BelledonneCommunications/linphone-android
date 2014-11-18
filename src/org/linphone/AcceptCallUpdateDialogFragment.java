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