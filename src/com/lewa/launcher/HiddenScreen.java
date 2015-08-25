package com.lewa.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class HiddenScreen extends ViewGroup {
    private int mScreenIndex;
    static int CNT_PER_SCREEN;
    static int ITEMS_PER_COL;
    static int ITEMS_PER_ROW;

    public HiddenScreen(Context context, int index){
        super(context);

        mScreenIndex = index;
        ITEMS_PER_ROW = LauncherModel.getCellCountX();
        ITEMS_PER_COL = LauncherModel.getCellCountY();
        CNT_PER_SCREEN = ITEMS_PER_COL * ITEMS_PER_ROW;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int count = getChildCount();
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        heightMeasureSpec = MeasureSpec.makeMeasureSpec((int)(heightSpecSize / ITEMS_PER_COL), MeasureSpec.EXACTLY);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        widthMeasureSpec = MeasureSpec.makeMeasureSpec((int)(widthSpecSize / ITEMS_PER_ROW), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childLeft = 0;
        int childTop = 0;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.setTag(R.id.item_index, i + mScreenIndex * CNT_PER_SCREEN);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            childLeft += childWidth;
            if (i != 0 && (i + 1) % ITEMS_PER_ROW == 0) {
                childLeft = getPaddingLeft();
                childTop += childHeight;
            }
        }
    }
}
