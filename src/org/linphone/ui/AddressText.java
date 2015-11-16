/*
AddressView.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone.ui;

import org.linphone.DialerFragment;
import org.linphone.LinphoneManager.AddressType;
import org.linphone.R;

import android.content.Context;
import android.graphics.Paint;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.EditText;

/**
 * @author Guillaume Beraudo
 * 
 */
public class AddressText extends EditText implements AddressType {

	private String displayedName;
	private Uri pictureUri;
	private Paint mTestPaint;
	private DialerFragment dialer;
	
	public void setPictureUri(Uri uri) {
		pictureUri = uri;
	}

	public Uri getPictureUri() {
		return pictureUri;
	}

	public AddressText(Context context, AttributeSet attrs) {
		super(context, attrs);

		mTestPaint = new Paint();
		mTestPaint.set(this.getPaint());
	}

	public void clearDisplayedName() {
		displayedName = null;
	}

	public String getDisplayedName() {
		return displayedName;
	}

	public void setContactAddress(String uri, String displayedName) {
		setText(uri);
		this.displayedName = displayedName;
	}

	public void setDisplayedName(String displayedName) {
		this.displayedName = displayedName;
	}
	
	private String getHintText() {
		String resizedText = getContext().getString(R.string.address_bar_hint);
		if (getHint() != null) {
			resizedText = getHint().toString();
		}
		return resizedText;
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int before,
			int after) {
		clearDisplayedName();
		pictureUri = null;

		refitText(getWidth(), getHeight());
		
		if (dialer != null) {
			dialer.enableDisableAddContact();
		}

		super.onTextChanged(text, start, before, after);
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		if (width != oldWidth) {
			refitText(getWidth(), getHeight());
		}
	}
	
	private float getOptimizedTextSize(String text, int textWidth, int textHeight) {
		int targetWidth = textWidth - getPaddingLeft() - getPaddingRight();
		int targetHeight = textHeight - getPaddingTop() - getPaddingBottom();
		float hi = 90;
		float lo = 2;
		final float threshold = 0.5f;

		mTestPaint.set(getPaint());

		while ((hi - lo) > threshold) {
			float size = (hi + lo) / 2;
			mTestPaint.setTextSize(size);
			if (mTestPaint.measureText(text) >= targetWidth || size >= targetHeight) {
				hi = size;
			}
			else {
				lo = size;
			}
		}
		
		return lo;
	}

	private void refitText(int textWidth, int textHeight) {
		if (textWidth <= 0) {
			return;
		}
		
		float size = getOptimizedTextSize(getHintText(), textWidth, textHeight);
		float entrySize = getOptimizedTextSize(getText().toString(), textWidth, textHeight);
		if (entrySize < size)
			size = entrySize;
		setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		int height = getMeasuredHeight();
		
		refitText(parentWidth, height);
		setMeasuredDimension(parentWidth, height);
	}

	public void setDialerFragment(DialerFragment dialerFragment) {
		dialer = dialerFragment;
	}
}
