package com.lewa.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ThumbnailScreen extends ViewGroup {
    private int mScreenIndex;
    private Launcher mLauncher;
    
    static final int CNT_PER_SCREEN = 9;
    static final int MAX_ROWS = 3;
    static final float SCALE = 0.33333f;
    
    public ThumbnailScreen(Context context) {
        super(context);
    } 

    public ThumbnailScreen(Context context, int index) {
        super(context);

        mLauncher = (Launcher)context;
        mScreenIndex = index;
        setPadding(10, 10, 10, 10);
    } 
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() * 4;
        widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (widthSpecSize * SCALE), MeasureSpec.EXACTLY);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() * 4;
        heightMeasureSpec = MeasureSpec.makeMeasureSpec((int) (heightSpecSize * SCALE), MeasureSpec.EXACTLY);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childLeft = getPaddingLeft();
        int childTop = getPaddingTop();

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.setTag(i + mScreenIndex * CNT_PER_SCREEN);
            
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            childLeft += (childWidth + getPaddingLeft());
            if (i != 0 && (i + 1) % MAX_ROWS == 0) {
                childLeft = getPaddingLeft();
                childTop += (childHeight + getPaddingTop());
            } 
        }
    }
}
