package com.lewa.launcher;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.animation.TranslateAnimation;
public class HiddenLayout extends SmoothPagedView implements OnLongClickListener, DragSource, DropTarget, DragScroller {
    private Launcher mLauncher;
    private Hidden mHidden;
    private View mDragView;
    
    private ArrayList<View> movedList = new ArrayList<View>();
    private int[] startLoc = new int[2];
    private int[] targetLoc = new int[2];
    
    public HiddenLayout(Context context, AttributeSet attrs) {       
        super(context, attrs);
        
        mLauncher = (Launcher)context;
        mContentIsRefreshable = false;
        mFadeInAdjacentScreens = false;
        setDataIsReady();
    }   
    
    public boolean onLongClick(View v) {
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return true;
        
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            if (!v.isInTouchMode()) {
                return false;
            }
            mDragView = (View) v.getParent();
            mLauncher.getDragController().startDrag(mDragView, this,
                    mDragView.getTag(), DragController.DRAG_ACTION_MOVE);
        }
        return true;
    }
       
    public int getScrIdx(int itemIndex) {
        return itemIndex / HiddenScreen.CNT_PER_SCREEN;
    }
    
    public int getItemIdx(int itemIndex) {
        return itemIndex % HiddenScreen.CNT_PER_SCREEN;
    }
    
    public HiddenScreen getScreen(int itemIndex) {
        return (HiddenScreen) getChildAt(getScrIdx(itemIndex));
    }

    public View getItemAt(int itemIndex) {
        HiddenScreen screen = getScreen(itemIndex);
        if (screen != null) {
            return screen.getChildAt(getItemIdx(itemIndex));
        }
        return null;
    }
    
    @Override
    public void syncPages() {
        
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
        
    }

    @Override
    public boolean onEnterScrollArea(int x, int y, int direction) {
        return true;
    }

    @Override
    public boolean onExitScrollArea() {
        return true;
    }

    @Override
    public boolean isDropEnabled() {
        return true;
    }

    @Override
    public void onDrop(DragObject dragObject) {
        
    }

    @Override
    public void onDragEnter(DragObject dragObject) {
        
    }
    
    @Override
    public void onDragOver(DragObject d) {
        if (mDragView == null) {
            return ;
        }
        
        int width = mDragView.getWidth();
        int height = mDragView.getHeight();
        int dragPos = (Integer) mDragView.getTag(R.id.item_index);     // the tag of dragview is always changing while draging
        int destPos = d.x / width + d.y / height * HiddenScreen.ITEMS_PER_COL + getCurrentPage() * HiddenScreen.CNT_PER_SCREEN;
        int visibleItemCount = mHidden.getItemCount();
        if (destPos > visibleItemCount - 1) {
            destPos = visibleItemCount - 1;
        }
        
        // (d.y / height >= MAX_ROWS) : while draging item out of screen bottom, the y index should not > MAX ROWS
        if ((destPos < 0) || (dragPos == destPos)
            || (d.y / height >= HiddenScreen.ITEMS_PER_COL)
            || ((d.x <= width / 2) && (d.y <= height / 2))) {
            return;
        }
        
        // Store movedView
        int movedSize = Math.abs(destPos - dragPos) + 1;
        movedList.clear();
        int transPos = destPos;
        for (int i = movedSize; i > 0; i--) {
            View movedView = getItemAt(transPos);
            movedList.add(movedView);
            if (destPos > dragPos) {
                transPos--;
            } else {
                transPos++;
            }
        }
        
        swapApps(dragPos, destPos);
        
        for (int i = movedSize - 2; i >= 0; i--) {
            View transView = movedList.get(i);
            View targetView = movedList.get(i + 1);
            if (transView != null && targetView != null) {
                transView.getLocationOnScreen(startLoc);
                targetView.getLocationOnScreen(targetLoc);
                playAnimation(transView, startLoc[0], targetLoc[0], startLoc[1], targetLoc[1]);
            }
        }
    }

    private void playAnimation(View movedView, int fromX, int toX, int fromY, int toY) {
        int xOffest = toX - fromX;
        int yOffest = toY - fromY;
        TranslateAnimation anim = new TranslateAnimation(-xOffest, 0, -yOffest, 0);
        anim.setDuration(400);
        movedView.startAnimation(anim);
    }

    private void swapApps(int start, int dest) {
        int startScrIdx = getScrIdx(start);        
        int startItemIdx = getItemIdx(start);
        int destScrIdx = getScrIdx(dest);
        int destItemIdx = getItemIdx(dest);
        HiddenScreen startScreen = getScreen(start);
        HiddenScreen destScreen = getScreen(dest);
        if (startScreen == null || destScreen == null) {
            return;
        }
        
        View v = startScreen.getChildAt(startItemIdx);  // Must remove drag view first
        startScreen.removeView(v);
        if (startScrIdx != destScrIdx) {
            if (startScrIdx > destScrIdx) {
                int lastIdx = destScreen.getChildCount() - 1;
                View last = destScreen.getChildAt(lastIdx);
                destScreen.removeView(last);
                startScreen.addView(last, 0);
            } else {
                View first = destScreen.getChildAt(0);
                destScreen.removeView(first);
                startScreen.addView(first, -1);
            }
        }
        destScreen.addView(v, destItemIdx);
    }
    
    public void setHidden(Hidden hidden) {
        mHidden = hidden;
    }

    @Override
    public void onFlingToDelete(DragObject dragObject, int x, int y, PointF vec) {
        
    }

    @Override
    public DropTarget getDropTargetDelegate(DragObject dragObject) {
        return null;
    }

    public boolean acceptDrop(DragObject d) {
        d.deferDragViewCleanupPostAnimation = false;
        final ItemInfo item = (ItemInfo)d.dragInfo;
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION 
                || itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT));
    }


    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    @Override
    public boolean supportsFlingToDelete() {
        return false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        
    }
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (mDragView == null) {
            return ;
        }
        
        mDragView.setVisibility(View.VISIBLE);
//        mHidden.updateItemLocationsInDatabase();
    }
    
    public void onDragExit(DragObject d) {
    }
    

}
