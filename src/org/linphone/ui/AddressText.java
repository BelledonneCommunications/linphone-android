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

import org.linphone.LinphoneManager.AddressType;

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
		displayedName = "";
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

	@Override
	protected void onTextChanged(CharSequence text, int start, int before,
			int after) {
		clearDisplayedName();
		pictureUri = null;

		String resizedText = getText().toString();
		if (resizedText.equals("") && getHint() != null) {
			resizedText = getHint().toString();
		}
		refitText(resizedText, getWidth());

		super.onTextChanged(text, start, before, after);
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		if (width != oldWidth) {
			String resizedText = getText().toString();
			if (resizedText.equals("") && getHint() != null) {
				resizedText = getHint().toString();
			}
			refitText(resizedText, getWidth());
		}
	}

	private void refitText(String text, int textWidth) {
		if (textWidth <= 0) {
			return;
		}
		
		int targetWidth = textWidth - getPaddingLeft() - getPaddingRight();
		float hi = 90;
		float lo = 2;
		final float threshold = 0.5f;

		mTestPaint.set(getPaint());

		while ((hi - lo) > threshold) {
			float size = (hi + lo) / 2;
			mTestPaint.setTextSize(size);
			if (mTestPaint.measureText(text) >= targetWidth) {
				hi = size;
			}
			else {
				lo = size;
			}
		}
		
		setTextSize(TypedValue.COMPLEX_UNIT_PX, lo);
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		int height = getMeasuredHeight();
		
		String resizedText = getText().toString();
		if (resizedText.equals("") && getHint() != null) {
			resizedText = getHint().toString();
		}
		refitText(resizedText, parentWidth);
		setMeasuredDimension(parentWidth, height);
	}
}
