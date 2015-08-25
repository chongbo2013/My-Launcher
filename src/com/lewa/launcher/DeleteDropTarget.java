
package com.lewa.launcher;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.lewa.launcher.DragController.DragListener;

public class DeleteDropTarget extends RelativeLayout implements DropTarget, DragListener {
    private Launcher mLauncher;

    protected boolean mActive;
    private static int DELETE_ANIMATION_DURATION = 285;
    protected int mHoverColor = 0;
    protected int mTransitionDuration;
    private ImageView mDeleteIcon;
    private TextView mToastMessage;
    private TransitionDrawable mUninstallDrawable;
    private TransitionDrawable mCurrentDrawable;


    public DeleteDropTarget(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLauncher = (Launcher) context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources r = getResources();
        mTransitionDuration = r.getInteger(R.integer.config_dropTargetBgTransitionDuration);
        mHoverColor = r.getColor(R.color.delete_target_hover_tint);
        mUninstallDrawable = (TransitionDrawable) r.getDrawable(R.drawable.uninstall_target_selector);
        mDeleteIcon = (ImageView) findViewById(R.id.delete_icon);
        mUninstallDrawable.setCrossFadeEnabled(true);
        mToastMessage = (TextView) findViewById(R.id.message);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.addDropTarget(this);
    }

    public static boolean isApplication(Object info) {
        return (info instanceof ShortcutInfo)
                && (((ShortcutInfo) info).itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
    }

    private boolean isShortcut(DragObject d) {
        return (d.dragInfo instanceof ShortcutInfo)
                && (((ShortcutInfo) d.dragInfo).itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);
    }

    private boolean isWidget(DragObject d) {
        return (d.dragInfo instanceof LauncherAppWidgetInfo);
    }

    private boolean isFolder(Object d) {
        if (d instanceof FolderInfo) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isEmptyFolder(Object d) {
        if (d instanceof FolderInfo) {
            FolderInfo info = (FolderInfo) d;
            for (ShortcutInfo shortcut : info.contents) {
                if (shortcut.state == ShortcutInfo.STATE_OK || !shortcut.isHidden()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void setHoverColor() {
        mCurrentDrawable.startTransition(mTransitionDuration);
    }

    private void resetHoverColor() {
        mCurrentDrawable.resetTransition();
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        d.deferDragViewCleanupPostAnimation = false;
        ItemInfo item = (ItemInfo) d.dragInfo;
        //add by huzeyin for Bug64682 2014.12.8
        mLauncher.getWorkspace().scrollFolderPreview(item, true);
        if (item instanceof FolderInfo && ((FolderInfo) item).contents.size() > 0) {
            d.cancelled = true;
            return false;
        } else if (isSystemApp(item)) {
            d.cancelled = true;
            makeToastEx(getResources().getText(R.string.uninstall_system_app_text));
            //Toast.makeText(mLauncher, R.string.uninstall_system_app_text, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isVisible = true;
        boolean isUninstall = false;

        if ((isFolder(info) && !isEmptyFolder(info)) || mLauncher.isFloating() || mLauncher.getEditModeLayer().isDraggingAddWidget()) {
            return;
        }
        mLauncher.showStatusBar(false);
        setVisibility(VISIBLE);
        // mLauncher.getWorkspace().blurWallpaper(true, mDeleteZoneHeight, 0.0f);

        if (isApplication(info)) {
            isUninstall = true;
        }

        if (isUninstall) {
            mDeleteIcon.setImageDrawable(mUninstallDrawable);
            mCurrentDrawable = mUninstallDrawable;
        } else {
            mDeleteIcon.setImageDrawable(mUninstallDrawable);
            mCurrentDrawable = mUninstallDrawable;
        }
        mActive = isVisible;
        resetHoverColor();
        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);

        ObjectAnimator translationDown = mTranslationDownAnimation = createDownAnimation();
        translationDown.start();
    }

    private ObjectAnimator createDownAnimation() {
        final float height = getHeight();
        setY(-height);
        setAlpha(0);
        clearAnimation();
        ObjectAnimator translationDown = ObjectAnimator.ofFloat(this, "Y", 0);
        translationDown.setStartDelay(300);
        translationDown.setDuration(200);
        translationDown.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator anim) {
                setY(-height);
                setAlpha(1);
            }

        });
        return translationDown;
    }

    private boolean isMakingToast = false;

    public void makeToastEx(CharSequence text) {
        isMakingToast = true;
        mLauncher.showStatusBar(false);
        ObjectAnimator translationDown = mTranslationDownAnimation = createDownAnimation();
        mToastMessage.setText(text);
        translationDown.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mToastMessage.setVisibility(GONE);
                        mDeleteIcon.setVisibility(VISIBLE);
                        mLauncher.showStatusBar(true);
                        isMakingToast = false;
                        setVisibility(INVISIBLE);
                    }
                }, 1000);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
                mDeleteIcon.setVisibility(INVISIBLE);
                mToastMessage.setVisibility(VISIBLE);
            }
        });
        translationDown.start();
    }

    private ObjectAnimator mTranslationDownAnimation;

    public void clearAnimation() {
        if (mTranslationDownAnimation != null
                && mTranslationDownAnimation.isRunning())
            mTranslationDownAnimation.cancel();
    }

    @Override
    public void onDragEnd() {
        if (!isMakingToast) {
            mLauncher.showStatusBar(true);
        }
        mLauncher.getEditModeLayer().setDraggingAddWidgetEnd();
        // mLauncher.getWorkspace().blurWallpaper(false, mDeleteZoneHeight, 1);
        setVisibility(INVISIBLE);
        mActive = false;
    }

    public void onDragEnter(DragObject d) {
        setBackgroundResource(R.drawable.delete_zone_bg_active);
        d.dragView.setAlpha(0.7f);
        setHoverColor();
    }

    public void onDragExit(DragObject d) {
        setBackgroundResource(R.drawable.delete_zone_bg_normal);
        d.dragView.setAlpha(1.0f);
        if (!d.dragComplete) {
            resetHoverColor();
        } else {
            d.dragView.setColor(mHoverColor);
        }
    }

    private void animateToTrashAndCompleteDrop(final DragObject d) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);
        Rect to = getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                mCurrentDrawable.getIntrinsicWidth(), mCurrentDrawable.getIntrinsicHeight());
        float scale = (float) to.width() / from.width();

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                completeDrop(d);
            }
        };
        dragLayer.animateView(d.dragView, from, to, scale, 1f, 1f, 0.1f, 0.1f,
                DELETE_ANIMATION_DURATION, new DecelerateInterpolator(2),
                new LinearInterpolator(), onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    private void completeDrop(DragObject d) {
        ItemInfo item = (ItemInfo) d.dragInfo;
        if (isApplication(item)) {
            // Uninstall the application if it is being dragged from AppsCustomize
            PackageManager pm = mLauncher.getPackageManager();
            ResolveInfo resolveInfo = pm.resolveActivity(((ShortcutInfo) item).intent, 0);
            if (resolveInfo == null || resolveInfo.activityInfo == null) {
                item.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                LauncherModel.deleteItemFromDatabase(mLauncher, item);
            } else {
                mLauncher.startShortcutUninstallActivity((ShortcutInfo) item);
            }
        } else if (isShortcut(d)) {
            LauncherModel.deleteItemFromDatabase(mLauncher, item);
            if (d.dragSource instanceof FolderLayout) {
                Folder folder = ((FolderLayout) d.dragSource).getFolder();
                folder.getInfo().remove((ShortcutInfo) item);
            }
        } else if (isEmptyFolder(item)) {
            FolderInfo folderInfo = (FolderInfo) item;
//            Folder.dismissFolder(mLauncher, folderInfo);
            mLauncher.removeFolder(folderInfo);
            LauncherModel.dismissFolderContentsFromDatabase(mLauncher, folderInfo);
        } else if (isWidget(d)) {
            // Remove the widget from the workspace
            mLauncher.removeAppWidget((LauncherAppWidgetInfo) item);
            LauncherModel.deleteItemFromDatabase(mLauncher, item);

            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = mLauncher.getAppWidgetHost();
            if (appWidgetHost != null) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        appWidgetHost.deleteAppWidgetId(launcherAppWidgetInfo.appWidgetId);
                    }
                }.start();
            }
        }
    }

    public void onDrop(DragObject d) {
        animateToTrashAndCompleteDrop(d);
    }

    public void onFlingToDelete(final DragObject d, int x, int y, PointF vel) {

    }

    @Override
    public boolean isDropEnabled() {
        return mActive;
    }

    @Override
    public void onDragOver(DragObject dragObject) {

    }

    @Override
    public DropTarget getDropTargetDelegate(DragObject dragObject) {
        return null;
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    Rect getIconRect(int itemWidth, int itemHeight, int drawableWidth, int drawableHeight) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        // Find the rect to animate to (the view is center aligned)
        Rect to = new Rect();
        View v = findViewById(R.id.delete_icon);
        dragLayer.getViewRectRelativeToSelf(v, to);
        int width = drawableWidth;
        int height = drawableHeight;
        int left = to.left + getPaddingLeft();
        int top = to.top + (getMeasuredHeight() - height) / 2;
        to.set(left, top, left + width, top + height);

        // Center the destination rect about the trash icon
        int xOffset = (int) -(itemWidth - width) / 2;
        int yOffset = (int) -(itemHeight - height) / 2;
        to.offset(xOffset, yOffset);

        return to;
    }

    private boolean isSystemApp(Object info) {
        if (!(info instanceof ShortcutInfo)) {
            return false;
        }
        ShortcutInfo shortcutInfo = (ShortcutInfo) info;
        PackageManager pm = mLauncher.getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(shortcutInfo.intent, 0);
        if (resolveInfo != null
                && shortcutInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                && resolveInfo.activityInfo != null
                && resolveInfo.activityInfo.applicationInfo != null
                && (resolveInfo.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }
        return false;
    }
}
