package com.lewa.launcher;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.TranslateAnimation;
import com.lewa.toolbox.YLog;

public class FolderLayout extends SmoothPagedView implements OnLongClickListener, DragSource, DropTarget, DragScroller
        ,DragLayer.FolderScrollCallbacks {
    private Launcher mLauncher;
    private static final int ON_EXIT_CLOSE_DELAY = 300;
    private Folder mFolder;
    private ShortcutInfo mCurrentDragInfo;
    private View mDragView;
    private Alarm mOnExitAlarm = new Alarm();

    private ArrayList<View> movedList = new ArrayList<View>();
    private int[] startLoc = new int[2];
    private int[] targetLoc = new int[2];
    private int mDragViewWidth;
    private int mDragViewHeight;
    private int mDropPosition ;

    public FolderLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLauncher = (Launcher) context;
        mContentIsRefreshable = false;
        mFadeInAdjacentScreens = false;
        setDataIsReady();
    }

    public boolean onLongClick(View v) {
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return true;
//        if (mLauncher.isFloating()) return true;

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            ShortcutInfo item = (ShortcutInfo) tag;
            if (!v.isInTouchMode()) {
                return false;
            }
            mCurrentDragInfo = item;
            mDragView = (View) v.getParent();
            mLauncher.getDragController().startDrag(mDragView, this, mDragView.getTag(), DragController.DRAG_ACTION_MOVE);
        }
        return true;
    }

    public int getScrIdx(int itemIndex) {
        return itemIndex / FolderScreen.CNT_PER_SCREEN;
    }

    public int getItemIdx(int itemIndex) {
        return itemIndex % FolderScreen.CNT_PER_SCREEN;
    }

    public FolderScreen getScreen(int itemIndex) {
        return (FolderScreen) getChildAt(getScrIdx(itemIndex));
    }

    public View getItemAt(int itemIndex) {
        FolderScreen screen = getScreen(itemIndex);
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
        Object dragInfo = dragObject.dragInfo;
        if (mDropPosition != -1 && dragInfo instanceof ShortcutInfo) {
            LauncherApplication app = ((LauncherApplication) mLauncher.getApplication());
            ShortcutInfo item = (ShortcutInfo) dragInfo;
            FolderInfo info = mFolder.mInfo;
            info.add(item, mDropPosition , true);
        }
    }

    @Override
    public void onDragEnter(DragObject dragObject) {
        if (mDragView == null) {
            mDragView = dragObject.dragView;
            mDragViewWidth = mFolder.getFolderWidth() / FolderScreen.ITEMS_PER_ROW;
            mDragViewHeight = mFolder.getFolderHeight() / FolderScreen.MAX_ROWS;
        }
        mDropPosition = -1;
    }

    @Override
    public void onDragOver(DragObject d) {
        if (mDragView == null) {
            return;
        }

        if (!(d.dragSource instanceof FolderLayout)) {
            dragFromExternal(d);
            return;
        }

        int width = mDragView.getWidth();
        int height = mDragView.getHeight();
        int dragPos = (Integer) mDragView.getTag(R.id.item_index);     // the tag of dragview is always changing while draging
        int destPos = d.x / width + d.y / height * FolderScreen.MAX_ROWS + getCurrentPage() * FolderScreen.CNT_PER_SCREEN;
        int visibleItemCount = mFolder.getInfo().getVisibleCnt();
        if (destPos > visibleItemCount - 1) {
            destPos = visibleItemCount - 1;
        }

        // (d.y / height >= MAX_ROWS) : while draging item out of screen bottom, the y index should not > MAX ROWS
        if ((destPos < 0) || (dragPos == destPos)
                || (d.y / height >= FolderScreen.MAX_ROWS)
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

        //#61069 Add by Fan.Yang
        mFolder.getFolderIcon().loadItemIcons(false);
    }

    private void dragFromExternal(DragObject d) {
        if (mDragViewWidth <= 0 || mDragViewWidth <= 0){
            mDragViewWidth = mFolder.getFolderWidth() / FolderScreen.ITEMS_PER_ROW;
            mDragViewHeight = mFolder.getFolderHeight() / FolderScreen.MAX_ROWS;
        }
        int destPos = d.x / mDragViewWidth + d.y / mDragViewHeight * FolderScreen.MAX_ROWS
                + getCurrentPage() * FolderScreen.CNT_PER_SCREEN;
        int visibleItemCount = mFolder.getInfo().getVisibleCnt();
        int dragPos = visibleItemCount;
        if (destPos > visibleItemCount - 1) {
            destPos = visibleItemCount;
        }
        if ((destPos < 0) || (d.y / mDragViewHeight >= FolderScreen.MAX_ROWS)
                || ((d.x <= mDragViewWidth / 2) && (d.y <= mDragViewHeight / 2))) {
            return;
        }
        mDropPosition = destPos;
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
        FolderScreen startScreen = getScreen(start);
        FolderScreen destScreen = getScreen(dest);
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

    public void setFolder(Folder folder) {
        mFolder = folder;
    }

    public Folder getFolder() {
        return mFolder;
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
        final ItemInfo item = (ItemInfo) d.dragInfo;
        return item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                || item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
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
            return;
        }
        //Start added by weihong, #59213
        DragView dv = d.dragView;
        if (target == null && dv != null){
            ViewGroup vg = (ViewGroup) dv.getParent();
            if (vg != null) {
                vg.removeView(dv);
            }
        }
        // End
        //lqwang - PR66952 - modify begin
        //when animating , do not modify view to avoid exception
        if(mFolder.getState() == Folder.STATE_ANIMATING){
            mTarget = target;
            mDragObject = d;
            mDropSuccess = success;
        }else{
            onDropCompleted(target, d, success);
        }
        //lqwang - PR66952 - modify end
    }

    //lqwang - PR66952 - modify begin
    private View mTarget;
    private DragObject mDragObject;
    private boolean mDropSuccess;

    public void onDropCompletedCallback(){
        if(mTarget != null && mDragObject != null){
            onDropCompleted(mTarget,mDragObject,mDropSuccess);
            mTarget = null;
            mDragObject = null;
        }
    }

    private void onDropCompleted(View target, DragObject d, boolean success) {
        mDragView.setVisibility(View.VISIBLE);
        if (success) {
            if (target != this) {
                if (target instanceof Hotseat && mFolder.getInfo().getVisibleCnt() > 2) {
                    mFolder.mDeleteFolderOnDropCompleted = false;
                }
                if (!(target instanceof DeleteDropTarget)) {
                    mFolder.getInfo().remove(mCurrentDragInfo);
                }
            }
        } else {
            // The drag failed, we need to return the item to the folder
            if (target == this) {
                mFolder.getFolderIcon().onDrop(d);
            } else{
                mLauncher.getWorkspace().scrollFolderPreview(null,true);//lqwang - pr69377 - modify
            }
            if (mOnExitAlarm.alarmPending()) {
                // mSuppressFolderDeletion = true;
            }
        }

        if (target != this) {
            if (mOnExitAlarm.alarmPending()) {
                mOnExitAlarm.cancelAlarm();
                // if the item of folder only one after remove, auto close it, otherwise not close folder when drop icon
                // from folder quick
                //#61319 Modify by Fan.Yang
                //if (mFolder.getInfo().getVisibleCnt() == 1 && !mLauncher.isFloating()) {
                //    completeDragExit();
                //}
            }
        }

        mFolder.mDeleteFolderOnDropCompleted = true;

        // Reordering may have occured, and we need to save the new item locations. We do this once
        // at the end to prevent unnecessary database operations.
        mFolder.updateItemLocationsInDatabase();
        //#58802 Add by Fan.Yang
        mFolder.getFolderIcon().loadItemIcons(false);
        //mFolder.getFolderIcon().resetPreviewScroll();
        mCurrentDragInfo = null;
    }
    //lqwang - PR66952 - modify end


    public void onDragExit(DragObject d) {
        boolean isAlarm = true;
        int y = d.y;
        // 从文件夹側边退出不会退出文件夹，防止误操作，#62527
        if (y < mFolder.folderX && y < mFolder.folderX + mFolder.getCurrentHeight()) {
            isAlarm = false;
        }
        if (!d.dragComplete && isAlarm) {
            mOnExitAlarm.setOnAlarmListener(mOnExitAlarmListener);
            mOnExitAlarm.setAlarm(ON_EXIT_CLOSE_DELAY);
        }
    }

    OnAlarmListener mOnExitAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            completeDragExit();
        }
    };

    public void completeDragExit() {
        mLauncher.closeFolder();
    }

    public void moveToDefaultPage() {
        mCurrentPage = 0;
        updateCurrentPageScroll();
        notifyPageSwitchListener();
        invalidate();
    }

    @Override
    protected void snapToPage(int whichPage) {
        // TODO Auto-generated method stub
        if (mScrollMode == X_LARGE_MODE) {
            super.snapToPage(whichPage, MAX_PAGE_SNAP_DURATION);
        } else {
            super.snapToPage(whichPage);
        }
    }

    public void snapToLastPage() {
        int whichPage = this.getChildCount() - 1;
        int currentPage = this.getCurrentPage();
        int childWidth = this.getChildAt(0).getMeasuredWidth();
        int delta = (whichPage - currentPage) * childWidth;
        super.snapToPage(whichPage, delta, /*PAGE_SNAP_ANIMATION_DURATION+(whichPage - currentPage) * 200*/400);
    }

    @Override
    protected void pageBeginMoving() {
        // TODO Auto-generated method stub
        super.pageBeginMoving();
    }

    @Override
    protected void pageEndMoving() {
        // TODO Auto-generated method stub
        super.pageEndMoving();
        if (mFolder.getState() == Folder.STATE_ANIMATING) {
            mFolder.closeAnimationEnd();
            mFolder.getFolderIcon().setVisibility(View.VISIBLE);
        }
        if (mLauncher.isFloating()) {
            mFolder.pageEndMoving();
        }
        //
        FolderIcon folderIcon = mFolder.getFolderIcon();
        folderIcon.setCurrentPage(getCurrentPage());
        mFolder.initIconPositionOffset();
        // 跨屏拖动时，实时更新边框，#62527
        mFolder.invalidate();
    }

    @Override
    public void scrollTo(int x, int y) {
        // TODO Auto-generated method stub
        if (getChildCount() <= 1) {
            x = 0;
        }

        super.scrollTo(x, y);
    }

    // Add by Fan.Yang for FR#51744
    @Override
    public void onActionDown(MotionEvent event) {
        acquireVelocityTrackerAndAddMovement(event);
        mDownMotionX = event.getX();
        mLastMotionX = event.getX();
        mActivePointerId = event.getPointerId(0);
    }

    @Override
    public void onActionMove(MotionEvent event) {
        acquireVelocityTrackerAndAddMovement(event);
        float x = event.getX();
        scrollBy((int) (mLastMotionX - x), 0);
        mLastMotionX = x;
    }

    @Override
    public void onActionUp(MotionEvent event) {
        acquireVelocityTrackerAndAddMovement(event);
        final int deltaX = (int) (event.getX() - mDownMotionX);
        View child = getPageAt(getCurrentPage());
        if (child == null) {
            return;
        }
        final int pageWidth = getScaledMeasuredWidth(child);
        final VelocityTracker velocityTracker = mVelocityTracker;
        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        int velocityX = (int) velocityTracker.getXVelocity(mActivePointerId);

        // In the case that the page is moved far to one direction and then is flung
        // in the opposite direction, we use a threshold to determine whether we should
        // just return to the starting page, or if we should skip one further.
        boolean returnToOriginalPage = false;
        if (Math.abs(deltaX) > pageWidth * 0.33f||(Math.abs(velocityX)>mFlingThresholdVelocity)) {
            returnToOriginalPage = true;
        }
        int finalPage = mCurrentPage;
        if (deltaX < 0) {
            finalPage = returnToOriginalPage ? Math.max((getChildCount() - 1), mCurrentPage + 1) : mCurrentPage;
        } else {
            finalPage = returnToOriginalPage ? Math.max(0, mCurrentPage - 1) : mCurrentPage;
        }
        snapToPageWithVelocity(finalPage, 300);
        releaseVelocityTracker();
    }

    /**
     * Sets the current page.
     * FR#51739 add by Fan.Yang
     */
    void setCurrentPage(int currentPage) {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getChildCount() == 0) {
            return;
        }

        mCurrentPage = Math.max(0, Math.min(currentPage, getPageCount() - 1));
        //updateCurrentPageScroll();
        int childWidth = mFolder.getFolderWidth();
        int newX = currentPage * childWidth;
        scrollTo(newX, 0);
        mScroller.setFinalX(newX);
        mScroller.forceFinished(true);
        invalidate();
    }

    ShortcutInfo getCurrentDragInfo(){
        return mCurrentDragInfo;
    }

    /*
    * return the index of current drag in mInfo
    */
    int getCurrentDragIndex() {
        int index = -1;
        if (mCurrentDragInfo != null) {
            ArrayList<ShortcutInfo> contents = mFolder.getInfo().contents;
            int i = 0;
            for (ShortcutInfo info : contents) {
                if (info.equals(mCurrentDragInfo)) {
                    index = i;
                }
                i++;
            }
        }
        return index;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mFolder.isOpen()) {
            return super.onInterceptTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mFolder.isOpen()) {
            return super.onTouchEvent(ev);
        } else {
            return false;
        }
    }

    public void cancelExitAlarm() {
        if (mOnExitAlarm != null) {
            mOnExitAlarm.cancelAlarm();
        }
    }
}
