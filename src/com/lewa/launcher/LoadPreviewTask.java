package com.lewa.launcher;

import lewa.content.res.IconCustomizer;
import lewa.laml.util.AppIconsHelper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;

import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.lewa.launcher.bean.EditBaseObject;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.IIoadImageListener;
import com.lewa.launcher.EditGridAdapter.VIEWTYPE;
import com.lewa.launcher.R;

public class LoadPreviewTask implements Runnable {
    private static final String TAG = "LoadPreviewTask";
    private PREVIEWTYPE mType;
    private Context mContext;
    private EditBaseObject mObject;
    private PackageManager mPackageManager;
    private Handler mHandler;
    private IIoadImageListener mImageLoadListener;
    private Resources res;
    private PendingAddItemInfo mItemInfo;
    private static int PREVIEW_HEIGHT;
    private int mAppIconSize;
    private VIEWTYPE mViewType;
    private int SHORTCUT_WIDTH;

    public LoadPreviewTask(Context context, PREVIEWTYPE type,
                           VIEWTYPE viewType, EditBaseObject object,
                           PendingAddItemInfo itemInfo, Handler handler,
                           IIoadImageListener imageLoadListener) {
        mType = type;
        mContext = context;
        mObject = object;
        mHandler = handler;
        mImageLoadListener = imageLoadListener;
        mPackageManager = mContext.getPackageManager();
        res = mContext.getResources();
        mItemInfo = itemInfo;
        PREVIEW_HEIGHT = res.getDimensionPixelSize(R.dimen.edit_widget_image_height);
        mAppIconSize = (int) (IconCache.getAppIconSize(res) * 0.8);
        mViewType = viewType;
        SHORTCUT_WIDTH = res
                .getDimensionPixelSize(R.dimen.shortcut_img_width);
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        if (mType == PREVIEWTYPE.GROUP) {
            String pkg = mObject.getPkgName();
            try {
                PackageInfo packInfo = mPackageManager.getPackageInfo(pkg,
                        PackageManager.GET_ACTIVITIES);
                final Bitmap b = getShortcutPreview(packInfo.applicationInfo,
                        mContext);
                displayBitmap(pkg, b, IconCustomizer.sCustomizedIconWidth,
                        IconCustomizer.sCustomizedIconHeight);
            } catch (NameNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else if (mType == PREVIEWTYPE.WIDGET) {
            if (mItemInfo != null) {
                Bitmap b = getItemBitmap(mItemInfo, mViewType);
                if (b == null)
                    EditModeUtils.logE(TAG, "class name : " + mItemInfo.componentName.getClassName() + " bitmap is null");
                if (b != null) {
                    EditModeUtils.logE(TAG, "class name : " + mItemInfo.componentName.getClassName());
                    displayBitmap(mItemInfo.componentName.getClassName(), b,
                            mItemInfo.cellX, mItemInfo.cellY);
                }
            }
        }
    }

    private void displayBitmap(String label, final Bitmap b, int width,
                               int height) {
        if (b != null) {
            String key = LauncherApplication.getCacheKey(label, width, height);
            LauncherApplication.cacheBitmap(key, b);
            if(mImageLoadListener != null){
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        mImageLoadListener.onLoadComplete(b);
                    }
                });
            }
        }
    }

    public enum PREVIEWTYPE {
        GROUP, WIDGET
    }

    private Bitmap getShortcutPreview(ApplicationInfo info, Context context) {
        // Render the icon
        Drawable icon = EditModeUtils.zoomDrawable(
                IconCustomizer.getCustomizedIcon(context,info), SHORTCUT_WIDTH, SHORTCUT_WIDTH);
        return ((BitmapDrawable) icon).getBitmap();
    }

    private Bitmap getItemBitmap(PendingAddItemInfo itemInfo, VIEWTYPE viewType) {
        if(isMmsWidget(itemInfo)){
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.lewa.PIM","com.android.contacts.activities.MessageActivity"));
            LauncherApplication app = ((LauncherApplication) mContext.getApplicationContext());
            Log.e("wangliqiang","get mms widget preview");
            return EditModeUtils.zoomBitmap(app.getIconCache().getIcon(intent),SHORTCUT_WIDTH,SHORTCUT_WIDTH);
        }
        if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {

            return getShortcutPreview(itemInfo, viewType == VIEWTYPE.GRID);

        } else if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {

            return getWidgetPreview(itemInfo, viewType, itemInfo.spanX,
                    itemInfo.spanY);
        }
        return null;
    }

    private boolean isMmsWidget(PendingAddItemInfo itemInfo){
        if(itemInfo.getComponentName() != null && itemInfo.getComponentName().getClassName().equals("com.android.mms.widget.MmsWidgetProvider")){
            return true;
        }
        return false;
    }

    private Bitmap getShortcutPreview(PendingAddItemInfo info,boolean scale) {
        Bitmap b = null;
        if(info.getComponentName() != null){
            LauncherApplication app = ((LauncherApplication) mContext.getApplicationContext());
            Intent intent = new Intent();
            intent.setComponent(info.getComponentName());
            b = app.getIconCache().getIcon(intent);
            if (scale && b != null)
                return EditModeUtils.zoomBitmap(b, SHORTCUT_WIDTH, SHORTCUT_WIDTH);
        }
        return b;
    }

    private Bitmap getWidgetPreview(PendingAddItemInfo itemInfo,
                                    VIEWTYPE viewType, int cellHSpan, int cellVSpan) {
        // Load the preview image if possible
        PendingAddWidgetInfo info = (PendingAddWidgetInfo) itemInfo;

        String packageName = info.componentName.getPackageName();
        Bitmap preview = null;
        if (info.previewImage != 0) {
            try {
                Resources res = mPackageManager
                        .getResourcesForApplication(packageName);
                if (viewType == VIEWTYPE.GRID) {
                    preview = EditModeUtils.parseBitmap(res, info.previewImage,
                            Config.ARGB_8888, 0, 0);
                } else {
                    preview = EditModeUtils.parseBitmap(res, info.previewImage,
                            Config.ARGB_4444, 0, 0);
                }
            } catch (NameNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        // Generate a preview image if we couldn't load one
        if (preview == null) {
            try {
                if (viewType == VIEWTYPE.GRID) {
                    PackageInfo packInfo = mPackageManager.getPackageInfo(
                            info.componentName.getPackageName(),
                            PackageManager.GET_ACTIVITIES);
                    preview = getShortcutPreview(packInfo.applicationInfo,
                            mContext);
                } else {
                    Drawable icon = null;
                    if (info.icon > 0) {
                        icon = mPackageManager.getDrawable(packageName,
                                info.icon, null);
                    }
                    if (icon == null) {
                    } else {
                        preview = createPreviewBitmap(icon, cellHSpan,
                                cellVSpan);
                    }

                }
            } catch (NameNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return preview;
    }

    Bitmap createPreviewBitmap(Drawable d, int spanX, int spanY) {
        Bitmap preview = null;
        int sourceHeight = d.getIntrinsicHeight();
        int sourceWidth = d.getIntrinsicWidth();
        int width, height;
        if (sourceHeight == sourceWidth) {
            height = width = (int) (mAppIconSize * 0.7);
        } else {
            height = (int) (mAppIconSize * 0.7);
            width = (int) (height * 1.5);
        }
        final float ratio = (float) sourceWidth / sourceHeight;
        if (sourceWidth > sourceHeight) {
            height = (int) (width / ratio);
        } else if (sourceHeight > sourceWidth) {
            width = (int) (height * ratio);
        }
        int yoffset = (PREVIEW_HEIGHT - height) / 2;
        preview = Bitmap.createBitmap(width, PREVIEW_HEIGHT,
                Bitmap.Config.ARGB_8888);
        renderDrawableToBitmap(d, preview, 0, yoffset, width, height);
        return preview;
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x,
                                        int y, int w, int h) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f);
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x,
                                        int y, int w, int h, float scale) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            c.setBitmap(null);
        }
    }
}
