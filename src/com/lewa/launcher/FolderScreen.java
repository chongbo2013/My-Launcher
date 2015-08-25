package com.lewa.launcher;

import android.content.Context;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class FolderScreen extends ViewGroup {
    static int CNT_PER_SCREEN = 9;
    static int MAX_ROWS = 3;
    static int ITEMS_PER_ROW = 3;

    private int mScreenIndex;
    private int childHeight;
    private int childSpace;
    Context mContext;
    private Paint sPaint = new Paint();
    //FR#51739 add by Fan.Yang
    public FolderScreen(Context context, int space, int height) {
        super(context);
        childSpace = space;
        childHeight = height;
    }

    public FolderScreen(Context context, int index){
        super(context);
        mContext = context;
        mScreenIndex = index;
        childHeight = context.getResources().getDimensionPixelSize(R.dimen.folder_cell_height);
        childSpace = 0;
        ITEMS_PER_ROW = getResources().getInteger(R.integer.folder_max_count_x);
        MAX_ROWS = getResources().getInteger(R.integer.folder_max_count_y);
        CNT_PER_SCREEN = getResources().getInteger(R.integer.folder_max_num_items);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int count = getChildCount();
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        if (childSpace != 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        } else {
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
            widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (widthSpecSize / ITEMS_PER_ROW), MeasureSpec.EXACTLY);
        }
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
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            childLeft += childWidth;
            if (i != 0 && (i + 1) % ITEMS_PER_ROW == 0) {
                childLeft = getPaddingLeft();
                childTop += childHeight + childSpace;
            } else {
                childLeft += childSpace;
            }
            
            if (child != null && child instanceof ShortcutIcon) {
                ShortcutIcon icon = (ShortcutIcon)child;
                icon.mInfo.cellX = i % ITEMS_PER_ROW;
                icon.mInfo.cellY = i / ITEMS_PER_ROW;
                icon.mInfo.screen = mScreenIndex;
                child.setTag(R.id.item_index, i + mScreenIndex * CNT_PER_SCREEN);
            }
        }
    }
    
    @Override
    public void addView(View child) {
        if (child != null && child instanceof ShortcutIcon) {
            ShortcutIcon icon = (ShortcutIcon)child;
            int count = getChildCount();
            icon.mInfo.cellX = count % ITEMS_PER_ROW;
            icon.mInfo.cellY = count / ITEMS_PER_ROW;
            icon.mInfo.screen = mScreenIndex;
        }
        super.addView(child);
    }

    public void setChildrenTranslationAndScale(int index, float translationX, float translationY, float appNameAlpha) {
        ShortcutIcon child = (ShortcutIcon) getChildAt(index);
        child.setTranslationX(translationX);
        child.setTranslationY(translationY);
        setChildrenNameAlpha(index,appNameAlpha);
    }

    public void setChildrenNameAlpha(int index, float alpha) {
        ShortcutIcon child = (ShortcutIcon) getChildAt(index);
        TextView appName = child.mAppName;
        appName.setAlpha(alpha);
    }

    public void setChildrenNameVisible(int index, boolean isVisible) {
        ShortcutIcon child = (ShortcutIcon) getChildAt(index);
        ShortcutInfo info = (ShortcutInfo) child.getTag();
        if (info == null || info.title == null) {
            return;
        }
        BubbleTextView bubbleTextView = child.mFavorite;
        TextView appName = child.mAppName;
        if (!isVisible) {
            bubbleTextView.setText(Utilities.convertStr(getContext(), info.title.toString()));
            appName.setAlpha(0f);
        } else {
            bubbleTextView.setText("");
            appName.setText(Utilities.convertStr(getContext(), info.title.toString()));
        }
    }

    public void setHwLayerEnable(boolean enable){
        setLayerType(enable ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE , sPaint);
    }
}
