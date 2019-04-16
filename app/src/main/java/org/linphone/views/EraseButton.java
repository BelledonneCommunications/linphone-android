/*
EraseButton.java
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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.linphone.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;

@SuppressLint("AppCompatCustomView")
public class EraseButton extends ImageView
        implements AddressAware, OnClickListener, OnLongClickListener, TextWatcher {

    private AddressText mAddress;

    public EraseButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEnabled(false);
        setOnClickListener(this);
        setOnLongClickListener(this);
    }

    public void onClick(View v) {
        if (mAddress.getText().length() > 0) {
            int lBegin = mAddress.getSelectionStart();
            if (lBegin == -1) {
                lBegin = mAddress.getEditableText().length() - 1;
            }
            if (lBegin > 0) {
                mAddress.getEditableText().delete(lBegin - 1, lBegin);
            }
        }
        setEnabled(mAddress.getText().length() > 0);
    }

    public boolean onLongClick(View v) {
        mAddress.getEditableText().clear();
        return true;
    }

    public void setAddressWidget(AddressText view) {
        mAddress = view;
        view.addTextChangedListener(this);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void afterTextChanged(Editable s) {
        setEnabled(s.length() > 0);
    }
}
