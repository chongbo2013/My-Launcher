package com.lewa.launcher;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import android.appwidget.AppWidgetHostView;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.YLog;

import lewa.content.res.IconCustomizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lewa.launcher.CellLayout.CellInfo;
import com.lewa.launcher.DropTarget.DragObject;
import com.lewa.launcher.PagedView.PageSwitchListener;

public class FloatingLayer extends FrameLayout implements DragSource,
        /*DropTarget,*/ PageSwitchListener, Folder.FolderStateListener {
    public static final int FLOATING_TIMEOUT = 1500;
    public static final float FLOATING_SCALE = 0.8f;
    private static final String TAG = "FloatingLayer";
    private static final int ANIM_MAX_NUM_REST_MOVE = 2;
    private static final int ANIM_MAX_NUM_REST_IN = 6;
    private static final int ANIM_TOGGLE_DURATION = 300;
    private static final int ANIM_RANGING_DURATION = 300;
    private static final int ANIM_SWITCHING_DURATION = 600;
    private static final int ANIM_SHRINK_DURATION = 200;
    private static final int FLOATING_BG_COLOR = 0x50000000;
    private static final boolean AUTO_FILL_WHEN_EQUAL = true;
    private int mRestIconHeight;
    private int mReatPadding;

    private LinearLayout.LayoutParams mRestIconLp;
    private LinearLayout.LayoutParams mFolderIconLp;
    HorizontalScrollView scroll;
    private ShadowLinearLayout mRestContainer;
    private ShadowLinearLayout mRestSeat;
    private ImageView addFolderImage;
    private CellLayout mCellLayout;
    private CellLayout mWorkspaceCellLayout;
    private Launcher mLauncher;
    private Workspace mWorkspace;
    private Folder mOpeningFolder;
    private AnimatorSet mRangingAnimator;
    private AnimatorSet mSwitchingAnimator;
    private AnimatorSet mRestSeatSwitchingAnimator;
    private AnimatorSet mStartEndAnimator;
    private int mStartPage;
    private TimeInterpolator mSwitchInterpolator = new BounceInterpolator();
    private TimeInterpolator mRestInInterpolator = new AccelerateInterpolator();
    private TimeInterpolator mRestOutInterpolator = new DecelerateInterpolator();
    private CellInfo mPendingCell;
    private int[][] mSwitchingCell;
    private boolean mFloating = false;
    private Rect mRestSeatRect;
    private boolean isDropIntoRestSeat = false;
    private boolean isNewFolderHide = false;

    private static HashMap<Long, Long> restoredFolder = new HashMap<Long, Long>();

    public FloatingLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        Resources res = context.getResources();
        int deleteZone = res.getDimensionPixelSize(R.dimen.workspace_delete_height);
        int seatHeight = res.getDimensionPixelSize(R.dimen.hotseat_height);
        mReatPadding = Math.round(res.getDisplayMetrics().density * 15);
        mRestIconHeight = Math.round(IconCustomizer.sCustomizedIconHeight * FLOATING_SCALE);
        mRestIconLp = new LinearLayout.LayoutParams(mRestIconHeight, mRestIconHeight);
        mRestIconLp.rightMargin = mReatPadding;
        mFolderIconLp = new LinearLayout.LayoutParams(mRestIconHeight, mRestIconHeight);
        int indicatorHeight = res.getDimensionPixelSize(R.dimen.indicator_height);

        mCellLayout = (CellLayout) inflate(context, R.layout.workspace_screen, null);
        mSwitchingCell = new int[mCellLayout.getCountX()][mCellLayout.getCountY()];
        // BUG#57644 Add by Fan.Yang
        int paddingLeft = res.getDimensionPixelSize(R.dimen.workspace_left_padding);
        int paddingRight = res.getDimensionPixelSize(R.dimen.workspace_right_padding);
        mCellLayout.setPadding(paddingLeft, deleteZone + 1, paddingRight, seatHeight + indicatorHeight);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        addView(mCellLayout, lp);

        scroll = new BounceHorizontalScrollView(context);
        mRestSeat = new ShadowLinearLayout(context, mRestIconHeight);
        mRestSeat.setPadding(mReatPadding, 0, 0, 0);
        mRestSeat.setGravity(Gravity.CENTER_VERTICAL);
        setTranslateLayoutTransition(mRestSeat, true);
        addFolderImage = new ImageView(context);
        addFolderImage.setImageDrawable(new BitmapDrawable(createFolderBitmap(context)));
        addFolderImage.setClickable(true);
        addFolderImage.setOnClickListener(mRestIconClickListener);
        addFolderImage.setTag(new String("addFolder"));
        mRestContainer = new ShadowLinearLayout(context, mRestIconHeight);
        mRestContainer.setPadding(mReatPadding, 0, 0, 0);
        mRestContainer.setGravity(Gravity.CENTER_VERTICAL);
        mRestContainer.addView(addFolderImage, 0, mFolderIconLp);
        mRestContainer.addView(mRestSeat, 1, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.addView(mRestContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundResource(R.drawable.floating_rest_bg);
        lp = new LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, seatHeight);
        lp.gravity = Gravity.BOTTOM;
        addView(scroll, lp);

        mLauncher = (Launcher) context;
        mCellLayout.setClipChildren(false);
        mCellLayout.setClipToPadding(false);
        mCellLayout.getShortcutsAndWidgets().setClipChildren(false);
        mCellLayout.getShortcutsAndWidgets().setClipToPadding(false);
        //setBackgroundColor(FLOATING_BG_COLOR);
        setAlpha(0);
    }

    private Bitmap createFolderBitmap(Context context) {
        Resources res = context.getResources();
        IconCache mIconCache = ((LauncherApplication) context.getApplicationContext()).getIconCache();
        Drawable bgDrawable = mIconCache.getIcon("icon_folder_background.png");
        if (bgDrawable == null) {
            bgDrawable = res.getDrawable(R.drawable.icon_folder_background);
        }
        Bitmap bg = ((BitmapDrawable) bgDrawable).getBitmap();
        Bitmap src = BitmapFactory.decodeResource(res, R.drawable.floating_new_folder);
        int width = bg.getWidth();
        int height = bg.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, bg.getConfig());
        Canvas canvas = new Canvas(bitmap);
        Rect rect = new Rect(0, 0, width, height);
        Matrix matrix = new Matrix();
        float scaleX = (float) bg.getWidth() / src.getWidth();
        float scaleY = (float) bg.getHeight() / src.getHeight();
        matrix.postScale(scaleX,scaleY);
        Bitmap scaledSrc = Bitmap
                .createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        canvas.drawBitmap(bg, rect, rect, null);
        canvas.drawBitmap(scaledSrc, 0, 0, null);
        bg = null;
        src.recycle();
        scaledSrc.recycle();
        return bitmap;
    }

    private void setTranslateLayoutTransition(ViewGroup v, boolean enable) {
        if (enable) {
            LayoutTransition trans = new LayoutTransition();
            Animator animator = ObjectAnimator.ofPropertyValuesHolder(
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -mRestIconHeight, 0));
            animator.setDuration(50);
            trans.setAnimator(LayoutTransition.APPEARING, animator);
            mRestSeat.setLayoutTransition(trans);
        } else {
            mRestSeat.setLayoutTransition(null);
        }
    }

    void startFloating(CellLayout.CellInfo cellInfo) {
        if (cellInfo == null || !mLauncher.getEditModeLayer().isFromEditEnterFloating() &&
                (!allowFloat(cellInfo.cell) || !LauncherModel.isItemInfoSynced((ItemInfo) (cellInfo.cell.getTag())))) {
            return;
        }
        if (mStartEndAnimator != null && mStartEndAnimator.isRunning()) {
            mStartEndAnimator.cancel();
            return;
        }
        mFloating = true;
        mWorkspace = mLauncher.getWorkspace();
        // Begin, added by zhumeiquan, req change , add new screen while entering floating
        //mWorkspace.addScreen(mWorkspace.getChildCount());
        mWorkspace.refreshFolderIconHat(true);
        mLauncher.getDeleteZone().clearAnimation();
        BlurWallpaperManager.getInstance(mLauncher).setBlur(true);
        //mWorkspace.blurWallpaper(true);
        mWorkspace.setPageSwitchListener(this);
        mLauncher.showStatusBar(false);
        mWorkspaceCellLayout = (CellLayout) mWorkspace.getChildAt(mWorkspace
                .getCurrentPage());
        mStartPage = cellInfo.screen;
        addCell(cellInfo);
        if (cellInfo.cell != null) {
            ObjectAnimator.ofPropertyValuesHolder(cellInfo.cell,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, FLOATING_SCALE, 1),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, FLOATING_SCALE, 1))
                    .setDuration(ANIM_TOGGLE_DURATION)
                    .start();
        }
        setVisibility(View.VISIBLE);
        mDragController.addDropTarget(mRestSeat);
        mDragController.addDropTarget(mLauncher.getDeleteZone());
        mLastPageIndex = mLauncher.getCurrentWorkspaceScreen();
        mStartEndAnimator = new AnimatorSet();
        Hotseat hotseat = mLauncher.getHotseat();
        if (mLauncher.isEditMode()) {
            mLauncher.getEditModeLayer().setEditLayerVisible(false);
            mStartEndAnimator.playTogether(ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1),
                    ObjectAnimator.ofFloat(mWorkspace, "blurredWallpaperAlpha", 1));
        } else {
            // add screen auto
            mWorkspace.addEmptyScreenAtLast();
            batchScaleCell(true, true, mStartPage);
            mStartEndAnimator.playTogether(ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1),
                    ObjectAnimator.ofFloat(mWorkspace, "blurredWallpaperAlpha", 1),
                    ObjectAnimator.ofFloat(hotseat, View.TRANSLATION_Y, 0, hotseat.getHeight() * 3));
        }
        mStartEndAnimator.setDuration(ANIM_SWITCHING_DURATION);
        mStartEndAnimator.start();
        mStartEndAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mWorkspace.scrollFolderPreview(null, false);
            }
        });
        mRestSeat.removeAllViews();
        mRestSeat.isChildrenShrinked = false;
        EditModeUtils.setInEditMode(mLauncher,isFloating());
    }

    void toggleCell(CellLayout.CellInfo cellInfo) {
        removeCallbacks(mAddFolderRunnable);
        removeCallbacks(mCellLayoutLongClickRunnable);
        if (isSwitching() || mWorkspace.isPageMoving()) {
            return;
        }
        boolean isFloatingCell = isFloatingCell(cellInfo.cell);
        if (isRanging()) {
            if (isFloatingCell) {
                mPendingCell = cellInfo;
            }
            return;
        }
        if (isFloatingCell) {
            int screen = mLauncher.getCurrentWorkspaceScreen();
            CellLayout layout = mLauncher.getCellLayout(cellInfo.container, screen);
            if (layout.getChildAt(cellInfo.cellX, cellInfo.cellY) == null && addCell(layout, cellInfo)) {
                cellInfo.screen = screen;
                final ItemInfo info = (ItemInfo) cellInfo.cell.getTag();
                LauncherModel.moveItemInDatabase(mLauncher, info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, cellInfo.cellX,
                        cellInfo.cellY);
                setChecked(cellInfo.cell, false);
                cellInfo.cell.setScaleX(1);
                cellInfo.cell.setScaleY(1);
                cellInfo.cell.animate()
                        .scaleX(FLOATING_SCALE)
                        .scaleY(FLOATING_SCALE)
                        .setDuration(ANIM_TOGGLE_DURATION)
                        .setInterpolator(mSwitchInterpolator)
                        .start();
                checkEmpty();
            }
        } else {
            addCell(cellInfo);
        }
    }

    private boolean isFloatingCell(View v) {
        if (v != null) {
            ViewParent p = v.getParent();
            if (p != null) {
                p = p.getParent();
            }
            if (p != null && p == mCellLayout) {
                return true;
            }
        }
        return false;
    }

    ArrayList<View> getAllItems() {
        ArrayList<View> items = new ArrayList<View>();
        ViewGroup vg = mCellLayout.getShortcutsAndWidgets();
        for (int i = 0, N = vg.getChildCount(); i < N; i++) {
            items.add(vg.getChildAt(i));
        }
        vg = mRestSeat;
        for (int i = 0, N = vg.getChildCount(); i < N; i++) {
            items.add((View) vg.getChildAt(i).getTag());
        }
        return items;
    }

    Rect getRestSeatRect() {
        if (mRestSeatRect == null) {
            mRestSeatRect = new Rect();
            int height = getHeight();
            int width = getWidth();
            int top = scroll.getLayoutParams().height;
            mRestSeatRect.set(0, height - top, width, height);
        }
        return mRestSeatRect;
    }

    private void batchScaleCell(boolean start, boolean anim, int animScreen) {
        for (int i = 0, N = mWorkspace.getChildCount(); i < N; i++) {
            if (start) {
                batchSetCellScale(i, 1, FLOATING_SCALE, anim && i == animScreen);
            } else {
                batchSetCellScale(i, FLOATING_SCALE, 1, anim && i == animScreen);
            }
        }
    }

    private void batchSetCellScale(int screen, float scaleStart, float scaleEnd, boolean anim) {
        CellLayout layout = mLauncher.getCellLayout(0, screen);
        ViewGroup group = layout.getShortcutsAndWidgets();
        ArrayList<Animator> anims = new ArrayList<Animator>();
        PropertyValuesHolder[] pvs = null;
        if (anim) {
            pvs = new PropertyValuesHolder[]{
                    PropertyValuesHolder.ofFloat(View.SCALE_X, scaleStart, scaleEnd),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleStart, scaleEnd)
            };
        }
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View v = group.getChildAt(i);
            if (allowFloat(v)) {
                if (anim) {
                    ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, pvs);
                    a.setInterpolator(mSwitchInterpolator);
                    anims.add(a);
                } else {
                    v.setScaleX(scaleEnd);
                    v.setScaleY(scaleEnd);
                }
            }
        }
        if (anims.size() > 0) {
            AnimatorSet set = new AnimatorSet();
            set.setDuration(ANIM_SWITCHING_DURATION);
            set.playTogether(anims);
            set.start();
            mSwitchingAnimator = set;
        }
    }

    private void checkEmpty() {
        if (isEmpty()) {
            endFloating(true, true);
        }
    }

    private boolean isFull() {
        return mCellLayout.getShortcutsAndWidgets().getChildCount()
                == mCellLayout.getCountX() * mCellLayout.getCountY();
    }

    private boolean isEmpty() {
        return mRestSeat.getChildCount() == 0
                && mCellLayout.getShortcutsAndWidgets().getChildCount() == 0;
    }

    boolean addCell(CellLayout.CellInfo cellInfo) {
        return addCell(mCellLayout, cellInfo);
    }

    public static boolean allowFloat(View v) {
        return v instanceof ShortcutIcon || v instanceof FolderIcon || v instanceof BubbleTextView;
    }

    public boolean isEmptyCell(View v) {
        return !allowFloat(v) && !mDragController.isDragging();
    }

    public boolean isFloatingCellLayout(View v) {
        return v == mCellLayout;
    }

    private static void setChecked(View v, boolean checked) {
        if (v instanceof ShortcutIcon) {
            ((ShortcutIcon) v).setChecked(checked);
        } else if (v instanceof FolderIcon) {
            ((FolderIcon) v).setChecked(checked);
        }
    }

    boolean addCell(CellLayout layout, CellLayout.CellInfo cellInfo) {
        View v = cellInfo.cell;
        if (!allowFloat(v)) {
            return false;
        }
        //lqwang-PR61640-modify begin
        if(layout == mCellLayout){
            CellLayout origin_parent = (CellLayout) mWorkspace.getChildAt(cellInfo.screen);
            //zwsun@lewatek.com PR69228 20150205 start
            if(origin_parent != null)
            {
            origin_parent.markCellsAsUnoccupiedForView(v);
            }
            //zwsun@lewatek.com PR69228 20150205 end
        }
        //lqwang-PR61640-modify end
        unbindParent(v);
        if (layout == mCellLayout) {
            setChecked(v, true);
            ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f, FLOATING_SCALE, 1),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f, FLOATING_SCALE, 1))
                    .setDuration(ANIM_TOGGLE_DURATION)
                    .start();
            v.animate().scaleX(1).scaleY(1).setInterpolator(mSwitchInterpolator)
                    .setDuration(ANIM_TOGGLE_DURATION).start();
        }
        // Start added by weihong , #59815
        if (mOldFloatingCells.contains(cellInfo)){
            mOldFloatingCells.remove(cellInfo);
        } else {
            mOldFloatingCells.add(cellInfo);
        }
        // End
        return layout.addViewToCellLayout(v, 0, v.getId(),
                (CellLayout.LayoutParams) v.getLayoutParams(), true);
    }

    synchronized void endFloating(boolean save, boolean animate) {
        removeCallbacks(mAddFolderRunnable);
        ArrayList<View> out = new ArrayList<View>();
        if (mCellLayout.isItemPlacementDirty() && mLastDragInfo != null
                && mLastDragInfo.cell != null) {
            mLastDragInfo.cell.setVisibility(View.VISIBLE);
            mDragController.cancelDrag();
            mLastDragInfo = null;
        }
        mCellLayout.revertTempState();
        if (mDragController.isDragging()) {
            if (mDragInfo != null) {
                View v = mDragInfo.cell;
                mDragInfo = null;
                mDragController.cancelDrag();
                mCellLayout.removeView(v);
                v.setVisibility(View.VISIBLE);
                addImageIcon(v);
            }
        }
        mFloating = false;
        isDragFloatingCell = false;
        if (mRestSeatSwitchingAnimator != null && mRestSeatSwitchingAnimator.isRunning()) {
            mRestSeatSwitchingAnimator.cancel();
        }
        if (isSwitching()) {
            mSwitchingAnimator.cancel();
        }
        if (isRanging()) {
            mRangingAnimator.cancel();
        }
        restoredFolder.clear();
        if (mOpeningFolder != null) {
            mLauncher.closeFolder();
        }
        mOldFloatingCells.clear();

        BlurWallpaperManager.getInstance(mLauncher).setBlur(false);
        mWorkspace.invalidate();
        final int screen = mLauncher.getCurrentWorkspaceScreen();
        CellLayout main = mLauncher.getCellLayout(0, screen);
        for (int x = mCellLayout.getCountX(); x >= 0; x--) {
            for (int y = mCellLayout.getCountY(); y >= 0; y--) {
                View v = mCellLayout.getChildAt(x, y);
                if (v != null) {
                    v.setVisibility(View.VISIBLE);
                    mCellLayout.removeView(v);
                    if (v.getTag() instanceof ItemInfo) {
                        if (save && main.getChildAt(x, y) == null) {
                            ItemInfo info = (ItemInfo) v.getTag();
                            ItemInfo syncedInfo = LauncherModel.getSyncedItemInfo(info);
                            if (info != syncedInfo && syncedInfo != null) {
                                info = syncedInfo;
                            }
                            if (v.getId() == -1) {
                                int newId = LauncherModel.getCellLayoutChildId(-100, screen, x, y, 1, 1);
                                v.setId(newId);
                            }
                            main.addViewToCellLayout(v, 0, v.getId(), (CellLayout.LayoutParams) v.getLayoutParams(), true);
                            LauncherModel.moveItemInDatabase(mLauncher, info, -100, screen, x, y);
                            setChecked(v, false);
                        } else {
                            out.add(v);
                        }
                    }
                }
            }
        }
        for (int i = mRestSeat.getChildCount() - 1; i >= 0; i--) {
            View v = mRestSeat.getChildAt(i);
            v.setVisibility(View.INVISIBLE);
            out.add((View) v.getTag());
        }
        mRestSeat.clearAnimation();
        mRestSeat.removeAllViews();
        mRestSeat.clearDisappearingChildren();
        // try put cells back to their original place
        ArrayList<View> tmp = new ArrayList<View>(out);
        for (View v : tmp) {
            ItemInfo info = (ItemInfo) v.getTag();
            setChecked(v, false);
            v.setX(0);
            v.setY(0);
            v.setTranslationX(0);
            v.setTranslationY(0);
            if (info != null) {
                CellLayout parent = mLauncher.getCellLayout(0, info.screen);
                FolderInfo folder = mLauncher.getModel().sBgFolders.get(info.container);
                if (parent != null && info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                        && parent.getChildAt(info.cellX, info.cellY) == null) {
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
                    lp.cellX = lp.tmpCellX = info.cellX;
                    lp.cellY = lp.tmpCellY = info.cellY;
                    parent.addViewToCellLayout(v, 0, v.getId(), lp, true);
                    out.remove(v);
                } else if (folder != null && info.container > 0
                        && v.getTag() instanceof ShortcutInfo) {
                    int index = info.screen * FolderScreen.CNT_PER_SCREEN + info.cellY
                            * FolderScreen.ITEMS_PER_ROW + info.cellX;
                    int size = folder.contents.size();
                    folder.add((ShortcutInfo) v.getTag(), index < size ? index
                            : (size - 1 > 0 ? size - 1 : 0), false);
                    out.remove(v);
                }
            }
        }

        // move duplicated and rest icons to end of the last page
        int lastScreen = mWorkspace.getChildCount() - 2; // we already added one screen in startFloating(), so here minus 2
        int[] xy = new int[2];
        findLastCell(xy, (CellLayout) mWorkspace.getChildAt(lastScreen));
        autoFillCellLayout(xy[0], xy[1], lastScreen, out);
        // move duplicated and rest icons to new page
        while (out.size() > 0) {
            if (mWorkspace.getChildAt(lastScreen + 1) == null) {    // already added ?
                mWorkspace.addScreen(lastScreen + 1);
            }
            autoFillCellLayout(0, 0, lastScreen, out);
            lastScreen++;
        }
        mDragController.removeDropTarget(mRestSeat);
        mWorkspace.removeAllEmptyFolder();
        int animScreen;
        if (!save) {
            mWorkspace.snapToPage(mStartPage);
            animScreen = mStartPage;
        } else {
            animScreen = mLauncher.getCurrentWorkspaceScreen();
        }
        if (mStartEndAnimator != null && mStartEndAnimator.isRunning())
            mStartEndAnimator.cancel();
        if (animate) {
            mStartEndAnimator = new AnimatorSet();
            Animator alpha = ObjectAnimator.ofFloat(this, View.ALPHA, 1, 0);
            Animator workspace = ObjectAnimator.ofFloat(mWorkspace, "blurredWallpaperAlpha", 0);
            Animator hotseat = ObjectAnimator.ofFloat(mLauncher.getHotseat(), View.TRANSLATION_Y, 0);
            if (mLauncher.isEditMode()) {
                mStartEndAnimator.playTogether(alpha, workspace);
                mLauncher.getEditModeLayer().setEditLayerVisible(true);
            } else {
                mWorkspace.refreshFolderIconHat(false);
                mLauncher.showStatusBar(true);
                batchScaleCell(false, true, animScreen);
                mStartEndAnimator.playTogether(alpha, workspace, hotseat);
            }
            mStartEndAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                    mWorkspace.setIsDropEnabled(true);
                    mWorkspace.scrollFolderPreview(null, true);
                    if (!mLauncher.isEditMode()) {
                        removeLastEmptyScreen();
                    }
                }
            });
            mStartEndAnimator.setDuration(ANIM_SWITCHING_DURATION);
            mStartEndAnimator.start();
        } else {
            removeLastEmptyScreen();
            setVisibility(View.GONE);
            mWorkspace.refreshFolderIconHat(false);
            mWorkspace.setIsDropEnabled(true);
            mWorkspace.scrollFolderPreview(null, true);
            mLauncher.getHotseat().setTranslationY(0);
            batchScaleCell(false, false, animScreen);
            mLauncher.getEditModeLayer().endEditMode();
            mLauncher.showStatusBar(true);
        }
        EditModeUtils.setInEditMode(mLauncher,isFloating());
    }

    private boolean saveSingleCell(int x, int y) {
        int screen = mLauncher.getCurrentWorkspaceScreen();
        CellLayout floating = mCellLayout;
        CellLayout main = mLauncher.getCellLayout(0, screen);
        View v = floating.getChildAt(x, y);
        if (v != null && main.getChildAt(x, y) == null) {
            ItemInfo info = (ItemInfo) v.getTag();
            if (info != null) {
                floating.removeView(v);
                main.addViewToCellLayout(v, 0, v.getId(), (CellLayout.LayoutParams) v.getLayoutParams(), true);
                ItemInfo syncedInfo = LauncherModel.getSyncedItemInfo(info);
                if (info != syncedInfo && syncedInfo != null) {
                    info = syncedInfo;
                }
                LauncherModel.moveItemInDatabase(mLauncher, info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, x, y);
                setChecked(v, false);
                mRestSeatSwitchingAnimator = new AnimatorSet();
                mRestSeatSwitchingAnimator.playTogether(ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE)));
                mRestSeatSwitchingAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        autoFixDuplicated(null, -1, -1, 0, 0, null);
                        if (mFloating) {
                            checkEmpty();
                        }
                    }
                });
                mRestSeatSwitchingAnimator.start();
            }
            return true;
        }
        return false;
    }

    private void findLastCell(int[] xy, CellLayout layout) {
        for (int y = layout.getCountY() - 1; y >= 0; y--) {
            for (int x = layout.getCountX() - 1; x >= 0; x--) {
                if (layout.getChildAt(x, y) != null) {
                    xy[0] = x;
                    xy[1] = y;
                    return;
                }
            }
        }
        xy[0] = 0;
        xy[1] = 0;
    }

    private void findEmptyCell(int[] xy, CellLayout layout) {
        for (int y = 0; y < layout.getCountY(); y++) {
            for (int x = 0; x < layout.getCountX(); x++) {
                if (layout.getChildAt(x, y) == null && mCellLayout.getChildAt(x, y) == null) {
                    xy[0] = x;
                    xy[1] = y;
                    return;
                }
            }
        }
        xy[0] = -1;
        xy[1] = -1;
    }

    private void autoFillCellLayout(int startX, int startY, int screen, ArrayList<View> views) {
        CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);
        for (int y = startY, YN = layout.getCountY(); y < YN; y++) {
            for (int x = y == startY ? startX : 0, XN = layout.getCountX(); x < XN; x++) {
                if (layout.getChildAt(x, y) != null) {
                    continue;
                }
                if (views.size() > 0) {
                    View v = views.get(views.size() - 1);
                    unbindParent(v);
                    ItemInfo itemInfo = (ItemInfo) v.getTag();
                    if (itemInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        restoreToScreen(layout, screen, x, y, v);
                    } else if (itemInfo.container > 0) {
                        restoreToFolder(layout, screen, x, y, v);
                    }
                    views.remove(v);
                } else {
                    return;
                }
            }
        }
    }

    private void restoreToScreen(CellLayout layout, int screen, int x, int y, View v) {
        ItemInfo info = (ItemInfo) v.getTag();
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
        lp.cellX = lp.tmpCellX = x;
        lp.cellY = lp.tmpCellY = y;
        lp.cellHSpan = info.spanX;
        lp.cellVSpan = info.spanY;
        lp.isLockedToGrid = true;
        layout.addViewToCellLayout(v, 0, v.getId(), lp, true);
        lp.x = x * layout.getCellWidth();
        lp.y = y * layout.getCellHeight();
        info.cellX = x;
        info.cellY = y;
        info.screen = screen;
        ItemInfo syncedInfo = LauncherModel.getSyncedItemInfo(info);
        if (syncedInfo != info && syncedInfo != null) {
            info = syncedInfo;
        }
        LauncherModel.moveItemInDatabase(mLauncher, info, info.container, screen, x, y);
    }

    // restore a new folder in screen which all items are dismissed in floating
    private void restoreToFolder(CellLayout layout, int screen, int cellX, int cellY, View shortcut) {
        ShortcutInfo shortcutInfo = (ShortcutInfo) shortcut.getTag();
        long restoredId;
        FolderInfo restoredInfo;
        if (restoredFolder.containsKey(shortcutInfo.container)) {
            restoredId = restoredFolder.get(shortcutInfo.container);
            restoredInfo = mLauncher.getModel().sBgFolders.get(restoredId);
        } else {
            // must return new folder id
            restoredInfo = mLauncher.addFolder(layout, screen, cellX, cellY);
            restoredId = restoredInfo.id;
            restoredFolder.put(shortcutInfo.container, restoredId);
            shortcutInfo.container = restoredId;
        }
        restoredInfo.add(shortcutInfo);
    }

    private static final float DENSITY = Resources.getSystem().getDisplayMetrics().density;
    private static final int MOVE_THRESHOLD = Math.round(DENSITY * 5);
    public static final int ENTER_MOVE_THRESHOLD = Math.round(DENSITY * 15);
    private static final int LONGPRESS_TIMEOUT = 150;
    private float mLastX, mLastY;
    private boolean mTouchCanceled;
    private int mRestTop;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mRestTop = ((ViewGroup) mRestContainer.getParent()).getTop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        if (y > mRestTop) {
            return super.dispatchTouchEvent(ev);
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = x;
                mLastY = y;
                mTouchCanceled = false;
                postDelayed(mCellLayoutLongClickRunnable, LONGPRESS_TIMEOUT);
                MotionEvent mv = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
                mWorkspace.dispatchTouchEvent(mv);
                mv.recycle();
                return super.dispatchTouchEvent(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mCellLayoutLongClickRunnable);
                if (mDragController.isDragging()) {
                    return true;
                }
                if (Math.abs(mLastX - x) < MOVE_THRESHOLD) {
                    CellLayout layout = mLauncher.getCellLayout(0, mLauncher.getCurrentWorkspaceScreen());
                    mTargetCell = findNearestArea((int) x, (int) y, 1, 1, mCellLayout, mTargetCell);
                    View v = layout.getChildAt(mTargetCell[0], mTargetCell[1]);
                    if (v != null && !allowFloat(v)) {
                        mv = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, x, y, 0);
                        mWorkspace.dispatchTouchEvent(mv);
                        super.dispatchTouchEvent(mv);
                        mv.recycle();
                    } else {
                        mv = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, x, y, 0);
                        super.dispatchTouchEvent(mv);
                        mWorkspace.dispatchTouchEvent(mv);
                        mv.setAction(MotionEvent.ACTION_DOWN);
                        super.dispatchTouchEvent(mv);
                        mWorkspace.dispatchTouchEvent(mv);
                        mv.setAction(MotionEvent.ACTION_UP);
                        super.dispatchTouchEvent(mv);
                        mWorkspace.dispatchTouchEvent(mv);
                        mv.recycle();
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mTouchCanceled && ev.getEventTime() - ev.getDownTime() < LONGPRESS_TIMEOUT
                        && Math.abs(mLastX - x) > MOVE_THRESHOLD) {
                    mTouchCanceled = true;
                    removeCallbacks(mCellLayoutLongClickRunnable);
                    mv = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, x, y, 0);
                    super.dispatchTouchEvent(mv);
                    mv.recycle();
                    return true;
                }
                break;

            default:
                break;
        }
        return mWorkspace.dispatchTouchEvent(ev);
    }

    private Runnable mCellLayoutLongClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDragController.isDragging() && mWorkspace.isPageMoving()) {
                return;
            }
            CellLayout layout = mCellLayout;
            mTargetCell = findNearestArea((int) mLastX, (int) mLastY, 1, 1, layout, mTargetCell);
            if (mTargetCell[0] >= 0 && mTargetCell[1] >= 0 && !mTouchCanceled) {
                View v = layout.getChildAt(mTargetCell[0], mTargetCell[1]);
                if (v != null && allowFloat(v) && !isRanging() && !isSwitching()) {
                    v.performLongClick();
                }
            }
        }
    };

    private void clearLayoutAnimation(ViewGroup v) {
        v.clearAnimation();
        LayoutTransition trans = v.getLayoutTransition();
        if (trans != null) {
            try {
                LayoutTransition.class.getMethod("endChangingAnimations").invoke(trans);
            } catch (Exception e) {
            }
        }
    }

    private void autoFixDuplicated(final View cell, int cellX, int cellY, float x, float y,
                                   final DragObject d) {
        if (isRanging()) {
            mRangingAnimator.cancel();
        }
        clearLayoutAnimation(mRestSeat);
        CellLayout main = mLauncher.getCellLayout(0, mLauncher.getCurrentWorkspaceScreen());
        CellLayout floating = mCellLayout;

        setTranslateLayoutTransition(mRestSeat, false);
        ArrayList<Animator> animators = new ArrayList<Animator>();

        View mv = main.getChildAt(cellX, cellY);
        if (mv != null) {
            if (mv instanceof FolderIcon && !(cell instanceof FolderIcon)) {
                ((FolderIcon) mv).onDrop(d);
                cell.animate().scaleX(0.5f).scaleY(0.5f).alpha(0)
                        .setInterpolator(mRestInInterpolator)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                unbindParent(cell);
                                checkEmpty();
                            }
                        });
            } else {
                cell.setTranslationX(0);
                cell.setTranslationY(0);
                cell.setX(x - 10 * DENSITY - mCellLayout.getCellWidth());
                cell.setY(y);
                autoRangeSingleCell(null, cell, animators, 0);
            }
        }

        for (int cy = floating.getCountY() - 1; cy >= 0; cy--) {
            for (int cx = floating.getCountX() - 1; cx >= 0; cx--) {
                View v = floating.getChildAt(cx, cy);
                if (v != null && v != cell && main.getChildAt(cx, cy) != null) {
                    autoRangeSingleCell(null, v, animators, 0);
                }
            }
        }
        if (animators.size() > 0) {
            mRangingAnimator = new AnimatorSet();
            mRangingAnimator.setDuration(ANIM_RANGING_DURATION);
            mRangingAnimator.playTogether(animators);
            mRangingAnimator.start();
        }
        mRestSeat.requestLayout();
    }

    private void autoRangeSingleCell(ArrayList<int[]> empties, final View v,
                                     ArrayList<Animator> animators, int delay) {
        autoRangeSingleCell(empties, v, animators, delay, true, false, false);
    }

    private void autoRangeSingleCell(ArrayList<int[]> empties, final View v,
                                     ArrayList<Animator> animators, int delay, boolean restInAnim) {
        autoRangeSingleCell(empties, v, animators, delay, restInAnim, false, false);
    }

    private void autoRangeSingleCell(ArrayList<int[]> empties, final View v,
                                     ArrayList<Animator> animators, int delay, final boolean restInAnim, final boolean save,
                                     final boolean check) {
        Animator animator = null;
        final boolean release = v.getParent() == mRestSeat;
        final View cell = release ? (View) v.getTag() : v;
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
        if (empties != null && empties.size() > 0) {
            int[] xy = empties.get(0);
            empties.remove(0);
            final int x = xy[0];
            final int y = xy[1];
            final int offsetX = x * mCellLayout.getCellWidth();
            final int offsetY = y * mCellLayout.getCellHeight();
            if (release) {
                int left = mRestSeat.getLeft();
                float vx = save ? v.getLeft() : (left + DENSITY * 10);
                float vy = mCellLayout.getBottom() - 120 * DENSITY;
                lp.cellX = lp.tmpCellX = mCellLayout.getCountX();
                lp.cellY = lp.tmpCellY = mCellLayout.getCountY();
                unbindParent(cell);
                mCellLayout.addViewToCellLayoutTemp(cell, 0, lp);
                if (!save) {
                    animator = ObjectAnimator.ofFloat(v, View.X, DENSITY * 15);
                    animator.setStartDelay(delay);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            v.setVisibility(View.GONE);
                            mRestSeat.removeView(v);
                        }
                    });
                    animators.add(animator);
                } else {
                    v.setVisibility(View.GONE);
                    mRestSeat.removeView(v);
                }
                animator = ObjectAnimator.ofPropertyValuesHolder(cell,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE * 0.6f, 1),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE * 0.6f, 1),
                        PropertyValuesHolder.ofFloat(View.X, vx, offsetX),
                        PropertyValuesHolder.ofFloat(View.Y, vy, offsetY));
                animator.setStartDelay(delay);
            } else {
                animator = ObjectAnimator.ofPropertyValuesHolder(cell,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1),
                        PropertyValuesHolder.ofFloat(View.X, offsetX),
                        PropertyValuesHolder.ofFloat(View.Y, offsetY));
            }
            animator.setInterpolator(mRestOutInterpolator);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    cell.setX(0);
                    cell.setY(0);
                    cell.setTranslationX(0);
                    cell.setTranslationY(0);
                    unbindParent(cell);
                    lp.cellX = lp.tmpCellX = x;
                    lp.cellY = lp.tmpCellY = y;
                    cell.setOnClickListener(mLauncher);
                    mCellLayout.addViewToCellLayout(cell, 0, cell.getId(), lp, true);
                    mSwitchingCell[x][y] = 0;
                    if (save) {
                        saveSingleCell(x, y);
                        mRestSeat.clearDisappearingChildren();
                    }
                    if (check) {
                        autoFixDuplicated(null, -1, -1, 0, 0, null);
                    }
                    cell.setScaleX(1);
                    cell.setScaleY(1);
                }
            });
            animators.add(animator);
        } else {
            final float top = mCellLayout.getBottom() - 130 * DENSITY;
            final float left = mOpeningFolder != null ? -DENSITY * 5 : mRestSeat.getLeft();
            animator = ObjectAnimator.ofPropertyValuesHolder(cell,
                    PropertyValuesHolder.ofFloat(View.X, left),
                    PropertyValuesHolder.ofFloat(View.Y, top),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE * 0.6f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE * 0.6f));
            if (restInAnim) {
                animator.setStartDelay(delay);
            }
            animator.setInterpolator(mRestInInterpolator);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    addImageIcon(cell);
                    mCellLayout.removeView(cell);
                }
            });
            animators.add(animator);
        }
    }

    private static void unbindParent(View v) {
        ViewGroup vg = (ViewGroup) v.getParent();
        if (vg != null) {
            vg.removeView(v);
        }
    }

    public boolean isRanging() {
        return mRangingAnimator != null && mRangingAnimator.isRunning();
    }

    private boolean isSwitching() {
        return mSwitchingAnimator != null && mSwitchingAnimator.isRunning();
    }

    public boolean isStartEnd() {
        return mStartEndAnimator != null && mStartEndAnimator.isRunning();
    }

    public synchronized void autoRangeCell(int newPageIndex) {
        if (mOpeningFolder != null || !isFloating()) {
            return;
        }
        removeCallbacks(mAddFolderRunnable);
        removeCallbacks(mCellLayoutLongClickRunnable);
        if (isRanging()) {
            mRangingAnimator.cancel();
        }
        mRestSeat.clearAnimation();
        mRestSeat.clearDisappearingChildren();
        mCellLayout.getShortcutsAndWidgets().clearDisappearingChildren();
        ArrayList<Animator> animators = new ArrayList<Animator>();
        final CellLayout layout = mLauncher.getCellLayout(0, newPageIndex);
        final CellLayout floating = mCellLayout;
        final ArrayList<int[]> empties = new ArrayList<int[]>();
        for (int y = 0, NY = layout.getCountY(); y < NY; y++) {
            for (int x = 0, NX = layout.getCountX(); x < NX; x++) {
                if (layout.getChildAt(x, y) == null) {
                    empties.add(new int[]{x, y});
                }
            }
        }
        // reorder floating cell
        final ArrayList<View> floated = new ArrayList<View>();
        for (int y = 0, NY = floating.getCountY(); y < NY; y++) {
            for (int x = 0, NX = floating.getCountX(); x < NX; x++) {
                View v = floating.getChildAt(x, y);
                if (v != null) {
                    Log.d(TAG, "autoRangCell,add float,x=" + x + ",y=" + y);
                    v.setVisibility(View.VISIBLE);
                    floated.add(v);
                }
            }
        }
        if (AUTO_FILL_WHEN_EQUAL && empties.size() != layout.getCountX() * layout.getCountY()
                && (floated.size() + mRestSeat.getChildCount() > empties.size())) {
            empties.clear();
        }
        int rest = floated.size() - empties.size();
        int restStart = 0;
        int order = 0;
        if (rest > ANIM_MAX_NUM_REST_MOVE) {
            restStart = rest - ANIM_MAX_NUM_REST_MOVE;
        }
        for (int i = 0, N = floated.size(); i < N; i++) {
            if (empties.size() > 0) {
                View v = floated.get(i);
                autoRangeSingleCell(empties, v, animators, 0);
            } else {
                for (i = N - i - 1; i >= 0; i--) {
                    View v = floated.get(i);
                    autoRangeSingleCell(empties, v, animators, 50 * (order >= restStart ? order
                            - restStart : 0), i <= ANIM_MAX_NUM_REST_IN);
                    order++;
                }
                break;
            }
        }
        // set rest seat layout animation
        setTranslateLayoutTransition(mRestSeat, order != 1);
        // release rest seat
        restStart = 0;
        int restNum = mRestSeat.getChildCount();
        rest = Math.min(empties.size(), restNum);
        if (rest > ANIM_MAX_NUM_REST_MOVE) {
            restStart = rest - ANIM_MAX_NUM_REST_MOVE;
        }
        for (int i = 0; i < restNum && empties.size() > 0; i++) {
            View v = mRestSeat.getChildAt(i);
            autoRangeSingleCell(empties, v, animators,
                    50 * (order >= restStart ? order - restStart : 0), i <= ANIM_MAX_NUM_REST_IN);
            order++;
        }
        if (animators.size() > 0) {
            mRangingAnimator = new AnimatorSet();
            mRangingAnimator.setDuration(ANIM_RANGING_DURATION);
            mRangingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mPendingCell != null) {
                        mRangingAnimator = null;
                        View cell = mPendingCell.cell;
                        if (cell != null && cell.getParent() != null) {
                            toggleCell(mPendingCell);
                        }
                        mPendingCell = null;
                    }
                    clearDisappearingChildren();
                    mCellLayout.getShortcutsAndWidgets().clearDisappearingChildren();
                }
            });
            mRangingAnimator.playTogether(animators);
            mRangingAnimator.start();
        }
    }

    int floatingFolderCount = 0;

    public void dropAllIntoRestSeat() {
        if (!mFloating) {
            return;
        }
        removeCallbacks(mAddFolderRunnable);
        removeCallbacks(mCellLayoutLongClickRunnable);
        if (isRanging()) {
            mRangingAnimator.cancel();
        }
        mRestSeat.clearAnimation();
        mRestSeat.clearDisappearingChildren();
        mCellLayout.getShortcutsAndWidgets().clearDisappearingChildren();
        ArrayList<Animator> animators = new ArrayList<Animator>();
        //final CellLayout layout = mLauncher.getCellLayout(0, newPageIndex);
        final CellLayout floating = mCellLayout;
        final ArrayList<int[]> empties = new ArrayList<int[]>();
        // reorder floating cell
        floatingFolderCount = 0;
        final ArrayList<View> floated = new ArrayList<View>();
        for (int y = 0, NY = floating.getCountY(); y < NY; y++) {
            for (int x = 0, NX = floating.getCountX(); x < NX; x++) {
                View v = floating.getChildAt(x, y);
                if (v != null) {
                    if (v instanceof FolderIcon) {
                        ///floated.add(floatingFolderCount, v);
                        floatingFolderCount++;
                        v.setVisibility(View.INVISIBLE);
                    } else if (v instanceof ShortcutIcon) {
                        floated.add(v);
                    }
                }
            }
        }
        int rest = floated.size() - empties.size();
        int restStart = 0;
        int order = 0;
        if (rest > ANIM_MAX_NUM_REST_MOVE) {
            restStart = rest - ANIM_MAX_NUM_REST_MOVE;
        }
        for (int i = 0, N = floated.size(); i < N; i++) {
            if (empties.size() > 0) {
                View v = floated.get(i);
                autoRangeSingleCell(empties, v, animators, 0);
            } else {
                for (i = N - i - 1; i >= 0; i--) {
                    View v = floated.get(i);
                    autoRangeSingleCell(empties, v, animators, 50 * (order >= restStart ? order
                            - restStart : 0), i <= ANIM_MAX_NUM_REST_IN);
                    order++;
                }
                break;
            }
        }
        // set rest seat layout animation
        setTranslateLayoutTransition(mRestSeat, order != 1);
        // release rest seat
        restStart = 0;
        int restNum = mRestSeat.getChildCount();
        rest = Math.min(empties.size(), restNum);
        if (rest > ANIM_MAX_NUM_REST_MOVE) {
            restStart = rest - ANIM_MAX_NUM_REST_MOVE;
        }
        for (int i = 0; i < restNum && empties.size() > 0; i++) {
            View v = mRestSeat.getChildAt(i);
            autoRangeSingleCell(empties, v, animators,
                    50 * (order >= restStart ? order - restStart : 0), i <= ANIM_MAX_NUM_REST_IN);
            order++;
        }
        if (animators.size() > 0) {
            mRangingAnimator = new AnimatorSet();
            mRangingAnimator.setDuration(ANIM_RANGING_DURATION);
            mRangingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mPendingCell != null) {
                        mRangingAnimator = null;
                        View cell = mPendingCell.cell;
                        if (cell != null && cell.getParent() != null) {
                            toggleCell(mPendingCell);
                        }
                        mPendingCell = null;
                    }
                    clearDisappearingChildren();
                    mCellLayout.getShortcutsAndWidgets().clearDisappearingChildren();
                    if (mRestSeat.isChildrenShrinked) {
                        shrinkIconsInRestSeat(false);
                    }
                }
            });
            mRangingAnimator.playTogether(animators);
            mRangingAnimator.start();
        }
    }

    private View addImageIcon(View v) {
        if (v == null) {
            return null;
        }
        ImageView img = new ImageView(getContext());
        img.setScaleType(ScaleType.FIT_XY);
        img.setAdjustViewBounds(true);
        img.setTag(v);
        if (v instanceof ShortcutIcon) {
            final ShortcutIcon icon = (ShortcutIcon) v;
            img.setImageDrawable(icon.getFavoriteCompoundDrawable());
        } else if (v instanceof FolderIcon) {
            final FolderIcon icon = (FolderIcon) v;
            img.setImageDrawable(icon.getSnapshot());
        }
        img.setClickable(true);
        img.setOnClickListener(mRestIconClickListener);
        img.setOnLongClickListener(mRestIconLongClickListener);
        mRestSeat.addView(img, 0, mRestIconLp);
        return img;
    }

    public boolean forbiddenOpenFolder() {
        return isStartEnd() || isRanging() || !isDropFinish;
    }

    public boolean forbiddenCloseFolder() {
        return mFloating && !mPostingCells.isEmpty();
    }

    private boolean isDropFinish = true;

    private void dropIntoRestSeat(final View v, final DragObject d) {
        mCellLayout.revertTempState();
        setChecked(v, true);
        final DragView dv = d.dragView;
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(dv,
                PropertyValuesHolder.ofFloat(View.X, (int) (-DENSITY * 5)),
                PropertyValuesHolder.ofFloat(View.Y, mRestTop),
                PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE));
        anim.setDuration(ANIM_TOGGLE_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (dv != null) {
                    unbindParent(dv);
                }
                clearLayoutAnimation(mRestSeat);
                setTranslateLayoutTransition(mRestSeat, mRestSeat.getChildCount() > 0);
                mCellLayout.removeView(v);
                addImageIcon(v);
                v.setVisibility(View.VISIBLE);
                if (mRestSeat.isChildrenShrinked && mOpeningFolder != null) {
                    shrinkIconsInRestSeat(true);
                } else if (mRestSeat.isChildrenShrinked && mOpeningFolder == null) {
                    shrinkIconsInRestSeat(false);
                }
                isDropFinish = true;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                isDropFinish = false;
                dv.cancelAnimation();
                mLauncher.getDragLayer().clearAnimatedView();
            }
        });
        anim.start();
    }

    private OnClickListener mRestIconClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isDropFinish) {
                return;
            }
            if (v.getTag() instanceof String) {
                addFolderInScreen(v);
                return;
            }
            if (mOpeningFolder != null && v.getTag() != null) {
                addShortcutInFolder(v);
                return;
            }
            if (mRestSeatSwitchingAnimator != null && mRestSeatSwitchingAnimator.isRunning()) {
                clearLayoutAnimation(mRestSeat);
                mRestSeatSwitchingAnimator.end();
            }
            final CellLayout main = mLauncher.getCellLayout(0,
                    mLauncher.getCurrentWorkspaceScreen());
            final CellLayout floating = mCellLayout;
            for (int y = 0, NY = main.getCountY(); y < NY; y++) {
                for (int x = 0, NX = main.getCountX(); x < NX; x++) {
                    if (main.getChildAt(x, y) == null && floating.getChildAt(x, y) == null
                            && mSwitchingCell[x][y] == 0) {
                        ArrayList<int[]> empties = new ArrayList<int[]>();
                        int[] cell = new int[]{x, y};
                        empties.add(cell);
                        mSwitchingCell[x][y] = 1;
                        ArrayList<Animator> animators = new ArrayList<Animator>();
                        autoRangeSingleCell(empties, v, animators, 0, true, true, true);
                        mRestSeatSwitchingAnimator = new AnimatorSet();
                        mRestSeatSwitchingAnimator.playTogether(animators);
                        mRestSeatSwitchingAnimator.start();
                        return;
                    }
                }
            }
        }
    };

    private OnLongClickListener mRestIconLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (isFull() || mRestSeat.isChildrenShrinked) {
                return true;
            }
            View c = (View) v.getTag();
            if (c != null) {
                ItemInfo item = (ItemInfo) c.getTag();
                if (item != null) {
                    CellInfo info = new CellInfo();
                    info.cell = c;
                    info.cellX = item.cellX;
                    info.cellY = item.cellY;
                    info.spanX = item.spanX;
                    info.spanY = item.spanY;
                    info.screen = item.screen;
                    int offset = -(int) (10 * DENSITY);
                    v.setTranslationX(offset);
                    v.setTranslationY(offset);
                    v.setScaleX(0.5f);
                    v.setScaleY(0.5f);
                    c.setTranslationX(0);
                    c.setTranslationY(0);
                    //lqwang-PR63020-modify begin
                    setTranslateLayoutTransition(mRestSeat, mRestSeat.getChildCount() > 1);
                    //lqwang-PR63020-modify end
                    unbindParent(c);
                    mCellLayout.getShortcutsAndWidgets().addView(c);
                    mDragView = v;
                    startDrag(info, false, false);
                    return true;
                }
            }
            return false;
        }
    };

    private View mDragView;
    private View mDragHomeView;

    boolean contains(CellLayout.CellInfo cellInfo) {
        return mCellLayout.getChildAt(cellInfo.cellX, cellInfo.cellY) != null;
    }

    boolean isFloating() {
        return mFloating;
    }

    private CellLayout.CellInfo mDragInfo, mLastDragInfo;
    public static final int DRAG_BITMAP_PADDING = 2;
    private final int[] mTempXY = new int[2];
    private final Rect mTempRect = new Rect();
    private DragController mDragController;

    void startDrag(CellLayout.CellInfo cellInfo, boolean dragFromHome) {
        startDrag(cellInfo, true, dragFromHome);
    }

    /*
    * begin add for mix floating dragging by fan.yang
    ********************************************************************************
    * beforeFloatingDrag和afterFloatingDropComplete方法为了处理浮动层和桌面层的混合拖动问题，
    * OS6上浮动层只是一个dragSource，当在浮动模式情况下，
    * 1.拖动一个已经浮动的图标，所有已经浮动的图标在before方法里下落到桌面层，
    * 在拖动完成后，在after方法把原来所有的浮动图标重新加入浮动层，并在worskpace的onDropEx方法中
    * 处理拖动的图标
    * 2.拖动一个桌面层的图标，所有浮动的图标下沉，拖动完成后重新浮起
    * 3.设计到Folder的拖动会稍复杂。
    * */
    private ArrayList<CellInfo> mFloatingCells = new ArrayList<CellInfo>();
    // added by weihong, #58816
    private ArrayList<CellInfo> mOldFloatingCells = new ArrayList<CellInfo>();
    public boolean isDragFloatingCell = false;
    private boolean dropTargetRemoved = false;

    void beforeFloatingDrag(CellLayout.CellInfo cellInfo) {
        if (!mFloating || cellInfo.cell == null) {
            return;
        }
        toggleAllFloatingIcon(cellInfo, true);
        //mDragController.removeDropTarget(this);
        dropTargetRemoved = true;
        mDragInfo = cellInfo;
    }

    void afterFloatingDropComplete() {
        if (!mFloating) {
            return;
        }
        toggleAllFloatingIcon(mDragInfo, false);
        if (mFloatingCells.size() > 0) {
            Log.e(TAG, "onDropCompleted error");
            throw new RuntimeException();
        }
        if (dropTargetRemoved) {
            //mDragController.addDropTarget(this);
            dropTargetRemoved = false;
        }
        //#61321,#61322 Add by Fan.Yang
        mDragInfo = null;
    }

    private void toggleAllFloatingIcon(CellLayout.CellInfo draggingInfo, boolean sink) {
        int screen = mLauncher.getCurrentWorkspaceScreen();
        CellLayout currentScreen = mLauncher.getCellLayout(0, screen);
        ShortcutAndWidgetContainer container = (ShortcutAndWidgetContainer) mCellLayout.getChildAt(0);
        for (int i = container.getChildCount() - 1; i >= 0 && sink; i--) {
            View child = container.getChildAt(i);
            final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child
                    .getLayoutParams();
            if (draggingInfo != null && !draggingInfo.cell.equals(child)) {
                CellInfo cellInfo = new CellInfo();
                mFloatingCells.add(cellInfo);
                cellInfo.cell = child;
                cellInfo.cellX = lp.cellX;
                cellInfo.cellY = lp.cellY;
                cellInfo.spanX = lp.cellHSpan;
                cellInfo.spanY = lp.cellVSpan;
                if (addCell(currentScreen, cellInfo)) {
                    cellInfo.screen = screen;
//                    final ItemInfo info = (ItemInfo) cellInfo.cell.getTag();
//                    LauncherModel.moveItemInDatabase(mLauncher, info,
//                            info.container, screen, cellInfo.cellX,
//                            cellInfo.cellY);
                }
            }
        }
        for (int i = mFloatingCells.size() - 1; i >= 0 && !sink; i--) {
            CellInfo cellInfo = mFloatingCells.get(i);
            View v = cellInfo.cell;
            boolean stillFloating = false;
            if (v.getParent() instanceof ShortcutAndWidgetContainer) {
                stillFloating = true;
            }

            if (stillFloating){
                //lqwang-PR61640-modify begin
                 CellLayout origin_parent = (CellLayout) mWorkspace.getChildAt(cellInfo.screen);
                 origin_parent.markCellsAsUnoccupiedForView(v);
                //lqwang-PR61640-modify end
                unbindParent(v);
                mCellLayout.addViewToCellLayout(v, 0, v.getId(),
                        (CellLayout.LayoutParams) v.getLayoutParams(), true);
            }
            mFloatingCells.remove(cellInfo);
        }
        if (sink){
            mOldFloatingCells.clear();
            mOldFloatingCells.addAll(mFloatingCells);
        }
    }

    public boolean isFloatedCell(View view) {
        if (!mFloating || view == null) {
            return false;
        }
        boolean result = false;
        for (CellInfo cell : mOldFloatingCells) {
            if (view.equals(cell.cell)) {
                result = true;
                break;
            }
        }
        return result;
    }

    void startDrag(CellLayout.CellInfo cellInfo, boolean check, boolean dragFromHome) {
        beforeFloatingDrag(cellInfo);
        // remove workspace touch event
        MotionEvent mv = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mWorkspace.dispatchTouchEvent(mv);
        mv.recycle();

        if (check && !contains(cellInfo)) {
            return;
        }
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (child == null || !child.isInTouchMode()) {
            return;
        }

        mLastDragInfo = mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);
        CellLayout layout = (CellLayout) child.getParent().getParent();
        layout.prepareChildForDrag(child);

        child.clearFocus();
        child.setPressed(false);
        beginDragShared(child, this);
        if (dragFromHome) {
            mDragHomeView = child;
        }
    }

    private void drawDragView(View v, Canvas destCanvas, int padding, boolean pruneToDrawable) {
        final Rect clipRect = mTempRect;
        v.getDrawingRect(clipRect);
        boolean textVisible = false;

        destCanvas.save();
        if (v instanceof TextView && pruneToDrawable) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            clipRect.set(0, 0, d.getIntrinsicWidth() + padding, d.getIntrinsicHeight() + padding);
            destCanvas.translate(padding / 2, padding / 2);
            d.draw(destCanvas);
        } else {
            if (v instanceof FolderIcon) {
                if (((FolderIcon) v).getTextVisible()) {
                    ((FolderIcon) v).setTextVisible(false);
                    textVisible = true;
                }
            } else if (v instanceof BubbleTextView) {
                final BubbleTextView tv = (BubbleTextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - (int) BubbleTextView.PADDING_V
                        + tv.getLayout().getLineTop(0);
            } else if (v instanceof TextView) {
                final TextView tv = (TextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - tv.getCompoundDrawablePadding()
                        + tv.getLayout().getLineTop(0);
            }
            destCanvas.translate(-v.getScrollX() + padding / 2, -v.getScrollY() + padding / 2);
            destCanvas.clipRect(clipRect, Op.REPLACE);
            v.draw(destCanvas);

            // Restore text visibility of FolderIcon if necessary
            if (textVisible) {
                ((FolderIcon) v).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    public Bitmap createDragBitmap(View v, Canvas canvas, int padding) {
        Bitmap b;
        if (v instanceof TextView) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            b = Bitmap.createBitmap(d.getIntrinsicWidth() + padding, d.getIntrinsicHeight()
                    + padding, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(v.getWidth() + padding, v.getHeight() + padding,
                    Bitmap.Config.ARGB_8888);
        }
        canvas.setBitmap(b);
        drawDragView(v, canvas, padding, true);
        canvas.setBitmap(null);
        return b;
    }

    public void beginDragShared(View child, DragSource source) {
        Resources r = getResources();
        final Bitmap b = createDragBitmap(child, new Canvas(), DRAG_BITMAP_PADDING);

        final int bmpWidth = b.getWidth();
        final int bmpHeight = b.getHeight();

        float scale;
        if (mDragView == null) {
            scale = mLauncher.getDragLayer().getLocationInDragLayer(child, mTempXY);
        } else {
            scale = mLauncher.getDragLayer().getLocationInDragLayer(mDragView, mTempXY);
            mDragView.clearAnimation();
            clearLayoutAnimation(mRestSeat);
            mRestSeat.removeView(mDragView);
            mRestSeat.clearDisappearingChildren();
        }
        int dragLayerX = Math.round(mTempXY[0] - (bmpWidth - scale * child.getWidth()) / 2);
        int dragLayerY = Math.round(mTempXY[1] - (bmpHeight - scale * bmpHeight) / 2
                - DRAG_BITMAP_PADDING / 2);

        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView) {
            int iconSize = IconCache.getAppIconSize(r);
            int iconPaddingTop = r.getDimensionPixelSize(R.dimen.app_icon_padding_top);
            int top = child.getPaddingTop();
            int left = (bmpWidth - iconSize) / 2;
            int right = left + iconSize;
            int bottom = top + iconSize;
            dragLayerY += top;
            dragVisualizeOffset = new Point(-DRAG_BITMAP_PADDING / 2, iconPaddingTop
                    - DRAG_BITMAP_PADDING / 2);
            dragRect = new Rect(left, top, right, bottom);
        } else if (child instanceof FolderIcon) {
            int previewSize = IconCache.getAppIconSize(r);
            dragRect = new Rect(0, 0, child.getWidth(), previewSize);
        }

        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedOrFocusedBackground();
        }

        mDragController.startDrag(b, dragLayerX, dragLayerY, source, child.getTag(),
                DragController.DRAG_ACTION_MOVE, dragVisualizeOffset, dragRect, scale);
        b.recycle();
    }

    void setup(DragController dragController) {
        mDragController = dragController;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete, boolean success) {
        if (isFloating() && mOpeningFolder != null && !isDropIntoRestSeat) {
            if (!(target instanceof FolderLayout)) {
                dropIntoRestSeat(mDragInfo.cell, d);
            }
        } else if (isFloating() && d.cancelled){
            d.deferDragViewCleanupPostAnimation = true;
            dropIntoRestSeat(mDragInfo.cell, d);
        }
        isDropIntoRestSeat = false;
        //yixiao modify #70617 2015.3.2
        if (mDragInfo != null && !(target instanceof ShadowLinearLayout)) {
            unbindParent(mDragInfo.cell);
        }
        // add for mix floating dragging by fan.yang
        afterFloatingDropComplete();

        mDragView = null;
    }

    //@Override
    public boolean isDropEnabled() {
        return true;
    }

    private float[] mDragViewVisualCenter = new float[2];

    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
                                            DragView dragView, float[] recycle) {
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    private int[] mTargetCell = new int[2];
    private Matrix mTempInverseMatrix = new Matrix();

    void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
        if (cachedInverseMatrix == null) {
            v.getMatrix().invert(mTempInverseMatrix);
            cachedInverseMatrix = mTempInverseMatrix;
        }
        xy[0] = xy[0] - v.getLeft();
        xy[1] = xy[1] + getScrollY() - v.getTop();
        cachedInverseMatrix.mapPoints(xy);
    }

    private int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY, CellLayout layout,
                                  int[] recycle) {
        return layout.findNearestArea(pixelX, pixelY, spanX, spanY, recycle);
    }
    // added by weihong, #56790, delete FloatingLayer DropTarget 20140915
    // Start
/*
    public void onDrop(final DragObject d) {
        if (d.y > mRestTop && mDragInfo != null) {
//            dropIntoRestSeat(mDragInfo.cell, d);
            return;
        }
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        CellLayout dropTargetLayout = mCellLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
        }

        if (mDragInfo != null) {
            final View cell = mDragInfo.cell;
            if (dropTargetLayout != null) {
                final ItemInfo info = (ItemInfo) cell.getTag();
                final int ocellX = info.cellX;
                final int ocellY = info.cellY;
                final int oscreen = info.screen;
                // Move internally
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);

                ItemInfo item = (ItemInfo) d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                int[] resultSpan = new int[2];
                mTargetCell = dropTargetLayout.createArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY, cell,
                        mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                if (foundCell) {
                    // update the item's position after drop
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    lp.cellX = lp.tmpCellX = mTargetCell[0];
                    lp.cellY = lp.tmpCellY = mTargetCell[1];
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;
                } else {
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.cellX;
                    mTargetCell[1] = lp.cellY;
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
                item.cellX = ocellX;
                item.cellY = ocellY;
                item.screen = oscreen;
            }
            if (d.dragView.hasDrawn()) {
                try {
                    mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, 100, null, this);
                } catch (Exception e) {
                }
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            dropTargetLayout.onDropChild(cell);
            if (mDragView == null || !saveSingleCell(mTargetCell[0], mTargetCell[1]))
                autoFixDuplicated(cell, mTargetCell[0], mTargetCell[1], mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], d);
        }
    }

    public void onDragEnter(DragObject dragObject) {
    }

    public void onDragExit(DragObject dragObject) {
        mCellLayout.setBackgroundAlpha(0);
        mCellLayout.clearDragOutlines();
    }

    private long mLastTime = 0;

    public void onDragOver(DragObject d) {
        long time = System.currentTimeMillis();
        if (time - mLastTime < 500) {
            return;
        }

        mLastTime = time;
        CellLayout dropTargetLayout = mCellLayout;
        ItemInfo item = (ItemInfo) d.dragInfo;

        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        // We want the point to be mapped to the dragTarget.
        mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);

        int minSpanX = item.spanX;
        int minSpanY = item.spanY;
        if (item.minSpanX > 0 && item.minSpanY > 0) {
            minSpanX = item.minSpanX;
            minSpanY = item.minSpanY;
        }

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        mTargetCell = dropTargetLayout.createArea((int) mDragViewVisualCenter[0],
                (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY, child,
                mTargetCell, null, CellLayout.MODE_DRAG_OVER);

        //dragOverInWorkspace(minSpanX, minSpanY, item, child);

        // Don't accept the drop if there's no room for the item
        if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
            dropTargetLayout.revertTempState();
            d.deferDragViewCleanupPostAnimation = false;
        }
    }

    private void dragOverInWorkspace(int minSpanX, int minSpanY, ItemInfo item,
            View child) {
        mWorkspaceCellLayout.createArea((int) mDragViewVisualCenter[0],
                (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY, child,
                mTargetCell, null, CellLayout.MODE_DRAG_OVER);
    }


    public boolean acceptDrop(DragObject d) {
        return true;
    }


    public DropTarget getDropTargetDelegate(DragObject dragObject) {
        return null;
    }


    public void onFlingToDelete(DragObject dragObject, int x, int y, PointF vec) {
    }
*/
    // End

    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    private void removeLastEmptyScreen() {
        if (mWorkspace == null) {
            return;
        }
        mWorkspace.removeLastEmptyScreen();
    }

    private int mLastPageIndex = -1;

    @Override
    public void onPageSwitch(View newPage, int newPageIndex) {
        if (getVisibility() == View.VISIBLE && mLastPageIndex != newPageIndex
                && !mDragController.isDragging()) {
            mLastPageIndex = newPageIndex;
            autoRangeCell(newPageIndex);
        }
        if (mWorkspace != null) {
            mWorkspaceCellLayout = (CellLayout) mWorkspace
                    .getChildAt(mWorkspace.getCurrentPage());
        }
    }

    public class ShadowLinearLayout extends LinearLayout implements DragSource, DropTarget {
        float mShadowRadius = IconCustomizer.getIconConfig().shadowRadius * FLOATING_SCALE;
        int mShadowColor = IconCustomizer.getIconConfig().shadowColor;
        int mIconHeight;
        boolean isDrawShadow = true;
        boolean isChildrenShrinked = false;

        public ShadowLinearLayout(Context context, int iconHeight) {
            super(context);
            mIconHeight = IconCustomizer.sCustomizedIconHeight;
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            canvas.save();
            if (child instanceof ImageView) {
                ImageView v = (ImageView) child;
                if (mShadowRadius > 0 && v != null && v.getVisibility() == View.VISIBLE && isDrawShadow) {
                    drawShadow(canvas, mShadowRadius, v);
                }
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        private Paint mBlurPaint;
        private Paint mShadowPaint;
        private Canvas mCanvas;
        private Bitmap mShadow;

        private void drawShadow(Canvas c, float shadow, ImageView v) {
            int dropShadow = Math.round(shadow);
            int height = mIconHeight;
            int width = mIconHeight;
            if (mShadow == null) {
                try {
                    if (mBlurPaint == null) {
                        mBlurPaint = new Paint();
                        mBlurPaint.setMaskFilter(new BlurMaskFilter(shadow * 2,
                                BlurMaskFilter.Blur.INNER));
                    }
                    if (mCanvas == null) {
                        mCanvas = new Canvas();
                        mCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                                Paint.FILTER_BITMAP_FLAG));
                    }
                    Drawable d = v.getDrawable();
                    Bitmap alpha = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    mCanvas.setBitmap(alpha);
                    d.draw(mCanvas);
                    alpha = alpha.extractAlpha(mBlurPaint, null);
                    if (mShadowPaint == null) {
                        mShadowPaint = new Paint();
                        mShadowPaint.setColor(Color.TRANSPARENT);
                        mShadowPaint.setShadowLayer(shadow, 0, 0, mShadowColor);
                    }

                    int doubleShadow = Math.round(shadow * 2);
                    mShadow = Bitmap.createBitmap(width + doubleShadow, height + doubleShadow, alpha.getConfig());
                    mCanvas.setBitmap(mShadow);
                    mCanvas.drawBitmap(alpha, dropShadow, dropShadow, mShadowPaint);
                    alpha.recycle();
                } catch (Exception e) {
                } catch (OutOfMemoryError e) {
                }
            }
            if (mShadow != null) {
                c.drawBitmap(mShadow, null, new Rect(v.getLeft() - dropShadow,
                        v.getTop(), v.getRight() + dropShadow, v.getBottom()
                        + dropShadow * 2), null);
            }
        }

        // added by weihong, #56790, add ShadowLinearLayout DropTarget
        // Start
        @Override
        public boolean supportsFlingToDelete() {
            // TODO Auto-generated method stub
            return true;
        }

        @Override
        public void onFlingToDeleteCompleted() {
            // TODO Auto-generated method stub
        }

        @Override
        public void onDropCompleted(View target, DragObject d,
                                    boolean isFlingToDelete, boolean success) {
        }

        @Override
        public boolean isDropEnabled() {
            // TODO Auto-generated method stub
            return true;
        }

        @Override
        public void onDrop(DragObject dragObject) {
            ItemInfo dragInfo = (ItemInfo) dragObject.dragInfo;
            // 这里写的不好，if的判断意思是在浮动状态拖到Restseat里
            //else判断是从文件夹拖入Restseat里，最好是根据dragSource去判断，Comment by Fan.Yang
            if (mDragInfo != null && mDragInfo.cell != null) {
                dropIntoRestSeat(mDragInfo.cell, dragObject);
                isDropIntoRestSeat = true;
            } else if (dragInfo != null){
                ShortcutIcon childView = (ShortcutIcon)mLauncher.createShortcut((ShortcutInfo) dragInfo);
                unbindParent(childView);
                // #62481 Add by Fan.Yang
                childView.setOnLongClickListener(mLauncher);
                dropIntoRestSeat(childView, dragObject);
            }
        }

        @Override
        public void onDragEnter(DragObject dragObject) {
        }

        @Override
        public void onDragOver(DragObject dragObject) {
            // TODO Auto-generated method stub
            DragSource source = dragObject.dragSource;
            if(source instanceof FolderLayout){
                ((FolderLayout)source).cancelExitAlarm();
            }
        }

        @Override
        public void onDragExit(DragObject dragObject) {
        }

        @Override
        public void onFlingToDelete(DragObject dragObject, int x, int y,
                                    PointF vec) {
            // TODO Auto-generated method stub
        }

        @Override
        public DropTarget getDropTargetDelegate(DragObject dragObject) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean acceptDrop(DragObject dragObject) {
            // TODO Auto-generated method stub
            boolean accept = true;
            if(mDragInfo != null && mDragInfo.cell instanceof  AppWidgetHostView){
                dragObject.deferDragViewCleanupPostAnimation = false;
                accept = false;
                dragObject.cancelled = true;
            }
            return accept;
        }

        @Override
        public void getLocationInDragLayer(int[] loc) {
            // TODO Auto-generated method stub
            //mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
            getRestSeatRect();
            loc[0] = mRestSeat.getLeft();
            loc[1] = mRestSeatRect.top;
        }

        public void getHitRect(Rect outRect) {
            getRestSeatRect();
            outRect.set(0, 0, mRestSeatRect.right, mRestSeatRect.bottom - mRestSeatRect.top);
        }
        // End
    }

    public class BounceHorizontalScrollView extends HorizontalScrollView {
        private final int sScrollToStartPositionTime = 80;
        private final int sMaxYOverscrollDistance = (int) (DENSITY * 50);
        boolean allowScrollBy = true;
        private float downX = 0;

        private static final int SCROLL_ZERO = 0;
        private boolean upIssue = false;

        public BounceHorizontalScrollView(Context context) {
            super(context);

            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_ALWAYS);
            disableEdgeEffect();
        }

        private void disableEdgeEffect() {
            try {
                EdgeEffect empty = new EmptyEdgeEffect(getContext());
                Field f = HorizontalScrollView.class.getDeclaredField("mEdgeGlowLeft");
                f.setAccessible(true);
                f.set(this, empty);
                f = HorizontalScrollView.class.getDeclaredField("mEdgeGlowRight");
                f.setAccessible(true);
                f.set(this, empty);
            } catch (Exception e) {
            }
        }

        private class EmptyEdgeEffect extends EdgeEffect {
            public EmptyEdgeEffect(Context context) {
                super(context);
            }

            @Override
            public boolean draw(Canvas canvas) {
                return true;
            }
        }

        @Override
        protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                                       int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY,
                                       boolean isTouchEvent) {
            if (allowScrollBy) {
                return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY,
                        sMaxYOverscrollDistance, maxOverScrollY, isTouchEvent);
            } else {
                return super.overScrollBy(0, deltaY, 0, scrollY, scrollRangeX, scrollRangeY,
                        sMaxYOverscrollDistance, maxOverScrollY, isTouchEvent);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                downX = ev.getX();
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (mOpeningFolder == null) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    upIssue = false;
                } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                    upIssue = true;
                } else if(ev.getAction() == MotionEvent.ACTION_MOVE){
                    mHandler.removeMessages(SCROLL_ZERO);
                    mHandler.sendEmptyMessageDelayed(SCROLL_ZERO, sScrollToStartPositionTime);
                }
                allowScrollBy = true;
                return super.onTouchEvent(ev);
            } else if (isShrinking()) {
                return true;
            }
            int width = mCellLayout.getWidth();
            int count = mRestSeat.getChildCount();
            int restWidth = count * (mRestIconHeight + mReatPadding);
            int rightEdge = Math.min((restWidth - scroll.getScrollX() ), width - 50);
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                downX = ev.getX();
                if (downX > rightEdge || mRestSeat.isChildrenShrinked) {
                    allowScrollBy = false;
                } else {
                    allowScrollBy = true;
                }
            } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                if (downX > rightEdge && (ev.getX() - downX < -50)) {
                    shrinkIconsInRestSeat(true);
                    allowScrollBy = false;
                    return true;
                }
                if (ev.getX() - downX > 30 && mRestSeat.isChildrenShrinked) {
                    shrinkIconsInRestSeat(false);
                }
                downX = 0;
            }
            return super.onTouchEvent(ev);
        }

        private Handler mHandler = new Handler(){

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == SCROLL_ZERO && (!upIssue || mRestSeat.isChildrenShrinked)) {
                    smoothScrollTo(0, 0);
                }
            }
        };
    }

    // private View mPostingView;
    private ArrayList<View> mPostingCells = new ArrayList<View>();

    public boolean isFolderCellPosting() {
        return mFloating && !mPostingCells.isEmpty();
    }

    private void addShortcutInFolder(final View v) {
        Object tag = v.getTag();
        FolderLayout folderLayout = mOpeningFolder.getFolderLayout();
        if (tag instanceof ShortcutIcon && !isFolderCellPosting() && !folderLayout.isPageMoving()) {
            mPostingCells.add(v);
            int currentPage = folderLayout.getCurrentPage();
            int pageCount = folderLayout.getChildCount();
            FolderScreen currentScreen = (FolderScreen) folderLayout.getChildAt(currentPage);
            if ((folderLayout.getCurrentPage() != pageCount - 1 && pageCount != 0)
                    || (currentScreen != null && currentScreen.getChildCount() == FolderScreen.CNT_PER_SCREEN)) {
                mOpeningFolder.snapToLastPage();
            } else {
                addShortcutInFolder();
            }
        }
    }

    private void addShortcutInFolder() {
        if (!isFolderCellPosting()) {
            return;
        }
        //to avoid when drop to reset seat and page moving caused force close
        if(!(mPostingCells.size() > 0 && mPostingCells.get(0) instanceof ImageView)){
            Log.e(TAG,"add to folder is not from reset seat");
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        if (mRestSeat.isChildrenShrinked) {
            mRestSeat.isChildrenShrinked = false;
            mPostingCells.clear();
            mRestSeat.isDrawShadow = false;
            int count = mRestSeat.getChildCount();
            for (int i = count - 1; i >= 0; i--) {
                View child = mRestSeat.getChildAt(i);
                if (child != null && child.getVisibility() == View.VISIBLE) {
                    mPostingCells.add(0, child);
                }
            }
            int[] lastPosition = mOpeningFolder.getFolderLastPosition();
            ArrayList<Animator> animators = new ArrayList<Animator>();
            boolean isNext = false;
            for (int i = 0; i < mPostingCells.size(); i++) {
                YLog.d("before,x=" + lastPosition[0] + ",y=" + lastPosition[1]);
                if (lastPosition[0] >= FolderScreen.ITEMS_PER_ROW) {
                    if (lastPosition[1] != FolderScreen.MAX_ROWS - 1) {
                        lastPosition[0] = 0;
                        lastPosition[1] = Math.min(FolderScreen.MAX_ROWS, ++lastPosition[1]);
                    } else {
                        isNext = true;
                        lastPosition[0] = 0;
                        lastPosition[1] = 0;
                    }
                }
                YLog.d("after,x=" + lastPosition[0] + ",y=" + lastPosition[1]);
                int[] offset = mOpeningFolder.computeFolderChildOffset(lastPosition[0], lastPosition[1], isNext);
                int delay = (isNext ? 0 : i) * 30;
                Animator animator = autoFillFolder(mPostingCells.get(i), offset, delay, true);
                animators.add(animator);
                lastPosition[0]++;
            }
            animatorSet.playTogether(animators);
        } else {
            int[] position = mOpeningFolder.getFolderLastPosition();
            int[] offset = mOpeningFolder.computeFolderChildOffset(position[0], position[1], false);
            View v = mPostingCells.get(0);
            Animator animator = autoFillFolder(v, offset, 0, false);
            animatorSet.play(animator);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ArrayList<ShortcutInfo> infos = new ArrayList<ShortcutInfo>();
                for (View posting : mPostingCells) {
                    View cell = (View) posting.getTag();
                    unbindParent(cell);
                    ShortcutInfo shortcutInfo = (ShortcutInfo) cell.getTag();
                    infos.add(shortcutInfo);
                }
                mOpeningFolder.mInfo.add(infos);
                mPostingCells.clear();
                mRestSeat.isDrawShadow = true;
            }
        });
        animatorSet.start();
    }

    private Animator autoFillFolder(final View view, int[] offset, int delay, boolean isShrinked) {
        final ShortcutIcon cell = (ShortcutIcon) view.getTag();
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell
                .getLayoutParams();
        int left = mRestContainer.getLeft();
        float vx = isShrinked ? (left + DENSITY * 10) : view.getLeft() - scroll.getScrollX();
        float vy = mCellLayout.getBottom() - 120 * DENSITY;
        unbindParent(cell);
        cell.setChecked(false);
        mCellLayout.addViewToCellLayoutTemp(cell, 0, lp);
        int cellLeft = offset[2] - mCellLayout.getPaddingLeft();
        int cellTop = offset[3] - mCellLayout.getPaddingTop();

        Animator animator = ObjectAnimator.ofPropertyValuesHolder(cell,
                PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE * 0.6f, 1),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE * 0.6f, 1),
                PropertyValuesHolder.ofFloat(View.X, vx, cellLeft - offset[0]),
                PropertyValuesHolder.ofFloat(View.Y, vy, cellTop - offset[1]));
        animator.setStartDelay(delay);
        animator.setDuration(ANIM_RANGING_DURATION);
        animator.setInterpolator(mRestOutInterpolator);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // TODO Auto-generated method stub
                view.setVisibility(View.GONE);
                mRestSeat.removeView(view);
                mRestSeat.clearDisappearingChildren();
            }
        });
        return animator;
    }

    private void addFolderInScreen(View v) {
        if (!((String) v.getTag()).equals("addFolder") || !mFloating) {
            return;
        }
        removeCallbacks(mAddFolderRunnable);
        postDelayed(mAddFolderRunnable, 100);
    }

    private Runnable mAddFolderRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDragController.isDragging() || mWorkspace.isPageMoving() || isRanging()) {
                return;
            }
            final int screen = mLauncher.getCurrentWorkspaceScreen();
            CellLayout main = mLauncher.getCellLayout(0, screen);
            int[] xy = new int[2];
            findEmptyCell(xy, main);
            if (xy[0] == -1 || xy[1] == -1) {
                Toast.makeText(mLauncher,getResources().getString(R.string.screen_not_space_available),Toast.LENGTH_SHORT).show();
                return;
            }
            FolderIcon folderIcon = mLauncher.addFolder(main,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, xy[0], xy[1]);
            setChecked(folderIcon, false);
            folderIcon.animate().scaleX(FLOATING_SCALE).scaleY(FLOATING_SCALE)
                    .setDuration(ANIM_TOGGLE_DURATION).setInterpolator(mSwitchInterpolator).start();
        }
    };

    private void setRestSeatFolderVisibility(boolean visibility) {
        for (int i = mRestSeat.getChildCount() - 1; i >= 0; i--) {
            View v = mRestSeat.getChildAt(i);
            if (v.getTag() instanceof FolderIcon) {
                if (visibility) {
                    v.setVisibility(View.VISIBLE);
                } else {
                    v.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void folderOpenStart(Folder folder) {
        // TODO Auto-generated method stub
        mOpeningFolder = folder;
        if (mFloating) {
            clearLayoutAnimation(mRestSeat);
            //lqwang - RP63662 - modify begin
            setTranslateLayoutTransition(mRestSeat,false);
            //lqwang - RP63662 - modify end
            setRestSeatFolderVisibility(false);
            scrollRestContainer();
            dropAllIntoRestSeat();
        }
    }

    @Override
    public void folderOpenEnd(Folder folder) {
        if (!mFloating || mOpeningFolder == null) {
            return;
        }
        mWorkspace.setPageSwitchListener(null);
        mWorkspace.setIsDropEnabled(false);
    }

    @Override
    public void folderCloseStart() {
        if (mFloating) {
            // #62485 Modify by fan.yang
            if(hasEnoughCells()){
                setRestSeatFolderVisibility(true);
            }
            scrollRestContainer();
        }
    }

    @Override
    public void folderCloseEnd() {
        // TODO Auto-generated method stub
        mOpeningFolder = null;
        if (!mFloating) {
            return;
        }
        if (!hasEnoughCells()) {
            shrinkIconsInRestSeat(false);
        }
        int current = mWorkspace.getCurrentPage();
        // added by weihong, 20141010
        if (!mDragController.isDragging()) {
            autoRangeCell(current);
        } else {
            for (int y = 0, NY = mCellLayout.getCountY(); y < NY; y++) {
                for (int x = 0, NX = mCellLayout.getCountX(); x < NX; x++) {
                    View v = mCellLayout.getChildAt(x, y);
                    if (v != null) {
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
        mWorkspace.setPageSwitchListener(this);
        mWorkspace.setIsDropEnabled(true);
    }

    @Override
    public void folderPageSwitch() {
        // TODO Auto-generated method stub
        addShortcutInFolder();
    }

    @Override
    public void folderChildClick(CellInfo cellInfo) {
        // TODO Auto-generated method stub
        if (cellInfo == null || mOpeningFolder == null || isFolderCellPosting()) {
            return;
        }
        if (!isNewFolderHide) {
            scrollRestContainer();
        }
        mPostingCells.add(cellInfo.cell);
        scroll.smoothScrollTo(0, 0);
        dropIntoRestSeatFromFolder(cellInfo.cellX, cellInfo.cellY);
    }

    private void dropIntoRestSeatFromFolder(int x, int y) {
        final ShortcutIcon cell = (ShortcutIcon) mPostingCells.get(0);
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
        unbindParent(cell);
        // #62481 Add by Fan.Yang
        cell.setOnLongClickListener(mLauncher);
        mCellLayout.addViewToCellLayoutTemp(cell, 0, new CellLayout.LayoutParams(0,0,lp.cellHSpan,lp.cellVSpan)); //PR68199 modify by lqwang
        int[] offset = mOpeningFolder.computeOnClickedChildOffset(x, y);
        int cellLeft = offset[2] - mCellLayout.getPaddingLeft();
        int cellTop = offset[3] - mCellLayout.getPaddingTop();

        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(cell,
                PropertyValuesHolder.ofFloat(View.X, cellLeft - offset[0], (int) (-DENSITY * 5)
                        - mCellLayout.getPaddingLeft()),
                PropertyValuesHolder.ofFloat(View.Y, cellTop - offset[1],
                        mRestTop - mCellLayout.getPaddingTop()),
                PropertyValuesHolder.ofFloat(View.SCALE_X, FLOATING_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, FLOATING_SCALE));
        anim.setDuration(ANIM_TOGGLE_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clear();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                clear();
            }

            private void clear() {
                mPostingCells.clear();
                mOpeningFolder.removeShortcut(cell);
                setTranslateLayoutTransition(mRestSeat, mRestSeat.getChildCount() > 0);
                cell.setChecked(true);
                mCellLayout.removeView(cell);
                addImageIcon(cell);
                if (mRestSeat.isChildrenShrinked) {
                    shrinkIconsInRestSeat(true);
                }
            }
        });
        anim.start();
    }

    AnimatorSet shrinkAnimator = new AnimatorSet();

    private boolean isShrinking() {
        if (shrinkAnimator != null && shrinkAnimator.isRunning()) {
            return true;
        }
        return false;
    }

    /*
     *  RestSeat中不显示文件夹,根据手势收缩图标动画
     */
    private boolean shrinkIconsInRestSeat(final boolean shrink) {
        //lqwang - RP63664 - modify begin
        if(isFolderCellPosting()){
            return true;
        }
        //lqwang - RP63664 - modify end
        if (isShrinking()) {
            shrinkAnimator.cancel();
        }
        ArrayList<Animator> animators = new ArrayList<Animator>();
        Animator animator = null;
        int paddingLeft = (int) (10 * mLauncher.getResources().getDisplayMetrics().density);
        int childCount = mRestSeat.getChildCount();
        int shortcutCount = 0;
        if (shrink) {
            for (int i = 0; i < childCount; i++) {
                final View child = mRestSeat.getChildAt(i);
                View tag = (View) child.getTag();
                int left = shortcutCount * mRestIconHeight + shortcutCount * mReatPadding
                        - shortcutCount * paddingLeft;
                int delay = shortcutCount * 30;
                animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, -left);
                animator.setStartDelay(delay);
                animators.add(animator);
                if (tag instanceof ShortcutIcon) shortcutCount++;
            }
            // #62478
            if(shortcutCount<2) return false;
        } else {
            for (int i = childCount - 1; i > -1; i--) {
                final View child = mRestSeat.getChildAt(i);
                int delay = (childCount - 1 - i) * 30;
                animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, 0);
                animator.setStartDelay(delay);
                animators.add(animator);
            }
        }

        shrinkAnimator = new AnimatorSet();
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                if (shrink) {
                    scroll.smoothScrollTo(0, 0);
                    mRestSeat.isDrawShadow = false;
                    mRestSeat.invalidate();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (shrink) {
                    mRestSeat.isDrawShadow = false;
                    mRestSeat.isChildrenShrinked = true;
                } else {
                    mRestSeat.isDrawShadow = true;
                    mRestSeat.isChildrenShrinked = false;
                    if(mOpeningFolder==null) setRestSeatFolderVisibility(true);
                    mRestSeat.invalidate();
                }
            }
        });
        shrinkAnimator.setDuration(ANIM_SHRINK_DURATION);
        shrinkAnimator.playTogether(animators);
        shrinkAnimator.start();
        return true;
    }

    /*
     *  RestSeat中显示文件夹的动画方式
     */
    private boolean shrinkIconsInRestSeat2(final boolean shrink) {
        ArrayList<Animator> animators = new ArrayList<Animator>();
        Animator animator = null;
        int paddingLeft = (int) (10 * mLauncher.getResources().getDisplayMetrics().density);
        int folderCount = 0;
        int shortcutCount = 0;
        for (int i = 0; i < mRestSeat.getChildCount(); i++) {
            final View child = mRestSeat.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof ShortcutIcon) {
                int left = i * (mRestIconHeight + mReatPadding) - shortcutCount * paddingLeft;
                int delay = shortcutCount * 30;
                if (shrink) {
                    animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, -left);
                } else {
                    animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, 0);
                }
                shortcutCount++;
                animator.setStartDelay(delay);
                animators.add(animator);
            }
        }

        //文件夹位置根据普通图标数目决定
        int shortCutRight = mReatPadding * 2 + (shortcutCount - 1) * paddingLeft + mRestIconHeight;
        for (int i = 0; i < mRestSeat.getChildCount() && shortcutCount > 1; i++) {
            final View child = mRestSeat.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof FolderIcon) {
                int left = folderCount * (mRestIconHeight + mReatPadding) + shortCutRight;
                if (shrink) {
                    animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X,
                            left - child.getLeft());
                } else {
                    animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, 0);
                }
                folderCount++;
                animators.add(animator);
            }
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                if (shrink) {
                    scroll.smoothScrollTo(0, 0);
                    mRestSeat.isDrawShadow = false;
                    mRestSeat.invalidate();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (shrink) {
                    mRestSeat.isDrawShadow = false;
                    mRestSeat.isChildrenShrinked = true;
                } else {
                    mRestSeat.isDrawShadow = true;
                    mRestSeat.isChildrenShrinked = false;
                    mRestSeat.invalidate();
                }
            }
        });
        animatorSet.setDuration(ANIM_SHRINK_DURATION);
        animatorSet.playTogether(animators);
        animatorSet.start();
        return true;
    }

    private void scrollRestContainer() {
        if (isNewFolderHide) {
            Animator animator = ObjectAnimator.ofFloat(mRestContainer, View.X, 0);
            animator.setDuration(200);
            if (hasEnoughCells()) {
                if (mOpeningFolder != null) {
                    // 延迟文件夹关闭的时间
                    animator.setStartDelay(420);
                }
            }
            animator.start();
            isNewFolderHide = false;
            addFolderImage.setVisibility(View.VISIBLE);
        } else if (mOpeningFolder != null && !isNewFolderHide) {
            addFolderImage.setVisibility(View.INVISIBLE);
            Animator animator = ObjectAnimator.ofFloat(mRestContainer, View.X,
                    -/*(floatingFolderCount + 1) **/ (mRestIconHeight + mReatPadding));
            animator.setDuration(200);
            animator.start();
            isNewFolderHide = true;
        }
    }

    private boolean hasEnoughCells() {
        final CellLayout layout = mLauncher.getCellLayout(0, mWorkspace.getCurrentPage());
        int count = 0;
        for (int y = 0, NY = layout.getCountY(); y < NY; y++) {
            for (int x = 0, NX = layout.getCountX(); x < NX; x++) {
                if (layout.getChildAt(x, y) == null) {
                    count++;
                }
            }
        }
        return count >= mRestSeat.getChildCount();
    }
}
