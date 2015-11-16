package org.linphone.ui;

import org.linphone.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;

/*
LinphoneSliders.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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

/**
 * @author Sylvain Berfini
 */
public class LinphoneSliders extends View implements OnGestureListener {
	private Drawable leftSliderImg, rightSliderImg;
	private int leftSliderX, rightSliderX;
	private int slidersHeight, slidersWidth;
	private GestureDetector mGestures;
	private LinphoneSliderTriggered mTriggerListener;
	private boolean slidingLeftHandle, slidingRightHandle;
	private static final double mCoeff = 0.5;
	
	public LinphoneSliders(Context context, AttributeSet attrs) {
		super(context, attrs);
		mGestures = new GestureDetector(getContext(), this);
		//leftSliderImg = getResources().getDrawable(R.drawable.slider_left);
		//rightSliderImg = getResources().getDrawable(R.drawable.slider_right);
		
		slidersHeight = leftSliderImg.getIntrinsicHeight();
		slidersWidth = leftSliderImg.getIntrinsicWidth();
		
		leftSliderX = 0;
		rightSliderX = 0;
		slidingLeftHandle = slidingRightHandle = false;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		rightSliderImg.setBounds(getWidth() - slidersWidth - rightSliderX, getHeight() - slidersHeight, getWidth(), getHeight());
		rightSliderImg.draw(canvas);
		
		leftSliderImg.setBounds(0, getHeight() - slidersHeight, slidersWidth + leftSliderX, getHeight());
		leftSliderImg.draw(canvas);
		
		if (slidingLeftHandle && Math.abs(leftSliderX) >= mCoeff * getWidth()) {
			mTriggerListener.onLeftHandleTriggered();
		} else if (slidingRightHandle && rightSliderX >= mCoeff * getWidth()) {
			mTriggerListener.onRightHandleTriggered();
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
			leftSliderX = 0;
			rightSliderX = 0;
			slidingLeftHandle = slidingRightHandle = false;
			invalidate();
		}
		
		return mGestures.onTouchEvent(event); 
	}

	@Override
	public boolean onDown(MotionEvent e) {
		return true;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		if (e1.getY() < getHeight() - slidersHeight) {
			return false;
		}
		
		if (e1.getX() < getWidth() / 2) {
			leftSliderX -= distanceX;
			slidingLeftHandle = true;
		} else {
			rightSliderX += distanceX;
			slidingRightHandle = true;
		}
		invalidate();
		
		return true;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}
	
	public void setOnTriggerListener(LinphoneSliderTriggered listener) {
        mTriggerListener = listener;
    }

	public interface LinphoneSliderTriggered {
		public void onLeftHandleTriggered();
		public void onRightHandleTriggered();
	}
}

