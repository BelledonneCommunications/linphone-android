package org.linphone.views;

/*
NumpadView.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Collection;
import org.linphone.R;

public class Numpad extends LinearLayout implements AddressAware {
    private final boolean mPlayDtmf;

    public Numpad(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Numpad);
        mPlayDtmf = 1 == a.getInt(R.styleable.Numpad_play_dtmf, 1);
        a.recycle();
        LayoutInflater.from(context).inflate(R.layout.numpad, this);
        setLongClickable(true);
    }

    @Override
    protected final void onFinishInflate() {
        for (Digit v : retrieveChildren(this, Digit.class)) {
            v.setPlayDtmf(mPlayDtmf);
        }
        super.onFinishInflate();
    }

    public void setAddressWidget(AddressText address) {
        for (AddressAware v : retrieveChildren(this, AddressAware.class)) {
            v.setAddressWidget(address);
        }
    }

    private <T> Collection<T> retrieveChildren(ViewGroup viewGroup, Class<T> clazz) {
        final Collection<T> views = new ArrayList<>();

        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ViewGroup) {
                views.addAll(retrieveChildren((ViewGroup) v, clazz));
            } else {
                if (clazz.isInstance(v)) views.add(clazz.cast(v));
            }
        }

        return views;
    }
}
