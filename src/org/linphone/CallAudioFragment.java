package org.linphone;
/*
CallAudioFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import android.app.Activity;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;

/**
 * @author Sylvain Berfini
 */
public class CallAudioFragment extends Fragment {
	private CallActivity incallActvityInstance;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.audio, container, false);
        return view;
    }

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		incallActvityInstance = (CallActivity) activity;
		
		if (incallActvityInstance != null) {
			incallActvityInstance.bindAudioFragment(this);
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();

		// Just to be sure we have incall controls
		if (incallActvityInstance != null) {
			incallActvityInstance.removeCallbacks();
		}
	}
	
	class SwipeGestureDetector implements OnTouchListener {
	    static final int MIN_DISTANCE = 100;
	    private float downX, upX;
	    private boolean lock;
	    
		private SwipeListener listener;
		
		public SwipeGestureDetector(SwipeListener swipeListener) {
			super();
			listener = swipeListener;
		}
		
        @Override
    	public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
            	lock = false;
                downX = event.getX();
                return true;
                
            case MotionEvent.ACTION_MOVE:
            	if (lock) {
            		return false;
            	}
                upX = event.getX();

                float deltaX = downX - upX;

                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    lock = true;
                    if (deltaX < 0) { listener.onLeftToRightSwipe(); return true; }
                    if (deltaX > 0) { listener.onRightToLeftSwipe(); return true; }
                }
                break;
            }
            return false;
        }
    }
	
	interface SwipeListener {
		void onRightToLeftSwipe();
		void onLeftToRightSwipe();
	}
}
