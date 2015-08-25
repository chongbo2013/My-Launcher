package com.lewa.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Created by Fan.Yang on 2014/8/27.
 * FR#51739
 */
public class FolderPreviewLayout extends SmoothPagedView {

    private Folder mFolder;

    public FolderPreviewLayout(Context context) {
        super(context, null);
    }

    public FolderPreviewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContentIsRefreshable = false;
        mFadeInAdjacentScreens = false;
        setDataIsReady();
    }

    @Override
    public void syncPages() {

    }

    @Override
    public void syncPageItems(int page, boolean immediate) {

    }

    public FolderScreen getScreen(int itemIndex) {
        return (FolderScreen) getChildAt(getScrIdx(itemIndex));
    }

    public int getScrIdx(int itemIndex) {
        return itemIndex / FolderScreen.CNT_PER_SCREEN;
    }

    public void snapToFirstPage() {
        int currentX;
        if (!mScroller.isFinished()) {
            currentX = mScroller.getCurrX();
            mScroller.abortAnimation();
        } else {
            currentX = mScroller.getFinalX();
        }
        int whichPage = this.getChildCount() - 1;
        if (whichPage < 0 /*|| currentPage == 0*/) {
            return;
        }

        super.snapToPage(0, -currentX, 650);

        //Folder snap to first Page
        mFolder.setCurrentPage(0);
        mFolder.removeEmptyPageIfNeed();
    }

    public void snapToLastPage() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        int whichPage = this.getChildCount() - 1;
        if (whichPage < 0 || (whichPage == mCurrentPage)) {
            return;
        }

        int childWidth = this.getChildAt(0).getMeasuredWidth();
        int delta = (whichPage - mCurrentPage) * childWidth;
        super.snapToPage(whichPage, delta, 650);
        //Folder snap to last Page
        mFolder.setCurrentPage(whichPage);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Sets the current page.
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
        updateCurrentPageScroll();
        invalidate();
    }

    public void setFolder(Folder folder){
        mFolder = folder;
    }
}
