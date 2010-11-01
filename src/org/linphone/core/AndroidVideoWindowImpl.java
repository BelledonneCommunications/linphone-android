package org.linphone.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface.OutOfResourcesException;
import android.view.SurfaceHolder.Callback;

public class AndroidVideoWindowImpl implements VideoWindow {
	private Bitmap mBitmap;
	private SurfaceView mView;
	private Surface mSurface;
	private VideoWindowListener mListener;
	public static interface VideoWindowListener{
		void onSurfaceReady(AndroidVideoWindowImpl vw);
		void onSurfaceDestroyed(AndroidVideoWindowImpl vw);
	};
	public AndroidVideoWindowImpl(SurfaceView view){
		mView=view;
		mBitmap=null;
		mSurface=null;
		mListener=null;
		view.getHolder().addCallback(new Callback(){
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				synchronized(AndroidVideoWindowImpl.this){
					mBitmap=Bitmap.createBitmap(width,height,Config.RGB_565);
					if (mListener!=null) mListener.onSurfaceReady(AndroidVideoWindowImpl.this);
					mSurface=holder.getSurface();
				}
			}

			public void surfaceCreated(SurfaceHolder holder) {				
			}

			public void surfaceDestroyed(SurfaceHolder holder) {
				synchronized(AndroidVideoWindowImpl.this){
					mBitmap=null;
					if (mListener!=null)
						mListener.onSurfaceDestroyed(AndroidVideoWindowImpl.this);
					mSurface=null;
				}
			}
		});
	}
	public void setListener(VideoWindowListener l){
		mListener=l;
	}
	public Surface getSurface(){
		return mView.getHolder().getSurface();
	}
	public Bitmap getBitmap(){
		return mBitmap;
	}
	//Called by the mediastreamer2 android display filter
	public synchronized void update(){
		if (mSurface!=null){
			try {
				Canvas canvas=mSurface.lockCanvas(null);
				canvas.drawBitmap(mBitmap, 0, 0, null);
				mSurface.unlockCanvasAndPost(canvas);
				
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (OutOfResourcesException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
