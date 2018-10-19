package org.linphone.call;

/*
CallVideoFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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

import android.app.Fragment;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.compatibility.CompatibilityScaleGestureDetector;
import org.linphone.compatibility.CompatibilityScaleGestureListener;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.VideoDefinition;
import org.linphone.mediastream.Log;

public class CallVideoFragment extends Fragment implements OnGestureListener, OnDoubleTapListener, CompatibilityScaleGestureListener {
    private TextureView mVideoView;
    private TextureView mCaptureView;
    private GestureDetector mGestureDetector;
    private float mZoomFactor = 1.f;
    private float mZoomCenterX, mZoomCenterY;
    private CompatibilityScaleGestureDetector mScaleDetector;
    private CallActivity inCallActivity;
    private int previewX, previewY;

    @SuppressWarnings("deprecation")
    // Warning useless because value is ignored and automatically set by new APIs.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view;
        if (LinphoneManager.getLc().hasCrappyOpengl()) {
            view = inflater.inflate(R.layout.video_no_opengl, container, false);
        } else {
            view = inflater.inflate(R.layout.video, container, false);
        }

        mVideoView = view.findViewById(R.id.videoSurface);
        mCaptureView = view.findViewById(R.id.videoCaptureSurface);
        
        LinphoneManager.getLc().setNativeVideoWindowId(mVideoView);
        LinphoneManager.getLc().setNativePreviewWindowId(mCaptureView);

        mVideoView.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (mScaleDetector != null) {
                    mScaleDetector.onTouchEvent(event);
                }

                mGestureDetector.onTouchEvent(event);
                if (inCallActivity != null) {
                    inCallActivity.displayVideoCallControlsIfHidden();
                }
                return true;
            }
        });

        mCaptureView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        previewX = (int) motionEvent.getX();
                        previewY = (int) motionEvent.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int x = (int) motionEvent.getX();
                        int y = (int) motionEvent.getY();
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mCaptureView.getLayoutParams();
                        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0); // Clears the rule, as there is no removeRule until API 17.
                        lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                        int left = lp.leftMargin + (x - previewX);
                        int top = lp.topMargin + (y - previewY);
                        lp.leftMargin = left;
                        lp.topMargin = top;
                        view.setLayoutParams(lp);
                        break;
                }
                return true;
            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        inCallActivity = (CallActivity) getActivity();
        if (inCallActivity != null) {
            inCallActivity.bindVideoFragment(this);
        }
    }

    private void resizePreview() {
        Core lc = LinphoneManager.getLc();
        if (lc.getCallsNb() > 0) {
            Call call = lc.getCurrentCall();
            if (call == null) {
                call = lc.getCalls()[0];
            }
            if (call == null) return;

            DisplayMetrics metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenHeight = metrics.heightPixels;
            int maxHeight = screenHeight / 4; // Let's take at most 1/4 of the screen for the camera preview

            VideoDefinition videoSize = call.getCurrentParams().getSentVideoDefinition(); // It already takes care of rotation
            if (videoSize.getWidth() == 0 || videoSize.getHeight() == 0) {
                Log.w("Couldn't get sent video definition, using default video definition");
                videoSize = lc.getPreferredVideoDefinition();
            }
            int width = videoSize.getWidth();
            int height = videoSize.getHeight();

            Log.d("Video height is " + height + ", width is " + width);
            width = width * maxHeight / height;
            height = maxHeight;

            if (mCaptureView == null) {
                Log.e("mCaptureView is null !");
                return;
            }

            RelativeLayout.LayoutParams newLp = new RelativeLayout.LayoutParams(width, height);
            newLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1); // Clears the rule, as there is no removeRule until API 17.
            newLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 1);
            mCaptureView.setLayoutParams(newLp);
            Log.d("Video preview size set to " + width + "x" + height);
        }
    }

    public void switchCamera() {
        try {
            String currentDevice = LinphoneManager.getLc().getVideoDevice();
            String[] devices = LinphoneManager.getLc().getVideoDevicesList();
            int index = 0;
            for (String d : devices) {
                if (d.equals(currentDevice)) {
                    break;
                }
                index++;
            }

            String newDevice;
            if (index == 1)
                newDevice = devices[0];
            else if (devices.length > 1)
                newDevice = devices[1];
            else
                newDevice = devices[index];
            LinphoneManager.getLc().setVideoDevice(newDevice);

            CallManager.getInstance().updateCall();
        } catch (ArithmeticException ae) {
            Log.e("Cannot swtich camera : no camera");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphonePreferences.instance().isOverlayEnabled()) {
            LinphoneService.instance().destroyOverlay();
        }

        mGestureDetector = new GestureDetector(inCallActivity, this);
        mScaleDetector = Compatibility.getScaleGestureDetector(inCallActivity, this);

        resizePreview();
    }

    @Override
    public void onPause() {
        if (LinphonePreferences.instance().isOverlayEnabled()) {
            LinphoneService.instance().createOverlay();
        }

        super.onPause();
    }

    public boolean onScale(CompatibilityScaleGestureDetector detector) {
        mZoomFactor *= detector.getScaleFactor();
        // Don't let the object get too small or too large.
        // Zoom to make the video fill the screen vertically
        float portraitZoomFactor = ((float) mVideoView.getHeight()) / (float) ((3 * mVideoView.getWidth()) / 4);
        // Zoom to make the video fill the screen horizontally
        float landscapeZoomFactor = ((float) mVideoView.getWidth()) / (float) ((3 * mVideoView.getHeight()) / 4);
        mZoomFactor = Math.max(0.1f, Math.min(mZoomFactor, Math.max(portraitZoomFactor, landscapeZoomFactor)));

        Call currentCall = LinphoneManager.getLc().getCurrentCall();
        if (currentCall != null) {
            currentCall.zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (LinphoneUtils.isCallEstablished(LinphoneManager.getLc().getCurrentCall())) {
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

                if (mZoomCenterX > 1)
                    mZoomCenterX = 1;
                if (mZoomCenterX < 0)
                    mZoomCenterX = 0;
                if (mZoomCenterY > 1)
                    mZoomCenterY = 1;
                if (mZoomCenterY < 0)
                    mZoomCenterY = 0;

                LinphoneManager.getLc().getCurrentCall().zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (LinphoneUtils.isCallEstablished(LinphoneManager.getLc().getCurrentCall())) {
            if (mZoomFactor == 1.f) {
                // Zoom to make the video fill the screen vertically
                float portraitZoomFactor = ((float) mVideoView.getHeight()) / (float) ((3 * mVideoView.getWidth()) / 4);
                // Zoom to make the video fill the screen horizontally
                float landscapeZoomFactor = ((float) mVideoView.getWidth()) / (float) ((3 * mVideoView.getHeight()) / 4);

                mZoomFactor = Math.max(portraitZoomFactor, landscapeZoomFactor);
            } else {
                resetZoom();
            }

            LinphoneManager.getLc().getCurrentCall().zoom(mZoomFactor, mZoomCenterX, mZoomCenterY);
            return true;
        }

        return false;
    }

    private void resetZoom() {
        mZoomFactor = 1.f;
        mZoomCenterX = mZoomCenterY = 0.5f;
    }

    @Override
    public void onDestroy() {
        inCallActivity = null;

        mCaptureView = null;
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

        super.onDestroy();
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true; // Needed to make the GestureDetector working
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
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
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }
}
