package com.lewa.launcher;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

import com.lewa.launcher.DragController.DragListener;

public class Hotseat extends LinearLayout implements DragSource, DropTarget,
    OnLongClickListener, DragListener {
    private static final int MAX_HOTSEAT = 4;
    private static final int HOTSEAT_SCREEN = -1;
    private Launcher mLauncher;
    private int mItemWidth;
    private IconCache mIconCache;
    private View mDragView;
    private ItemInfo mDragInfo;
    private int mDragPos = -1;
    private static boolean isSwap = false;
    private static boolean bSuccess = true;
    private static boolean bExchange = false;
    private static boolean bUninstall = false;
    
    private final int[] startLoc = new int[2];
    private final int[] destLoc = new int[2];

    private boolean debug = false;

    public Hotseat(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLauncher = (Launcher) context;
        mIconCache = ((LauncherApplication) context.getApplicationContext()).getIconCache();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        updateItemValidWidth();
    }
    
    public void setSeat(ItemInfo info) {
        ShortcutIcon shortcut = (ShortcutIcon) getChildAt(info.cellX);
        if(shortcut != null){
            shortcut.applyFromShortcutInfo((ShortcutInfo)info, mIconCache);
            shortcut.setText(" ");
            shortcut.setVisibility(View.VISIBLE);
            shortcut.mFavorite.setOnClickListener(mLauncher);
            shortcut.mFavorite.setOnLongClickListener(this);
        }
    }
    
    @Override
    public void onDrop(DragObject d) {
        //FR#51739 add by Fan.Yang
        mLauncher.getWorkspace().scrollFolderPreview((ItemInfo) d.dragInfo, true);
        ItemInfo dragInfo = (ItemInfo) d.dragInfo;
        if (dragInfo == null || dragInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            isSwap = true;
            if (debug) Log.e(TAG, "onDrop() info = null or the drag item is already on Hotseat, return !!");
            return;
        }
        
        int pos = d.x / mItemWidth;
        if (pos <= 0 ) {
            pos = 0;
        } else if (pos > getVisibleCnt() - 1){
            pos =  getVisibleCnt() - 1;
        }

        int cellx = getCellXByPos(pos);
        ItemInfo seatinfo = (ItemInfo) getChildAt(cellx).getTag();   // hotseat item
        mDragInfo = seatinfo;
        isSwap = true;
        if (seatinfo != null) { // exchange item
            if (debug) Log.i(TAG, "onDrop() currentInfo != null , no more seat for the drop item, exchange items !");
            Workspace workspace = mLauncher.getWorkspace();
                if (d.dragSource instanceof Workspace) {
                    bExchange = true;
                    View child = mLauncher.createShortcut((ShortcutInfo)seatinfo);
                    workspace.addInScreen(child, dragInfo.container, dragInfo.screen, dragInfo.cellX, dragInfo.cellY, dragInfo.spanX, dragInfo.spanY);
                    LauncherModel.moveItemInDatabase(mLauncher, seatinfo, dragInfo.container, dragInfo.screen, dragInfo.cellX, dragInfo.cellY);
                } else if (d.dragSource instanceof FolderLayout) {
                    FolderInfo finfo = ((FolderLayout)d.dragSource).getFolder().getInfo();
                    finfo.add((ShortcutInfo)seatinfo);
                }
            
            isSwap = false;
            mDragView = null;
            mDragPos = -1;
        }
        
        if (debug) Log.i(TAG, "onDrop() setSeat info = "+dragInfo.title+" at pos = "+cellx);
        LauncherModel.moveItemInDatabase(mLauncher, dragInfo, LauncherSettings.Favorites.CONTAINER_HOTSEAT, HOTSEAT_SCREEN, cellx, 0);
        setSeat(dragInfo);
    }

    @Override
    public void onDragOver(final DragObject d) {
        int pos = d.x / mItemWidth;
        int cellX = getCellXByPos(pos);  
        View toView = getChildAt(cellX); 

        final int emptyPos = getEmptySeatPos();
        if (mDragView == null && emptyPos == -1) {
            if (debug) Log.e(TAG, "onDragOver() try to drag item to hotseat but no more seat,  return !");
            return;
        }
        
        if (mDragView == null && emptyPos != -1) {
            final View emptyView = getChildAt(emptyPos);
            emptyView.setVisibility(View.INVISIBLE);
            updateItemValidWidth();
            mDragPos = d.x / mItemWidth;
            int vericellx = getCellXByPos(mDragPos);
            if (debug) Log.i(TAG, "mDragPos = "+mDragPos+" , verified cellX = "+vericellx);
            if (emptyPos != vericellx && vericellx < getChildCount()) {
                removeView(emptyView);
                addView(emptyView, vericellx);
            }
            mDragView = emptyView;
            if (debug) Log.e(TAG, "onDragOver() drag from outside and has empty seat,  return !");
            return;
        }

        int dragPos = mDragPos;
        if (dragPos == pos || cellX > MAX_HOTSEAT - 1) {
            if (debug) Log.i(TAG, "onDragOver() drag from inside and the drag item is on its seat,  return !");
            return;
        }

        View dragView = mDragView;
        int idx = getCellXByPos(dragPos);
        if (idx > getChildCount() - 1) {
            if (debug) Log.e(TAG, "idx = "+idx+" > max index, return !");
            return;
        }
        removeViewAt(idx);
        if (dragView.getParent() != null) {
            removeView(dragView);
        }
        addView(dragView, cellX);
        if (debug) Log.i(TAG, "onDragOver() add dragview to position = "+cellX+" , drag from inside and swap item's position,  return!!!");
        mDragPos = pos;

        toView.getLocationOnScreen(startLoc);
        dragView.getLocationOnScreen(destLoc);
        TranslateAnimation anim = new TranslateAnimation((startLoc[0] - destLoc[0]), 0, (startLoc[1] - destLoc[1]), 0);
        anim.setDuration(400);
        toView.startAnimation(anim);
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    public void onDragEnter(DragObject dragObject) {
        mLauncher.getWorkspace().removeEnterFloatingCallbacks();
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
        ItemInfo iteminfo = (ItemInfo)d.dragInfo;
        d.deferDragViewCleanupPostAnimation = false;
        if (iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET 
                && iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER && getShortcutAdded() < MAX_HOTSEAT) {
            return true;
        } else {
            d.cancelled = true;
            return false;
        }
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete, boolean success) {
        // if swap items in hotseat, dragview should be visible
        bSuccess = success;        
        if (target instanceof DeleteDropTarget
                && ((ItemInfo) d.dragInfo).itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            bUninstall = true;
        }
        if (isSwap || !success || bUninstall) {
            if (mDragView != null) {
                mDragView.setVisibility(View.VISIBLE);
            }
        } else {
            // if drag the hotseat item to workspace after this item is swapped, dragview should be gone
            if (mDragView != null) {
                mDragView.setVisibility(View.GONE);
            }
        }
        //FR#51739 add by Fan.Yang
        //zwsun@lewatek.com PR68581 PR67030 PR68651 20150202 start
        /*if (d.dragSource instanceof Hotseat) {
            mLauncher.getWorkspace().scrollFolderPreview((ItemInfo) d.dragInfo, true);
            mLauncher.getWorkspace().releaseDragOutline();
        }*/
        //zwsun@lewatek.com PR68581 PR67030 PR68651 20150202 end
    }

    @Override
    public boolean onLongClick(View view) {
        if (mLauncher.isWorkspaceLocked()) {
            return true;
        }
        View v = (View)view.getParent();
        ShortcutInfo info = (ShortcutInfo) v.getTag();
        mDragView = v;
        mDragView.setPressed(false);
        mDragPos = getDragPosBycellX(info.cellX);
        setViewDraging(v,true);
        mLauncher.getWorkspace().onDragStartedWithItem(v);
        mLauncher.getWorkspace().beginDragShared(v, this);
        v.setVisibility(View.INVISIBLE);
        //FR#51739 add by Fan.Yang
        mLauncher.getWorkspace().scrollFolderPreview(null, false);
        return true;
    }

    private void setViewDraging(View v,boolean isDraging) {
        if(v != null && v instanceof ShortcutIcon){
            ((ShortcutIcon) v).setIsDraging(isDraging);
        }
    }


    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        
    }

    @Override
    public void onDragEnd() {
        //zwsun@lewatek.com PR68581 PR67030 PR68651 20150202 start
        mLauncher.getWorkspace().scrollFolderPreview(null, true);
        //zwsun@lewatek.com PR68581 PR67030 PR68651 20150202 end
        if (mDragView == null) {
            return;
        }
        setViewDraging(mDragView,false);
        ItemInfo fromInfo = (ItemInfo) mDragInfo;
        if (fromInfo == null && !isSwap && bSuccess && !bUninstall) {
            mDragView.setVisibility(View.GONE);
            ((ShortcutIcon)mDragView).clearReflectCache();
            mDragView.setTag(null);
        }
        bSuccess = true;
        isSwap = false;

        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View child = getChildAt(i);
            if (child == null || child.getTag() == null) {
                continue;
            }
            if (child.getVisibility() == GONE) {
                child.setTag(null);
                ((ShortcutIcon)child).clearReflectCache();
                continue;
            }
            ItemInfo info = (ItemInfo) child.getTag();
            info.cellX = i;
            LauncherModel.addOrMoveItemInDatabase(mLauncher, info, LauncherSettings.Favorites.CONTAINER_HOTSEAT, HOTSEAT_SCREEN, i, 0);
        }
        bExchange = false;
        bUninstall = false;
        mDragPos = -1;
        mDragInfo = null;
        mDragView = null;
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
    
    private int getEmptySeatPos() {
        int result = -1;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() == GONE) {
                result = i;
                break;
            }
        }
        return result;
    }
    
    private int getVisibleCnt() {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() != GONE) {
                result++;
            }
        }
        return result;
    }

    private int getShortcutAdded(){
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() != GONE && ((ShortcutIcon)v).mInfo != null) {
                result++;
            }
        }
        return result;
    }

    private int getDragPosBycellX(int cellX) {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            if (i == cellX) {
                break;
            }
            View v = getChildAt(i);
            if (v != null && v.getVisibility() != GONE) {
                result++;
            }
        }
        return result;
    }

    private int getCellXByPos(int pos) {
        int result = 0;
        int i = 0;
        for (; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() == View.GONE) {
                continue;
            }
            if (result == pos) {
                break;
            }
            result++;
        }
        if (i > MAX_HOTSEAT - 1 && result == 0) {
            i = 0;
        }
        return i;
    }

    private void updateItemValidWidth() {
        int cnt = getVisibleCnt();
        if (cnt != 0) {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / cnt;
        } else {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        }
    }

    public static boolean isExchangeFromWorkspace() {
        return bExchange;
    }

    void resetLayout() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View v = getChildAt(i);
            v.setVisibility(GONE);
            ((ShortcutIcon)v).clearReflectCache();
            v.setTag(null);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            return true;
        }
//        CheckoutMove(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            return true;
        }
//        CheckoutMove(ev);
        return true;
    }

    private void CheckoutMove(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastDownX = ev.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                mXdiff = ev.getX() - mLastDownX;
                if(mXdiff < 0 && Math.abs(mXdiff) > ENTER_EDIT_XDIFF){
                    mLauncher.enterEditMode(EditModeLayer.ANIMORIENTATION.HORIZONTAL);
                }
                break;
        }
    }

    private float mLastDownX;
    private float mXdiff;
    private static final int ENTER_EDIT_XDIFF = 100;
}
