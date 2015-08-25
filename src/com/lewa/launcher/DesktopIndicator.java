package com.lewa.launcher;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class DesktopIndicator extends LinearLayout {
    private int mTotal;
    private int mCurrent;
    private int padding;
    private final LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT, 1.0f);
    private final float SCALE = 0.8f;
    //lqwang - PR65502 - add begin
    private Paint sPaint = new Paint();
    //lqwang - PR65502 - add end

    public DesktopIndicator(Context context, AttributeSet attrs) {
    	super(context, attrs);
    	
        setFocusable(false);
        setWillNotDraw(false);
        padding = getResources().getDimensionPixelSize(R.dimen.indicator_lr_padding);
    }
    
    public void setItems(int total, int current) {
        mTotal = total;
        mCurrent = current;
        removeAllViews();
        if (mTotal < 1) {
            return;
        }
        
        for (int i = 0; i < mTotal; i++) {
            ImageView iv = new ImageView(getContext());
            iv.setImageResource(R.drawable.pager_dots);
            addView(iv, lp);
            if (i == mCurrent) {
                ((TransitionDrawable) iv.getDrawable()).startTransition(50);
            }else{
                iv.setScaleX(SCALE);
                iv.setScaleY(SCALE);
            }
        }
        
        int parentWidth = total * ((ImageView)getChildAt(0)).getDrawable().getIntrinsicWidth() + total * padding;
        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
        DisplayMetrics display = getResources().getDisplayMetrics();
        if (parentWidth >= display.widthPixels) {
            params.width = display.widthPixels;
        } else {
            params.width = parentWidth; 
        }
        setLayoutParams(params);
    }
    
    public void indicate(float percent) {
    	int position = Math.round(mTotal * percent);
    	if (position != mCurrent) {
        	mCurrent = position;
        	updateLayout();
    	}
    }
    
    public void fullIndicate(int position) {
        if (position != mCurrent) {
            mCurrent = position;
            updateLayout();
        }
    }
    
    void updateLayout() {
        int count =  getChildCount();
        for (int i = 0; i < count; i++) {
            final ImageView child = (ImageView) getChildAt(i);
            TransitionDrawable tmp = (TransitionDrawable) child.getDrawable();
            if (i == mCurrent) {
                child.setScaleX(1.0f);
                child.setScaleY(1.0f);
                tmp.startTransition(50);
            } else {
                child.setScaleX(SCALE);
                child.setScaleY(SCALE);
                tmp.resetTransition();
            }
        }
    }
    //lqwang - PR65502 - add begin
    public void setHwLayerEnable(boolean enable){
        setLayerType(enable ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE , sPaint);
    }
    //lqwang - PR65502 - add end
}