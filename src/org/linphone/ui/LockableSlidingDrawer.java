package org.linphone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SlidingDrawer;

public class LockableSlidingDrawer extends SlidingDrawer {
    private boolean locked = false;
    
    public LockableSlidingDrawer(Context context) {
        super(context, null, 0);
    }
    
    public LockableSlidingDrawer(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }
    
    public LockableSlidingDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event)  {
	    if (this.locked) {
	    	return false;
	    }
	    
	    return super.onTouchEvent(event);
    }
    
    public void lock() {
        this.locked = true;
    }
}