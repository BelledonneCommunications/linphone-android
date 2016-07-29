package org.linphone.ui;
/*
LinphoneScrollView.java
Copyright (C) 2013  Belledonne Communications, Grenoble, France

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
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * @author Sylvain Berfini
 */
public class LinphoneScrollView extends ScrollView {
	private ScrollViewListener scrollViewListener = null;

    public LinphoneScrollView(Context context) {
        super(context);
    }

    public LinphoneScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public LinphoneScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollViewListener(ScrollViewListener scrollViewListener) {
        this.scrollViewListener = scrollViewListener;
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldx, int oldy) {
        super.onScrollChanged(x, y, oldx, oldy);
        if (y >= getMeasuredHeight() && scrollViewListener != null) {
        	//scrollViewListener.OnScrollToBottom();
        }
        else if (y == 0 && scrollViewListener != null) {
        	scrollViewListener.OnScrollToTop(getMeasuredHeight());
        }
    }
}
