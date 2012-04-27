package org.linphone.ui;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class CameraView extends ViewGroup implements SurfaceHolder.Callback {
    
	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

    CameraView(Context context) {
        super(context);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

	SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedSizes;
    Camera mCamera;
    int mCameraId;

    public void setCamera(Camera camera, int id) {
        mCamera = camera;
        mCameraId = id;
        if (mCamera != null) {
            mSupportedSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    public void switchCamera(Camera camera, int id) {
       setCamera(camera, id);
       try {
           camera.setPreviewDisplay(mHolder);
       } catch (IOException exception) {
    	   
       }
       Camera.Parameters parameters = camera.getParameters();
       parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
       requestLayout();

       camera.setParameters(parameters);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);;
        int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);;
        setMeasuredDimension(width, height);

        if (mSupportedSizes != null) {
        	mPreviewSize = getOptimalPreviewSize(mSupportedSizes, width, height);
        }
        
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    	if (display.getRotation() == Surface.ROTATION_90 || display.getRotation() == Surface.ROTATION_270) {
    		Size tempSize = mPreviewSize;
    		mPreviewSize.width = tempSize.height;
    		mPreviewSize.height = tempSize.width;
    	}
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the surface view
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
        	
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	if (mCamera == null)
    		return;
    	
    	mCamera.stopPreview();
    	Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Camera.Parameters parameters = mCamera.getParameters();
        
        int rotation = 0;
        if(display.getRotation() == Surface.ROTATION_90) {
            rotation = 90;
        }
        else if(display.getRotation() == Surface.ROTATION_270) {
            rotation = 270;
        }
        else if (display.getRotation() == Surface.ROTATION_180) {
            rotation = 180;
        }
        
        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(mCameraId, cameraInfo);
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
        	mCamera.setDisplayOrientation((cameraInfo.orientation - rotation + 360) % 360);
	    } else {
	    	mCamera.setDisplayOrientation((cameraInfo.orientation + rotation) % 360);
	    }
        
        requestLayout();        
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }
}