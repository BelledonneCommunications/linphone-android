package org.linphone.core;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.linphone.OpenGLESDisplay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface.OutOfResourcesException;
import android.view.SurfaceHolder.Callback;

public class AndroidVideoWindowImpl  {
	private boolean useGLrendering;
	private Bitmap mBitmap; 
	private SurfaceView mView;
	private Surface mSurface; 
	private VideoWindowListener mListener;
	private Renderer renderer;
	public static interface VideoWindowListener{
		void onSurfaceReady(AndroidVideoWindowImpl vw);
		void onSurfaceDestroyed(AndroidVideoWindowImpl vw);
	};
	public AndroidVideoWindowImpl(SurfaceView view){
		useGLrendering = (view instanceof GLSurfaceView);
		mView=view;
		mBitmap=null;
		mSurface=null;
		mListener=null;
		view.getHolder().addCallback(new Callback(){
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Log.i("Surface is being changed.");
				if (!useGLrendering) {
					synchronized(AndroidVideoWindowImpl.this){
						mBitmap=Bitmap.createBitmap(width,height,Config.RGB_565);
						mSurface=holder.getSurface();
					}
				}
				if (mListener!=null) mListener.onSurfaceReady(AndroidVideoWindowImpl.this);
				Log.w("Video display surface changed");
			}

			public void surfaceCreated(SurfaceHolder holder) {
				Log.w("Video display surface created");
			}

			public void surfaceDestroyed(SurfaceHolder holder) {
				if (!useGLrendering) {
					synchronized(AndroidVideoWindowImpl.this){
						mSurface=null;
						mBitmap=null;
					}
				}
				if (mListener!=null)
					mListener.onSurfaceDestroyed(AndroidVideoWindowImpl.this);
				Log.d("Video display surface destroyed"); 
			}
		});
		
		if (useGLrendering) {
			renderer = new Renderer();
			((GLSurfaceView)mView).setRenderer(renderer);
			((GLSurfaceView)mView).setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		}
	}
	static final int LANDSCAPE=0;
	static final int PORTRAIT=1;
	public void requestOrientation(int orientation){
		//Surface.setOrientation(0, orientation==LANDSCAPE ? 1 : 0);
		//Log.d("Orientation changed.");
	}
	public void setListener(VideoWindowListener l){
		mListener=l; 
	}
	public Surface getSurface(){
		if (useGLrendering)
			Log.e("View class does not match Video display filter used (you must use a non-GL View)");
		return mView.getHolder().getSurface();
	}
	public Bitmap getBitmap(){
		if (useGLrendering)
			Log.e("View class does not match Video display filter used (you must use a non-GL View)");
		return mBitmap;
	}
	 
	public void setOpenGLESDisplay(int ptr) {
		if (!useGLrendering)
			Log.e("View class does not match Video display filter used (you must use a GL View)");
		renderer.setOpenGLESDisplay(ptr);
	}
	
	public void requestRender() {
		((GLSurfaceView)mView).requestRender();
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
	
    private static class Renderer implements GLSurfaceView.Renderer {
    	int ptr;
    	boolean initPending;
    	int width=-1, height=-1;
    	
    	public Renderer() {
    		ptr = 0; 
    		initPending = false;
    	}
    	 
    	public void setOpenGLESDisplay(int ptr) {
    		this.ptr = ptr;
    		// if dimension are set, we are recreating MS2 graph without
    		// recreating the surface => need to force init
    		if (width > 0 && height > 0) {
    			initPending = true;
    		}
    	}

        public void onDrawFrame(GL10 gl) {
        	if (ptr == 0)
        		return;
        	if (initPending) {
            	OpenGLESDisplay.init(ptr, width, height);
            	initPending = false;
        	}
            OpenGLESDisplay.render(ptr);
        }
        
        public void onSurfaceChanged(GL10 gl, int width, int height) {
        	/* delay init until ptr is set */
        	this.width = width;
        	this.height = height;
        	initPending = true;
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
           
        }
    }
}


