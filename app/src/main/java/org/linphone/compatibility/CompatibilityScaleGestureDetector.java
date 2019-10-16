/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.compatibility;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class CompatibilityScaleGestureDetector
        extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private ScaleGestureDetector detector;
    private CompatibilityScaleGestureListener listener;

    public CompatibilityScaleGestureDetector(Context context) {
        detector = new ScaleGestureDetector(context, this);
    }

    public void setOnScaleListener(CompatibilityScaleGestureListener newListener) {
        listener = newListener;
    }

    public void onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (listener == null) {
            return false;
        }

        return listener.onScale(this);
    }

    public float getScaleFactor() {
        return detector.getScaleFactor();
    }

    public void destroy() {
        listener = null;
        detector = null;
    }
}
