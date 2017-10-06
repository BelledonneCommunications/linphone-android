package org.linphone.ui;

/*
LinphoneOverlay.java
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

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

public class LinphoneOverlay extends org.linphone.mediastream.video.display.GL2JNIView {
	private WindowManager wm;
	private WindowManager.LayoutParams params;
	private DisplayMetrics metrics;
	private float x;
	private float y;
	private float touchX;
	private float touchY;
	private boolean dragEnabled;
	private AndroidVideoWindowImpl androidVideoWindowImpl;

	public LinphoneOverlay(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		params = new WindowManager.LayoutParams(
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.TYPE_PHONE,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.TOP | Gravity.LEFT;
		metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		androidVideoWindowImpl = new AndroidVideoWindowImpl(this, null, new AndroidVideoWindowImpl.VideoWindowListener() {
			public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				LinphoneManager.getLc().setVideoWindow(vw);
			}

			public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {

			}

			public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
			}

			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {
			}
		});

		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		LinphoneCallParams callParams = call.getCurrentParams();
		params.width = callParams.getReceivedVideoSize().width;
		params.height = callParams.getReceivedVideoSize().height;
		LinphoneManager.getLc().setVideoWindow(androidVideoWindowImpl);

		setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Context context = LinphoneService.instance();
				Intent intent = new Intent(context, LinphoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
			}
		});
		setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				dragEnabled = true;
				return true;
			}
		});
	}

	public LinphoneOverlay(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public LinphoneOverlay(Context context) {
		this(context, null);
	}

	public void destroy() {
		androidVideoWindowImpl.release();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		x = event.getRawX();
		y = event.getRawY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			touchX = event.getX();
			touchY = event.getY();
			break;
		case MotionEvent.ACTION_MOVE:
			if (dragEnabled) {
				updateViewPostion();
			}
			break;
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			touchX = touchY = 0;
			dragEnabled = false;
			break;
		default:
			break;
		}
		return super.onTouchEvent(event);
	}

	private void updateViewPostion() {
		params.x = Math.min(Math.max(0, (int) (x - touchX)), metrics.widthPixels - getMeasuredWidth());
		params.y = Math.min(Math.max(0, (int) (y - touchY)), metrics.heightPixels - getMeasuredHeight());
		wm.updateViewLayout(this, params);
	}

	public WindowManager.LayoutParams getWindowManagerLayoutParams() {
		return params;
	}
}
