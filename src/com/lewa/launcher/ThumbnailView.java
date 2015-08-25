package com.lewa.launcher;

import java.util.ArrayList;

import com.lewa.launcher.Launcher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.Adapter;
import android.widget.ImageView;
public class ThumbnailView extends SmoothPagedView implements OnLongClickListener, DragSource, DropTarget, DragScroller {
    private Launcher mLauncher;
    private Workspace mWorkspace;
    
    private ThumbnailViewAdapter mAdapter;
    private ThumbnailViewAdapterObserver mAdapterObserver;
    private View mDragView;
    private final ArrayList<View> movedList = new ArrayList<View>();
    private int[] startLoc = new int[2];
    private int[] targetLoc = new int[2];

    private class ThumbnailViewAdapterObserver extends DataSetObserver {
        public void onChanged() {
            refreshThumbnails();
            requestLayout();
        }

        public void onInvalidated() {
            onChanged();
        }
    }
    
    public ThumbnailView(Context context, AttributeSet attrs) {       
        super(context, attrs);
        
        mContentIsRefreshable = false;
        mFadeInAdjacentScreens = false;
        setDataIsReady();
        
        mLauncher = (Launcher)context;
        mAdapterObserver = new ThumbnailViewAdapterObserver();
    }   
       
    public void setAdapter(Workspace workspace, ThumbnailViewAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mAdapterObserver);
        }
        mWorkspace = workspace;
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(mAdapterObserver);
        mAdapterObserver.onInvalidated();
    }
    //yixiao add for piflow 2015.1.15 begin
    public  Adapter getAdapter(){
        return mAdapter;
    }
    //yixiao add for piflow 2015.1.15 end
    
    protected void refreshThumbnails() {
        if (mAdapter == null) {
            return;
        }
        int screenCnt = mAdapter.getCount();
        int existThumbScrCnt = getChildCount();
        int totalThumbScrCnt = (screenCnt - 1) / ThumbnailScreen.CNT_PER_SCREEN + 1;
        
        for (int idx = 0; idx < existThumbScrCnt; idx++) {
            // MUST remove all the views, or else memory leak
            ((ViewGroup)getChildAt(idx)).removeAllViews();
        }

        while (totalThumbScrCnt < existThumbScrCnt) {
            existThumbScrCnt--;
            removeViewAt(existThumbScrCnt);
        }
        
        for(; totalThumbScrCnt > existThumbScrCnt; existThumbScrCnt++) {
            ThumbnailScreen screen = new ThumbnailScreen(mLauncher, existThumbScrCnt);
            screen.layout(0, 0, getWidth(), getHeight());
            addView(screen);
        }

        for(int idx = 0; idx < screenCnt; idx++) {
            View view = mAdapter.getView(idx, null, null);
            ((ThumbnailScreen)getChildAt(getScrIdx(idx))).addView(view);
        }
        invalidate();
    }
    
    public int getScrIdx(int itemIndex) {
        return itemIndex / ThumbnailScreen.CNT_PER_SCREEN;
    }
    
    public int getItemIdx(int itemIndex) {
        return itemIndex % ThumbnailScreen.CNT_PER_SCREEN;
    }
    
    public View getItemAt(int itemIndex) {
        return ((ThumbnailScreen) getChildAt(getScrIdx(itemIndex)))
                .getChildAt(getItemIdx(itemIndex));
    }
    
    @Override
    public boolean onLongClick(View v) {
        //yixiao add #70117 2015.2.13
        if(Launcher.exitPreview){
            return false;
        }
        //yixiao add #70117 2015.2.13
        mDragView = v;
        mLauncher.getDragController().startDrag(v, this, v.getTag(), DragController.DRAG_ACTION_MOVE);
        animateShowAddView(false);
       return true;
    }
    
    private void animateShowAddView(boolean bShow) {
        ThumbnailScreen lastScreen = (ThumbnailScreen) getChildAt(getChildCount() - 1);
        View addView = lastScreen.getChildAt(lastScreen.getChildCount() - 1);
        addView.setVisibility(bShow ? VISIBLE : GONE);
        ObjectAnimator.ofFloat(addView, View.ALPHA, (bShow ? 0 : 1), (bShow ? 1 : 0)).setDuration(200).start();
    }

    @Override
    public void onDragOver(DragObject d) {
        if (mDragView == null) {
            return;
        }
        int width = mDragView.getWidth();
        int height = mDragView.getHeight();
        int dragPos = (Integer) mDragView.getTag();     // the tag of dragview is always changing while draging
        int destPos = d.x / width + d.y / height * ThumbnailScreen.MAX_ROWS + getCurrentPage() * ThumbnailScreen.CNT_PER_SCREEN;
        
        if (destPos > mAdapter.getCount() - 2) {    // one is "add" view
            destPos = mAdapter.getCount() - 2;
        }
        // (d.y / height >= MAX_ROWS) : while draging item out of screen bottom, the y index should not > MAX ROWS
       //zwsun@letek.com 20150108 start
	    if ((destPos < 0) || (dragPos == destPos) || (d.y / height >= ThumbnailScreen.MAX_ROWS)
                || ((d.x <= width / 2) && (d.y <= height / 2)) || (mLauncher.isPiflowPageEnable() && destPos == 0)) {
            return;
        }
		//zwsun@letek.com 20150108 end
        
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
        
        swapScreens(dragPos, destPos);

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

    private void swapScreens(int start, int dest) {
        int startScrIdx = getScrIdx(start);        
        int startItemIdx = getItemIdx(start);
        int destScrIdx = getScrIdx(dest);
        int destItemIdx = getItemIdx(dest);
        
        ThumbnailScreen startScreen = (ThumbnailScreen) getChildAt(startScrIdx);
        ThumbnailScreen destScreen = (ThumbnailScreen) getChildAt(destScrIdx);
        
        View v = startScreen.getChildAt(startItemIdx);  // Must remove drag view first
        startScreen.removeView(v);
        if (startScrIdx != destScrIdx) {
            if (startScrIdx > destScrIdx) { // if drag backward, need add the dest screen's last item to drag screen's first position
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
        // swap workspace view
        View screen = mWorkspace.getChildAt(start);
        //zwsun@lewatek.com PR69212 2015.02.09 start
        if(screen != null)
        {
        mWorkspace.removeView(screen);
        mWorkspace.addView(screen, dest);
        }
        //zwsun@lewatek.com PR69212 2015.02.09 start
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
    public void onDragExit(DragObject dragObject) {

    }

    @Override
    public DropTarget getDropTargetDelegate(DragObject dragObject) {
        return null;
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        d.deferDragViewCleanupPostAnimation = false;
        return true;
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete, boolean success) {
        mDragView.setVisibility(View.VISIBLE);
        animateShowAddView(true);
        updateScreenMap();
    }
    
    @Override
    public boolean supportsFlingToDelete() {
        return false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        
    }

    @Override
    public void onFlingToDelete(DragObject dragObject, int x, int y, PointF vec) {
        
    }
    
    public void updateScreenMap() {
        int totalThumbScrCnt = getChildCount();
        for (int i = 0; i < totalThumbScrCnt; i++) {
            ThumbnailScreen screen = (ThumbnailScreen) getChildAt(i);
            int cntPerScr = screen.getChildCount();
            for (int j = 0; j < cntPerScr; j++) {
                Long screenId = (Long) screen.getChildAt(j).findViewById(R.id.preview_screen).getTag();
                if (screenId != null) {
                    int pos = j + i * ThumbnailScreen.CNT_PER_SCREEN;
                    Log.e("zmq", "updateScreenMap()  pos = "+pos+" , screenId = " + screenId);
                    LauncherModel.getScreenInfoMap().set(pos, screenId);
                    LauncherModel.logScreenMap("ThumbnailView.updateScreenMap set pos , "+pos+"  id,"+screenId);
                }
            }
        }
        LauncherModel.updateScreenItem(mLauncher);
        
        int workspaceScrCnt = mWorkspace.getChildCount();
        for (int j = 0; j < workspaceScrCnt; j++) {
            long screenId = mWorkspace.getCellLayout(j).getScreenId();
            if (screenId == mWorkspace.getCurrentScrId()) {
                mWorkspace.snapToPage(j);
                mWorkspace.setCurrentPage(j); // make the exit animation smooth
            }
            if (screenId == mWorkspace.getDefaultScrId()) {
                mWorkspace.setDefaultPage(j);
            }
        }
    }
    
    public void recycleBitmap() {
        int totalThumbScrCnt = getChildCount();
        for (int i = 0; i < totalThumbScrCnt; i++) {
            ThumbnailScreen screen = (ThumbnailScreen) getChildAt(i);
            int cntPerScr = screen.getChildCount();
            for (int j = 0; j < cntPerScr; j++) {
                ImageView preview = (ImageView) screen.getChildAt(j).findViewById(R.id.preview_screen);
                preview.setImageBitmap(null);
            }
        }
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
    public void scrollTo(int x, int y) {
        if (x < 0) {
            x = 0;
        } 
        super.scrollTo(x, y);
    }
}
