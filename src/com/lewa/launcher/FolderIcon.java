/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lewa.launcher;

import java.util.ArrayList;

import lewa.content.res.IconCustomizer;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lewa.launcher.DropTarget.DragObject;
import com.lewa.launcher.FolderInfo.FolderListener;
import lewa.laml.FancyDrawable;

/**
 * An icon that can appear on in the workspace representing an {@link UserFolder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener {
    private Launcher mLauncher;
    private Folder mFolder;
    private FolderInfo mInfo;
    private static boolean sStaticValuesDirty = true;

    //    private CheckLongPressHelper mLongPressHelper;
    private Matrix matrix;
    public static final float PREVIEW_SCALE = 0.22f;

    // The number of icons to display in the
    public static final int NUM_ITEMS_IN_PREVIEW = 9;
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;
    private static final int DROP_IN_ANIMATION_DURATION = 400;
    private static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    // The degree to which the inner ring grows when accepting drop
    private static final float INNER_RING_GROWTH_FACTOR = 0.15f;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.24f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;

    private View mFolderIconZone;
    private ImageView mItemIcons[];
    private Rect mIconPreviewRect[];
    private View mPreviewBg;
    private FolderPreviewLayout mPreviewLayout;
    private TextView mFolderName;
    private TextView mUnread;
    private ImageView mCheck;
    //Open in floating mode
    private ImageView mOpen;
    private int previewHeight;
    private int previewSpace;

    FolderRingAnimator mFolderRingAnimator = null;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private int mIntrinsicIconSize;
    private float mBaselineIconScale;
    private int mBaselineIconSize;
    private int mAvailableSpaceInPreview;
    private int mTotalWidth = -1;
    private int mPreviewOffsetX;
    private int mPreviewOffsetY;
    private float mMaxPerspectiveShift;
    boolean mAnimating = false;
    float mShadowRadius = IconCustomizer.getIconConfig().shadowRadius;
    int mShadowColor = IconCustomizer.getIconConfig().shadowColor;

    private PreviewItemDrawingParams mParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private PreviewItemDrawingParams mAnimParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private ArrayList<ShortcutInfo> mHiddenItems = new ArrayList<ShortcutInfo>();
    private Drawable emptyDrawable;

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        setWillNotDraw(false);
        emptyDrawable = context.getResources().getDrawable(R.drawable.empty);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIconPreviewRect = new Rect[NUM_ITEMS_IN_PREVIEW];
        for (int i = 0; i < mIconPreviewRect.length; i++) {
            mIconPreviewRect[i] = new Rect();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int t = mFolderIconZone.getTop();
        int r = mPreviewBg.getRight();
        int uwidth = mUnread.getMeasuredWidth();
        int uheight = mUnread.getMeasuredHeight();
        mUnread.layout(r - uwidth * 2 / 3, t - uheight / 4, r + uwidth / 3, t + uheight * 3 / 4);

        int cWidth = mCheck.getMeasuredWidth();
        int cHeight = mCheck.getMeasuredHeight();
        mCheck.layout(r - cWidth * 2 / 3, t - cHeight / 4, r + cWidth / 3, t + cHeight * 3 / 4);
    }

    private void init() {
        //mLongPressHelper = new CheckLongPressHelper(this);
        matrix = new Matrix();
        matrix.postScale(PREVIEW_SCALE, PREVIEW_SCALE);
    }

    public void initParams() {
        int current = mPreviewLayout.getCurrentPage();
        FolderScreen previewScreen = (FolderScreen) mPreviewLayout.getChildAt(current);
        for (int i = 0; i < mIconPreviewRect.length && previewScreen != null; i++) {
            View child = previewScreen.getChildAt(i);
            if (child != null) {
                child.getGlobalVisibleRect(mIconPreviewRect[i]);
            }
        }
    }

    public Rect getIconPreviewRect(int index) {
        if (index < 0 || index > mIconPreviewRect.length - 1) {
            return null;
        }
        return mIconPreviewRect[index];
    }

    public boolean isDropEnabled() {
        return true;
    }

    static FolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
                              FolderInfo folderInfo, IconCache iconCache) {
        FolderIcon icon = (FolderIcon) LayoutInflater.from(launcher).inflate(resId, group, false);
        icon.mFolderName = (TextView) icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(Utilities.convertStr(launcher, folderInfo.title.toString()));
        icon.mPreviewBg = icon.findViewById(R.id.preview_background);
        icon.mFolderIconZone = icon.findViewById(R.id.folder_icon_zone);
        icon.mPreviewLayout = (FolderPreviewLayout) icon.findViewById(R.id.folder_preview_container);
        icon.mPreviewLayout.setClickable(false);
        icon.mUnread = (TextView) icon.findViewById(R.id.folder_unread);
        icon.mCheck = (ImageView) icon.findViewById(R.id.app_check);
        icon.mOpen = (ImageView) icon.findViewById(R.id.floating_open);

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) icon.mPreviewBg.getLayoutParams();
        lp.width = IconCustomizer.sCustomizedIconWidth;
        lp.height = IconCustomizer.sCustomizedIconHeight;
        icon.mPreviewBg.setLayoutParams(lp);
        icon.previewHeight = (int) Math.ceil(IconCustomizer.sCustomizedIconHeight * PREVIEW_SCALE);
        int padding = icon.mPreviewBg.getPaddingTop()+icon.mPreviewBg.getPaddingBottom();
        icon.previewSpace = (int) (IconCustomizer.sCustomizedIconHeight - 3 * icon.previewHeight - padding) / 2;
        //lqwang-modify for theme not effect for folder-begin
        Drawable d = iconCache.getIcon("com_android_folder.png");// for supporting V4 theme
        if (d == null) {
            d = iconCache.getIcon("icon_folder_background.png");//this will must be found because there exists one in system/media/theme/icons
        }
        //lqwang-modify for theme not effect for folder-end
        //d = launcher.getResources().getDrawable(R.drawable.icon_folder_background);
        icon.mPreviewBg.setBackgroundDrawable(icon.mIcon = d);

        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;
        icon.mOpen.setOnClickListener(launcher);
        icon.mOpen.setTag(folderInfo);

        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.mFolder = folder;
        icon.mPreviewLayout.setFolder(folder);
        icon.mFolderRingAnimator = new FolderRingAnimator(launcher, icon, iconCache);
        folderInfo.addListener(icon);
        icon.loadItemIcons(true);
        return icon;
    }

    public View getFolderIconZone() {
        return mFolderIconZone;
    }

    public View getFolderPreviewBg() {
        return mPreviewBg;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        sStaticValuesDirty = true;
        return super.onSaveInstanceState();
    }

    public static class FolderRingAnimator {
        public int mCellX;
        public int mCellY;
        private CellLayout mCellLayout;
        public float mInnerRingSize;
        public FolderIcon mFolderIcon = null;
        public static Drawable sSharedInnerRingDrawable = null;
        public static int sPreviewSize = -1;
        public static int sPreviewPadding = -1;

        private ValueAnimator mAcceptAnimator;
        private ValueAnimator mNeutralAnimator;

        public FolderRingAnimator(Launcher launcher, FolderIcon folderIcon, IconCache iconCache) {
            mFolderIcon = folderIcon;
            Resources res = launcher.getResources();
            // We need to reload the static values when configuration changes in case they are
            // different in another configuration
            if (sStaticValuesDirty) {
                sPreviewSize = IconCache.getAppIconSize(res);
                sPreviewPadding = res.getDimensionPixelSize(R.dimen.folder_preview_padding);
                sSharedInnerRingDrawable = iconCache.getIcon("icon_folder_background.png"); // for V5 theme
                if (sSharedInnerRingDrawable == null) { // for V4 theme
                    sSharedInnerRingDrawable = iconCache.getIcon("com_android_folder.png");
                }
                if (sSharedInnerRingDrawable == null) { // neither found
                    sSharedInnerRingDrawable = res.getDrawable(R.drawable.icon_folder_background);
                }
                sStaticValuesDirty = false;
            }
        }

        public void animateToAcceptState() {
            if (mNeutralAnimator != null) {
                mNeutralAnimator.cancel();
            }
            mAcceptAnimator = LauncherAnimUtils.ofFloat(0f, 1f);
            mAcceptAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);

            final int previewSize = sPreviewSize;
            mAcceptAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mInnerRingSize = (1 + percent * INNER_RING_GROWTH_FACTOR) * previewSize;
                    if (mCellLayout != null) {
                        mCellLayout.invalidate();
                    }
                }
            });
            mAcceptAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mFolderIcon != null && mFolderIcon.mPreviewBg != null) {
                        mFolderIcon.mPreviewBg.setBackgroundDrawable(null);
                    }
                }
            });
            mAcceptAnimator.start();
        }

        public void animateToNaturalState() {
            if (mAcceptAnimator != null) {
                mAcceptAnimator.cancel();
            }
            mNeutralAnimator = LauncherAnimUtils.ofFloat(0f, 1f);
            mNeutralAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);

            final int previewSize = sPreviewSize;
            mNeutralAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mInnerRingSize = (1 + (1 - percent) * INNER_RING_GROWTH_FACTOR) * previewSize;
                    if (mCellLayout != null) {
                        mCellLayout.invalidate();
                    }
                }
            });
            mNeutralAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mCellLayout != null) {
                        mCellLayout.hideFolderAccept(FolderRingAnimator.this);
                    }
                    if (mFolderIcon != null && mFolderIcon.mPreviewBg != null) {
                        mFolderIcon.mPreviewBg.setBackgroundDrawable(mFolderIcon.mIcon);
                    }
                }
            });
            mNeutralAnimator.start();
        }

        // Location is expressed in window coordinates
        public void getCell(int[] loc) {
            loc[0] = mCellX;
            loc[1] = mCellY;
        }

        // Location is expressed in window coordinates
        public void setCell(int x, int y) {
            mCellX = x;
            mCellY = y;
        }

        public void setCellLayout(CellLayout layout) {
            mCellLayout = layout;
        }

        public float getInnerRingSize() {
            return mInnerRingSize;
        }
    }

    Folder getFolder() {
        return mFolder;
    }

    FolderInfo getFolderInfo() {
        return mInfo;
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) &&
                !mFolder.isFull() && item != mInfo && !mInfo.opened);
    }

    public boolean acceptDrop(Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        return !mFolder.isDestroyed() && willAcceptItem(item);
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item);
    }

    public void onDragEnter(Object dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem((ItemInfo) dragInfo)) {
            return;
        }
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        CellLayout layout = (CellLayout) getParent().getParent();
        mFolderRingAnimator.setCell(lp.cellX, lp.cellY);
        mFolderRingAnimator.setCellLayout(layout);
        mFolderRingAnimator.animateToAcceptState();
        layout.showFolderAccept(mFolderRingAnimator);
    }

    public void onDragOver(Object dragInfo) {
    }

    public void performCreateAnimation(final ShortcutInfo destInfo, final View destView,
                                       final ShortcutInfo srcInfo, final DragView srcView, Rect dstRect,
                                       float scaleRelativeToDragLayer, Runnable postAnimationRunnable) {

        // These correspond two the drawable and view that the icon was dropped _onto_
        Drawable animateDrawable = ((ShortcutIcon) destView).getFavoriteCompoundDrawable();
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth());

        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, INITIAL_ITEM_ANIMATION_DURATION, false, null);
        addItem(destInfo);

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1, postAnimationRunnable, null);
    }

    public void performDestroyAnimation(final View finalView, Runnable onCompleteRunnable) {
        Drawable animateDrawable = ((ShortcutIcon) finalView).getFavoriteCompoundDrawable();
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                finalView.getMeasuredWidth());

        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, FINAL_ITEM_ANIMATION_DURATION, true,
                onCompleteRunnable);
    }

    public void onDragExit(Object dragInfo) {
        onDragExit();
    }

    public void onDragExit() {
        mFolderRingAnimator.animateToNaturalState();
    }

    private void onDrop(final ShortcutInfo item, DragView animateView, Rect finalRect,
                        float scaleRelativeToDragLayer, int index, Runnable postAnimationRunnable,
                        DragObject d) {
        item.cellX = -1;
        item.cellY = -1;

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        final Workspace workspace = mLauncher.getWorkspace();
        if (animateView != null) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            Rect from = new Rect();
            dragLayer.getViewRectRelativeToSelf(animateView, from);
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform((CellLayout) getParent().getParent());
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform((CellLayout) getParent().getParent());
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, center);
            center[0] = (int) Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = (int) Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < NUM_ITEMS_IN_PREVIEW ? 0.5f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;
            dragLayer.animateView(animateView, from, to, finalAlpha,
                    1, 1, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    new DecelerateInterpolator(2), new AccelerateInterpolator(2),
                    postAnimationRunnable, DragLayer.ANIMATION_END_DISAPPEAR, null);
            //#51745 Add by Fan.Yang
//            int dragIndex = mFolder.getCurrentDragIndex();
//            if (dragIndex != -1) {
//                mInfo.add(item, dragIndex, false);
//                mInfo.remove(item);
//                mFolder.mRemoveChildOnDropCompleted = false;
//            }
            addItem(item);
            mHiddenItems.add(item);
            postDelayed(new Runnable() {
                public void run() {
                    mHiddenItems.remove(item);
                    invalidate();
                    //lqwang - PR62736 - modify begin
                    workspace.scrollFolderPreview(null,true);
                    //lqwang - PR62736 - modify end
                }
            }, DROP_IN_ANIMATION_DURATION);
        } else {
            addItem(item);
            //lqwang - PR62736 - modify begin
            workspace.scrollFolderPreview(null,true);
            //lqwang - PR62736 - modify end
        }
    }

    public void onDrop(DragObject d) {
        ShortcutInfo item;
        if (d.dragInfo instanceof ApplicationInfo) {
            // Came from all apps -- make a copy
            item = ((ApplicationInfo) d.dragInfo).makeShortcut();
        } else {
            item = (ShortcutInfo) d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d.dragView, null, 1.0f, mInfo.contents.size(), d.postAnimationRunnable, d);
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize) {
            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;

            final int previewSize = FolderRingAnimator.sPreviewSize;
            final int previewPadding = FolderRingAnimator.sPreviewPadding;

            mAvailableSpaceInPreview = (previewSize - 2 * previewPadding);
            // cos(45) = 0.707  + ~= 0.1) = 0.8f
            int adjustedAvailableSpace = (int) ((mAvailableSpaceInPreview / 2) * (1 + 0.8f));

            int unscaledHeight = (int) (mIntrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));
            mBaselineIconScale = (1.0f * adjustedAvailableSpace / unscaledHeight);

            mBaselineIconSize = (int) (mIntrinsicIconSize * mBaselineIconScale);
            mMaxPerspectiveShift = mBaselineIconSize * PERSPECTIVE_SHIFT_FACTOR;

            mPreviewOffsetX = (mTotalWidth - mAvailableSpaceInPreview) / 2;
            mPreviewOffsetY = previewPadding;
        }
    }

    class PreviewItemDrawingParams {
        PreviewItemDrawingParams(float transX, float transY, float scale, int overlayAlpha) {
            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
            this.overlayAlpha = overlayAlpha;
        }

        float transX;
        float transY;
        float scale;
        int overlayAlpha;
        Drawable drawable;
    }

    private float getLocalCenterForIndex(int index, int[] center) {
        mParams = computePreviewItemDrawingParams(Math.min(NUM_ITEMS_IN_PREVIEW, index), mParams);

        mParams.transX += mPreviewOffsetX;
        mParams.transY += mPreviewOffsetY;
        float offsetX = mParams.transX + (mParams.scale * mIntrinsicIconSize) / 2;
        float offsetY = mParams.transY + (mParams.scale * mIntrinsicIconSize) / 2;

        center[0] = (int) Math.round(offsetX);
        center[1] = (int) Math.round(offsetY);
        return mParams.scale;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
                                                                     PreviewItemDrawingParams params) {
        index = NUM_ITEMS_IN_PREVIEW - index - 1;
        float r = (index * 1.0f) / (NUM_ITEMS_IN_PREVIEW - 1);
        float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

        float offset = (1 - r) * mMaxPerspectiveShift;
        float scaledSize = scale * mBaselineIconSize;
        float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float transY = mAvailableSpaceInPreview - (offset + scaledSize + scaleOffsetCorrection);
        float transX = offset + scaleOffsetCorrection;
        float totalScale = mBaselineIconScale * scale;
        final int overlayAlpha = (int) (80 * (1 - r));

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    public void setIconPressed(boolean pressed) {
        Drawable d = mPreviewBg.getBackground();
        if (d != null) {
            if (pressed) {
                d.setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP);
                mPreviewBg.setBackgroundDrawable(d);
            } else {
                d.clearColorFilter();
                mPreviewBg.setBackgroundDrawable(d);
            }
        }
    }

    public void loadItemIcons(boolean firstLoad) {
        if (mFolder == null) {
            return;
        }

        ArrayList<View> items = mFolder.getItemsInReadingOrder();
        if (items == null) {
            return;
        }

        //FR#51739 add by Fan.Yang
        int count = items.size();
        int pageCnt = (int) Math.ceil((double) count / FolderScreen.CNT_PER_SCREEN);
        pageCnt = Math.max(pageCnt, 1);
        mPreviewLayout.removeAllViews();
        for (int i = 0; i < pageCnt; i++) {
            FolderScreen screen = new FolderScreen(mLauncher, previewSpace, previewHeight);
            mPreviewLayout.addView(screen);
        }

        for (int i = 0; i < pageCnt * NUM_ITEMS_IN_PREVIEW; i++) {
            final ImageView imageView = new ImageView(mLauncher);
            if (i < count) {
//                Drawable d = ((ShortcutIcon) items.get(i)).getFavoriteCompoundDrawable();
//                imageView.setImageDrawable(d);
                final Drawable d = ((ShortcutIcon) items.get(i)).getFavoriteCompoundDrawable();
                Bitmap b;
                if (d instanceof FancyDrawable) {
                    b = ((BitmapDrawable) d.getCurrent()).getBitmap();
                } else {
                    b = ((FastBitmapDrawable) d).getBitmap();
                }
                //lqwang - PR63346 - modify begin
                if(firstLoad && d instanceof  FancyDrawable){
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            Bitmap bmp = ((BitmapDrawable)(d.getCurrent())).getBitmap();
                            Bitmap bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                            imageView.setBackgroundDrawable(new FastBitmapDrawable(bitmap));
                        }
                    }, 100);
                }else{
                    Bitmap bitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix,
                            true);
                    imageView.setImageDrawable(new FastBitmapDrawable(bitmap));
                }
                //lqwang - PR63346 - modify end
            } else {
                imageView.setImageDrawable(emptyDrawable);
            }
            mPreviewLayout.getScreen(i).addView(imageView);
        }
    }

    private void animateFirstItem(final Drawable d, int duration, final boolean reverse,
                                  final Runnable onCompleteRunnable) {
        final PreviewItemDrawingParams finalParams = computePreviewItemDrawingParams(0, null);

        final float scale0 = 1.0f;
        final float transX0 = (mAvailableSpaceInPreview - d.getIntrinsicWidth()) / 2;
        final float transY0 = (mAvailableSpaceInPreview - d.getIntrinsicHeight()) / 2;
        mAnimParams.drawable = d;

        ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1.0f);
        va.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (Float) animation.getAnimatedValue();
                if (reverse) {
                    progress = 1 - progress;
                    mPreviewBg.setAlpha(progress);
                }

                mAnimParams.transX = transX0 + progress * (finalParams.transX - transX0);
                mAnimParams.transY = transY0 + progress * (finalParams.transY - transY0);
                mAnimParams.scale = scale0 + progress * (finalParams.scale - scale0);
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimating = false;
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
            }
        });
        va.setDuration(duration);
        va.start();
    }

    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    public TextView getFolderName() {
        return mFolderName;
    }

    public void onItemsChanged() {
        loadItemIcons(false);
        invalidate();
        requestLayout();
    }

    public void onAdd(ShortcutInfo item) {
        updateFolderUnreadNum(item.intent.getComponent(), item.unreadNum);
        invalidate();
        requestLayout();
    }

    @Override
    public void onAdd(ArrayList<ShortcutInfo> items) {
        for (int i = 0; i < items.size(); i++) {
            ShortcutInfo item = items.get(i);
            updateFolderUnreadNum(item.intent.getComponent(), item.unreadNum);
        }
        invalidate();
        requestLayout();
    }

    public void onRemove(ShortcutInfo item) {
        updateFolderUnreadNum(item.intent.getComponent(), item.unreadNum);
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(Utilities.convertStr(getContext(), title.toString()));
        setContentDescription(String.format(getContext().getString(R.string.folder_name_format),
                title));
    }

    public void updateFolderUnreadNum() {
        final ArrayList<ShortcutInfo> contents = mInfo.contents;
        final int contentsCount = contents.size();
        int unreadNumTotal = 0;
        for (int i = 0; i < contentsCount; i++) {
            final ShortcutInfo shortcutInfo = contents.get(i);
            final ComponentName componentName = shortcutInfo.intent.getComponent();
            final int unreadNum = MessageModel.getUnreadNumberOfComponent(componentName);
            if (unreadNum > 0) {
                shortcutInfo.unreadNum = unreadNum;
                unreadNumTotal += unreadNum;
            }
        }
        setFolderUnreadNum(unreadNumTotal);
    }

    public void updateFolderUnreadNum(ComponentName component, int unreadNum) {
        final ArrayList<ShortcutInfo> contents = mInfo.contents;
        final int contentsCount = contents.size();
        int unreadNumTotal = 0;
        for (int i = 0; i < contentsCount; i++) {
            final ShortcutInfo appInfo = contents.get(i);
            final ComponentName name = appInfo.intent.getComponent();
            if (name != null && name.equals(component)) {
                // fix unread number of icon in folder error when clear unread number
                if (unreadNum >= 0) {
                    appInfo.unreadNum = unreadNum;
                    Settings.System.putInt(getContext().getContentResolver(), name.getClassName(), unreadNum);
                    if (unreadNum > 0) {
                        unreadNumTotal += unreadNum;
                    }
                }
            } else if (appInfo.unreadNum > 0) {
                unreadNumTotal += appInfo.unreadNum;
            }
        }
        setFolderUnreadNum(unreadNumTotal);
    }

    public void setFolderUnreadNum(int unreadNum) {
        if (unreadNum <= 0) {
            mInfo.unreadNum = 0;
            mUnread.setVisibility(View.GONE);
        } else {
            mInfo.unreadNum = unreadNum;
            mUnread.setText(MessageModel.getDisplayText(unreadNum));
            if (!mLauncher.isFloating() && !mLauncher.isEditMode()) {
                mUnread.setVisibility(View.VISIBLE);
            } else {
                mUnread.setVisibility(View.GONE);
            }
        }
    }

    public void setFolderHatVisible(boolean visible) {
        if (!visible) {
            mOpen.setVisibility(View.GONE);
            if (mInfo.unreadNum > 0) {
                mUnread.setVisibility(View.VISIBLE);
            }
        } else {
            mOpen.setVisibility(View.VISIBLE);
            mUnread.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // mLongPressHelper.postCheckForLongPress();
                setIconPressed(true);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // mLongPressHelper.cancelLongPress();
                setIconPressed(false);
                break;
        }
        return result;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // mLongPressHelper.cancelLongPress();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mShadowRadius > 0) {
            drawShadow(canvas, mShadowRadius);
        }
        super.onDraw(canvas);
    }

    private Paint mBlurPaint;
    private Paint mShadowPaint;
    private Canvas mCanvas;
    private Bitmap mShadow;
    private Drawable mIcon;

    private void drawShadow(Canvas c, float shadow) {
        int dropShadow = Math.round(shadow);
        int height = IconCustomizer.sCustomizedIconHeight;
        int width = IconCustomizer.sCustomizedIconWidth;
        if (mIcon != null && mShadow == null) {
            try {
                if (mBlurPaint == null) {
                    mBlurPaint = new Paint();
                    mBlurPaint.setMaskFilter(new BlurMaskFilter(shadow * 2,
                            BlurMaskFilter.Blur.INNER));
                }
                if (mCanvas == null) {
                    mCanvas = new Canvas();
                }
                Bitmap alpha;
                if (mIcon instanceof BitmapDrawable) {
                    alpha = ((BitmapDrawable) mIcon).getBitmap();
                    alpha = alpha.extractAlpha(mBlurPaint, null);
                    width = alpha.getWidth();
                    height = alpha.getHeight();
                } else {
                    alpha = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    mCanvas.setBitmap(alpha);
                    mIcon.draw(mCanvas);
                    alpha = alpha.extractAlpha(mBlurPaint, null);
                }
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
            View v = mPreviewBg;
            View p = mFolderIconZone;
            float left = v.getLeft() + p.getLeft() - shadow;
            float top = v.getTop() + p.getTop() + shadow / 2;
            float right = left + IconCustomizer.sCustomizedIconWidth + shadow * 2;
            float bottom = top + IconCustomizer.sCustomizedIconHeight + shadow * 1.5f;
            c.drawBitmap(mShadow, null, new RectF(left, top, right, bottom), null);
        }
    }

    public Drawable getSnapshot() {
        final View v = mPreviewBg;
        Bitmap bmp = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        mPreviewBg.draw(c);
        return new BitmapDrawable(getResources(), bmp);
    }

    public void setChecked(boolean checked) {
        if (checked) {
            mCheck.setVisibility(View.VISIBLE);
            mUnread.setVisibility(View.GONE);
            ObjectAnimator.ofFloat(mCheck, View.ALPHA, 0, 1).setDuration(100).start();
        } else {
            mCheck.setVisibility(View.INVISIBLE);
            updateFolderUnreadNum();
        }
        if (mLauncher.isFloating()) {
            if (checked) {
                mOpen.setVisibility(View.GONE);
            } else {
                mOpen.setVisibility(View.VISIBLE);
                mUnread.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onReBind() {
        loadItemIcons(false);
    }

    @Override
    public void onUpdate() {
        // TODO Auto-generated method stub

    }

    @Override
    protected void drawableStateChanged() {
        if (isPressed()) {
            setAlpha(0.6f);
        } else {
            setAlpha(1f);
        }
    }

    //FR#51739 add by Fan.Yang
    private final static int STATE_FIRST = 0;
    private final static int STATE_SCROLLING = 1;
    private final static int STATE_LAST = 2;
    private int mState = STATE_FIRST;

    private FolderPreviewLayout getPreviewLayout() {
        return mPreviewLayout;
    }

    public void setCurrentPage(int page) {
        mPreviewLayout.setCurrentPage(page);
    }

    public void scrollIconPreview(boolean toLeft) {
        if (mLauncher.isFloating() && toLeft) {
            return;
        }

        if (!toLeft) {
            addEmptyPageAtLast();
            mPreviewLayout.snapToLastPage();
        } else if (toLeft) {
            mPreviewLayout.snapToFirstPage();
            loadItemIcons(false);
        }
    }

    public void resetPreviewScroll() {
        if (getVisibility() == VISIBLE) {
            mPreviewLayout.snapToFirstPage();
        }
    }

    private boolean addEmptyPageAtLast() {
        int lastPage = mPreviewLayout.getChildCount() - 1;
        FolderScreen folderScreen = (FolderScreen) mPreviewLayout
                .getChildAt(lastPage);
        if (folderScreen == null) {
            return false;
        }
        mFolder.addEmptyPageIfNeed();
        int visibleCount = mInfo.getVisibleCnt();
        if (visibleCount > 0 && visibleCount % FolderScreen.CNT_PER_SCREEN == 0) {
            FolderScreen screen = new FolderScreen(mLauncher, previewSpace, previewHeight);
            mPreviewLayout.addView(screen);
            for (int i = 0; i < NUM_ITEMS_IN_PREVIEW; i++) {
                ImageView imageView = new ImageView(mLauncher);
                imageView.setImageDrawable(emptyDrawable);
                screen.addView(imageView);
            }
            return true;
        }
        return false;
    }
}
