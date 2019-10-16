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
package org.linphone.call;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import org.linphone.LinphoneManager;
import org.linphone.compatibility.CompatibilityScaleGestureDetector;
import org.linphone.compatibility.CompatibilityScaleGestureListener;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.utils.LinphoneUtils;

public class VideoZoomHelper extends GestureDetector.SimpleOnGestureListener
        implements CompatibilityScaleGestureListener {
    private View mVideoView;
    private GestureDetector mGestureDetector;
    private float mZoomFactor = 1.f;
    private float mZoomCenterX, mZoomCenterY;
    private CompatibilityScaleGestureDetector mScaleDetector;

    public VideoZoomHelper(Context context, View videoView) {
        mGestureDetector = new GestureDetector(context, this);
        mScaleDetector = new CompatibilityScaleGestureDetector(context);
        mScaleDetector.setOnScaleListener(this);

        mVideoView = videoView;
        mVideoView.setOnTouchListener(
                new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent event) {
                        float currentZoomFactor = mZoomFactor;
                        if (mScaleDetector != null) {
                            mScaleDetector.onTouchEvent(event);
                        }
                        if (currentZoomFactor != mZoomFactor) {
                            // We did scale, prevent touch event from going further
                            return true;
                        }

                        boolean touch = mGestureDetector.onTouchEvent(event);
                        // If true, gesture detected, prevent touch event from going further
                        // Otherwise it seems we didn't use event,
                        // allow it to be dispatched somewhere else
                        return touch;
                    }
                });
    }

    public boolean onScale(CompatibilityScaleGestureDetector detector) {
        mZoomFactor *= detector.getScaleFactor();
        // Don't let the object get too small or too large.
        // Zoom to make the video fill the screen vertically
        float portraitZoomFactor =
                ((float) mVideoView.getHeight()) / (float) ((3 * mVideoView.getWidth()) / 4);
        // Zoom to make the video fill the screen horizontally
        float landscapeZoomFactor =
                ((float) mVideoView.getWidth()) / (float) ((3 * mVideoView.getHeight()) / 4);
        mZoomFactor =
                Math.max(
                        0.1f,
                        Math.min(mZoomFactor, Math.max(portraitZoomFactor, landscapeZoomFactor)));

        Call currentCall = LinphoneManager.getCore().getCurrentCall();
        if (currentCall != null) {
            currentCall.zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        Core core = LinphoneManager.getCore();
        if (LinphoneUtils.isCallEstablished(core.getCurrentCall())) {
            if (mZoomFactor > 1) {
                // Video is zoomed, slide is used to change center of zoom
                if (distanceX > 0 && mZoomCenterX < 1) {
                    mZoomCenterX += 0.01;
                } else if (distanceX < 0 && mZoomCenterX > 0) {
                    mZoomCenterX -= 0.01;
                }
                if (distanceY < 0 && mZoomCenterY < 1) {
                    mZoomCenterY += 0.01;
                } else if (distanceY > 0 && mZoomCenterY > 0) {
                    mZoomCenterY -= 0.01;
                }

                if (mZoomCenterX > 1) mZoomCenterX = 1;
                if (mZoomCenterX < 0) mZoomCenterX = 0;
                if (mZoomCenterY > 1) mZoomCenterY = 1;
                if (mZoomCenterY < 0) mZoomCenterY = 0;

                core.getCurrentCall().zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Core core = LinphoneManager.getCore();
        if (LinphoneUtils.isCallEstablished(core.getCurrentCall())) {
            if (mZoomFactor == 1.f) {
                // Zoom to make the video fill the screen vertically
                float portraitZoomFactor =
                        ((float) mVideoView.getHeight())
                                / (float) ((3 * mVideoView.getWidth()) / 4);
                // Zoom to make the video fill the screen horizontally
                float landscapeZoomFactor =
                        ((float) mVideoView.getWidth())
                                / (float) ((3 * mVideoView.getHeight()) / 4);

                mZoomFactor = Math.max(portraitZoomFactor, landscapeZoomFactor);
            } else {
                resetZoom();
            }

            core.getCurrentCall().zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
            return true;
        }

        return false;
    }

    public void destroy() {
        if (mVideoView != null) {
            mVideoView.setOnTouchListener(null);
            mVideoView = null;
        }
        if (mGestureDetector != null) {
            mGestureDetector.setOnDoubleTapListener(null);
            mGestureDetector = null;
        }
        if (mScaleDetector != null) {
            mScaleDetector.destroy();
            mScaleDetector = null;
        }
    }

    private void resetZoom() {
        mZoomFactor = 1.f;
        mZoomCenterX = mZoomCenterY = 0.5f;
    }
}
