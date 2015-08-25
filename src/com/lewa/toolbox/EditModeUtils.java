package com.lewa.toolbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import lewa.util.ImageUtils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Property;
import android.view.Display;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.lewa.launcher.CellLayout;
import com.lewa.launcher.Launcher;
import com.lewa.launcher.LauncherApplication;
import com.lewa.launcher.Workspace;
import com.lewa.launcher.bean.EditWidgetGroup;
import com.lewa.launcher.bean.OnlineWallpaper;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.launcher.wallpaper.NetBaseParam;
import com.lewa.launcher.wallpaper.UrlParam;
import com.lewa.launcher.R;
import com.lewa.reflection.ReflUtils;

public class EditModeUtils {
    private static final int ANIM_SWITCHING_DURATION = 600;
    public static final float SCALE = 0.8f;
    private static final String TAG = "EditModeUtils";
    private static TimeInterpolator mSwitchInterpolator = new BounceInterpolator();
    private static boolean DEBUG = false;
    public static final String SYSTEMGADGETS = "system_gadgets";
    public static final String EFFECTPREFIX = "effect_";
    public static final String PREFERENCE_WALLPAPER_PATH = "lewa_wallpaper_path";
    public static final String WALLPAPER_CHANGED = "com.lewa.launcher.wallpaperchanged";
    public static final String CUSTOM_WALLPAPER = "com.lewa.customwallpaper.jpg";
    public static final int DESKTOP_WALLPAPER = 4;
    public static final int ANIM_OUT_DELAY = 300;
    private static final int ANIM_IN_DELAY = 50;
    private static DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    public static void batchScaleCell(Context context, boolean start,
                                      boolean anim, int animScreen) {
        Launcher mLauncher = (Launcher) context;
        Workspace mWorkspace = mLauncher.getWorkspace();
        for (int i = 0, N = mWorkspace.getChildCount(); i < N; i++) {
            if (start) {
                batchSetCellScale(mLauncher, i, anim
                        && i == animScreen, false, ANIM_IN_DELAY, 1, 0.9f, SCALE);
            } else {
                batchSetCellScale(mLauncher, i, anim && i == animScreen, false, ANIM_OUT_DELAY, SCALE,1.0f);
            }
        }
    }

    public static void batchSetCellScale(Launcher launcher, int screen,boolean anim, boolean needDelay, int delay,float... values) {
        CellLayout layout = launcher.getCellLayout(0, screen);
        ViewGroup group = layout.getShortcutsAndWidgets();
        ArrayList<Animator> anims = new ArrayList<Animator>();
        Random random = new Random();
        PropertyValuesHolder[] pvs = null;
        if (anim) {
            pvs = new PropertyValuesHolder[]{
                    PropertyValuesHolder.ofFloat(View.SCALE_X, values),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, values)};
        }
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View v = group.getChildAt(i);
            if (v instanceof AppWidgetHostView) {
                continue;
            }
            if (anim) {
                ObjectAnimator a = ObjectAnimator
                        .ofPropertyValuesHolder(v, pvs);
                a.setInterpolator(mSwitchInterpolator);
                if (needDelay) {
                    a.setStartDelay(random.nextInt(delay));
                }
                anims.add(a);
            } else {
                v.setScaleX(values[values.length - 1]);
                v.setScaleY(values[values.length - 1]);
            }
        }
        if (anims.size() > 0) {
            AnimatorSet set = new AnimatorSet();
            set.setDuration(800);
            set.playTogether(anims);
            set.start();
        }
    }

    public static void animScaleView(View v, float scaleStart, float scaleEnd, long startDelay, AnimatorListener listener) {
        PropertyValuesHolder[] pvs = new PropertyValuesHolder[]{
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleStart,
                        scaleEnd),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleStart,
                        scaleEnd)
        };
        ObjectAnimator a = ObjectAnimator
                .ofPropertyValuesHolder(v, pvs);
        a.setInterpolator(mSwitchInterpolator);
        a.setDuration(ANIM_SWITCHING_DURATION);
        a.setStartDelay(startDelay);
        if (listener != null)
            a.addListener(listener);
        a.start();
    }


    public static Bitmap parseBitmap(String path, Config decodeConfig,
                                     int w, int h) {
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        if (w == 0 && h == 0) {
            decodeOptions.inPreferredConfig = decodeConfig;
            decodeOptions.inSampleSize = 2;
            bitmap = BitmapFactory.decodeFile(path, decodeOptions);
        } else {
            // If we have to resize this image, first get the natural bounds.
            decodeOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, decodeOptions);
            int actualWidth = decodeOptions.outWidth;
            int actualHeight = decodeOptions.outHeight;

            // Then compute the dimensions we would ideally like to decode to.
            int desiredWidth = getResizedDimension(w, h,
                    actualWidth, actualHeight);
            int desiredHeight = getResizedDimension(w, h,
                    actualHeight, actualWidth);

            // Decode to the nearest power of two scaling factor.
            decodeOptions.inJustDecodeBounds = false;
            // TODO(ficus): Do we need this or is it okay since API 8 doesn't
            // support it?
            // decodeOptions.inPreferQualityOverSpeed =
            // PREFER_QUALITY_OVER_SPEED;
            decodeOptions.inSampleSize = findBestSampleSize(actualWidth,
                    actualHeight, desiredWidth, desiredHeight);
            Bitmap tempBitmap = BitmapFactory.decodeFile(path, decodeOptions);

            // If necessary, scale down to the maximal acceptable size.
            int width = tempBitmap.getWidth();
            int height = tempBitmap.getHeight();
            if (tempBitmap != null
                    && (tempBitmap.getWidth() != w || tempBitmap
                    .getHeight() != h)) {
                Matrix matrix = new Matrix();
                float scaleWidth = ((float) w / width);
                float scaleHeight = ((float) h / height);
                matrix.postScale(scaleWidth, scaleHeight);
                bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height,
                        matrix, true);
                tempBitmap.recycle();
            } else {
                bitmap = tempBitmap;
            }
        }

        return bitmap;
    }

    public static Bitmap createReflectedBitmap(Resources res, Bitmap originalImage, boolean recyleOrigin) {
        // The gap we want between the reflection and the original image
        final int reflectionGap = res.getDimensionPixelSize(R.dimen.reflect_gap);

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        // This will not scale but will flip on the Y axis
        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);
        // Create a Bitmap with the flip matrix applied to it.
        // We only want the bottom half of the image
        Bitmap reflectionImage = Bitmap.createBitmap(originalImage, 0,
                height / 2, width, height / 2, matrix, false);

        // Create a new bitmap with same width but taller to fit reflection
        Bitmap bitmapWithReflection = Bitmap.createBitmap(width,
                (height + height / 3), Config.ARGB_8888);

        // Create a new Canvas with the bitmap that's big enough for
        // the image plus gap plus reflection
        Canvas canvas = new Canvas(bitmapWithReflection);
        // Draw in the original image
        canvas.drawBitmap(originalImage, 0, 0, null);
        // Draw in the gap
        Paint defaultPaint = new Paint();
        defaultPaint.setColor(res.getColor(android.R.color.transparent));
        canvas.drawRect(0, height, width, height + reflectionGap, defaultPaint);
        // Draw in the reflection
        canvas.drawBitmap(reflectionImage, 0, height + reflectionGap, null);

        // Create a shader that is a linear gradient that covers the reflection
        Paint paint = new Paint();
        LinearGradient shader = new LinearGradient(0,
                originalImage.getHeight(), 0, bitmapWithReflection.getHeight()
                + reflectionGap, 0xffffffff, 0x00ffffff,
                TileMode.MIRROR);
        // Set the paint to use this shader (linear gradient)
        paint.setShader(shader);
        // Set the Transfer mode to be porter duff and destination in
        paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
        // Draw a rectangle using the paint with our linear gradient
        canvas.drawRect(0, height, width, bitmapWithReflection.getHeight()
                + reflectionGap, paint);
        if (recyleOrigin) {
            originalImage.recycle();
        }
        return bitmapWithReflection;
    }

    public static Bitmap parseBitmap(Resources res, int id,
                                     Config decodeConfig, int w, int h) {
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        if (w == 0 && h == 0) {
            decodeOptions.inPreferredConfig = decodeConfig;
            decodeOptions.inSampleSize = 2;
            bitmap = BitmapFactory.decodeResource(res, id);
        } else {
            // If we have to resize this image, first get the natural bounds.
            decodeOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, id, decodeOptions);
            int actualWidth = decodeOptions.outWidth;
            int actualHeight = decodeOptions.outHeight;

            // Then compute the dimensions we would ideally like to decode to.
            int desiredWidth = getResizedDimension(w, h,
                    actualWidth, actualHeight);
            int desiredHeight = getResizedDimension(w, h,
                    actualHeight, actualWidth);

            // Decode to the nearest power of two scaling factor.
            decodeOptions.inJustDecodeBounds = false;
            // TODO(ficus): Do we need this or is it okay since API 8 doesn't
            // support it?
            // decodeOptions.inPreferQualityOverSpeed =
            // PREFER_QUALITY_OVER_SPEED;
            decodeOptions.inSampleSize = findBestSampleSize(actualWidth,
                    actualHeight, desiredWidth, desiredHeight);
            Bitmap tempBitmap = BitmapFactory.decodeResource(res, id,
                    decodeOptions);

            // If necessary, scale down to the maximal acceptable size.
            if (tempBitmap != null
                    && (tempBitmap.getWidth() != w || tempBitmap
                    .getHeight() != h)) {
                /*bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWidth,
                        desiredHeight, true);*/
                int width = tempBitmap.getWidth();
                int height = tempBitmap.getHeight();
                Matrix matrix = new Matrix();
                float scaleWidth = (float) w / width;
                float scaleHeight = (float) h / height;
                matrix.postScale(scaleWidth, scaleHeight);
                bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height, matrix, true);
                tempBitmap.recycle();
            } else {
                bitmap = tempBitmap;
            }
        }

        return bitmap;
    }

    /**
     * Scales one side of a rectangle to fit aspect ratio.
     *
     * @param maxPrimary      Maximum size of the primary dimension (i.e. width for max
     *                        width), or zero to maintain aspect ratio with secondary
     *                        dimension
     * @param maxSecondary    Maximum size of the secondary dimension, or zero to maintain
     *                        aspect ratio with primary dimension
     * @param actualPrimary   Actual size of the primary dimension
     * @param actualSecondary Actual size of the secondary dimension
     */
    private static int getResizedDimension(int maxPrimary, int maxSecondary,
                                           int actualPrimary, int actualSecondary) {
        // If no dominant value at all, just return the actual.
        if (maxPrimary == 0 && maxSecondary == 0) {
            return actualPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling
        // ratio.
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;
        if (resized * ratio > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    /**
     * Returns the largest power-of-two divisor for use in downscaling a bitmap
     * that will not result in the scaling past the desired dimensions.
     *
     * @param actualWidth   Actual width of the bitmap
     * @param actualHeight  Actual height of the bitmap
     * @param desiredWidth  Desired width of the bitmap
     * @param desiredHeight Desired height of the bitmap
     */
    // Visible for testing.
    static int findBestSampleSize(int actualWidth, int actualHeight,
                                  int desiredWidth, int desiredHeight) {
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }

        return (int) n;
    }

    public static boolean isSDMounted() {
        String state = Environment.getExternalStorageState();
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
    }

    public static InputStream getCalculateStream(String wallpaperPath,
                                                 int displayWidth, int displayHeight) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(wallpaperPath, options);
        int actualWidth = options.outWidth;
        int actualHeight = options.outHeight;
        options.inSampleSize = findBestSampleSize(actualWidth, actualHeight,
                displayWidth, displayHeight);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(wallpaperPath, options);
        if(bitmap != null){
            float scaleX = (float) displayWidth / actualWidth;
            float scaleY = (float) displayHeight / actualHeight;
            Matrix matrix = new Matrix();
            matrix.preScale(scaleX, scaleY);
            Bitmap scale_bmp = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scale_bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            baos.close();
            bitmap.recycle();
            scale_bmp.recycle();
            return is;
        }else{
            return new FileInputStream(wallpaperPath);
        }
    }

    public static boolean isSystemApplication(Context context,
                                              String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo packageInfo = manager.getPackageInfo(packageName,
                    PackageManager.GET_CONFIGURATIONS);
            if ((packageInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void logE(String tag, String msg) {
        if (DEBUG)
            Log.e(tag, msg);
    }

    /**
     * the widgets show category:
     * 1.system app widget show in the front
     * 2.system app widget show expand
     * 3.shrink all thirty party app widget
     * but some prebuilt thirty party app is exist as system app,some time is show unsatisfactory,so add custom category:
     * 1.we filter some system app not expand use method {@link com.lewa.toolbox.EditModeUtils#exclude(android.content.Context, String)}.
     * 2.we reorder the list and put some system app int the front use method {@link com.lewa.toolbox.EditModeUtils#sortWidgetList(android.content.Context, java.util.List)}
     * @param context
     * @return
     */
    public static Map<String, ArrayList<Object>> classifyGadgetByPackages(
            Context context) {
        List<AppWidgetProviderInfo> widgets = AppWidgetManager.getInstance(
                context).getInstalledProviders();
        PackageManager mPackageManager = context.getPackageManager();
        int mCellCountX = PreferencesProvider.getCellCountX(context);
        int mCellCountY = PreferencesProvider.getCellCountY(context);
        ArrayList<Object> mItems = new ArrayList<Object>();
        TreeMap<String, ArrayList<Object>> sortPackages = new TreeMap<String, ArrayList<Object>>();//the TreeMap use large time to compare,use comparetor 218ms,when remove just 40ms

        if (!LauncherApplication.isSystemApp()
                && !isSupportWidgetBind(mPackageManager)) {
            Intent thirdPartyIntent = new Intent();
            thirdPartyIntent.setClassName(context.getPackageName(),
                    "com.lewa.launcher.ThirdPartyWidgets");
            List<ResolveInfo> thirdList = mPackageManager
                    .queryIntentActivities(thirdPartyIntent, 0);
            mItems.addAll(thirdList);
        } else {
            for (AppWidgetProviderInfo widget : widgets) {
                int[] spanXY = Launcher.getSpanForWidget(context, widget);
                if (widget.minWidth > 0 && widget.minHeight > 0
                        && spanXY[0] <= mCellCountX && spanXY[1] <= mCellCountY) {
                    mItems.add(widget);
                } else {
                    Log.e(TAG, "Widget " + widget.provider
                            + " has invalid dimensions (" + widget.minWidth
                            + ", " + widget.minHeight + ")");
                }
            }
        }

        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        List<ResolveInfo> shortcuts = mPackageManager.queryIntentActivities(
                shortcutsIntent, 0);
        mItems.addAll(shortcuts);
        long startTime = System.currentTimeMillis();
        for (Object object : mItems) {
            putObject(context, sortPackages, object);
        }
        //lqwang - PR944628 - modify begin
        TreeMap<String, ArrayList<Object>> sortResult = new TreeMap<String, ArrayList<Object>>(new widgetAndShortcutsComparetor(context));
        for(Map.Entry<String,ArrayList<Object>> entry : sortPackages.entrySet()){
            sortResult.put(entry.getKey(),entry.getValue());
        }
        //lqwang - PR944628 - modify end
        logE(TAG, "classify gadgets total time: " + (System.currentTimeMillis() - startTime));
        return sortResult;
    }

    public static void sortWidgetList(Context context,List<Object> lists) {
        final List<String> orderList = Arrays.asList(context.getResources().getStringArray(R.array.system_widget_orders));
        if(lists != null && lists.size() > 0){
            Collections.sort(lists,new Comparator<Object>() {
                @Override
                public int compare(Object lhs, Object rhs) {
                    String lhs_pkg = getWidgetPackageName(lhs);
                    String rhs_pkg = getWidgetPackageName(rhs);
                    if(!TextUtils.isEmpty(lhs_pkg) && !TextUtils.isEmpty(rhs_pkg)){
                        int lhs_index = orderList.indexOf(lhs_pkg);
                        int rhs_index = orderList.indexOf(rhs_pkg);
                        if(lhs_index >= 0 && rhs_index < 0){
                            return -1;
                        }else if(lhs_index < 0 && rhs_index >= 0){
                            return 1;
                        }
                    }
                    return 0;
                }
            });
        }
    }

    public static String getWidgetPackageName(Object object) {
        String packageName = null;
        if (object instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo appWidgetProviderInfo = (AppWidgetProviderInfo) object;
            packageName = appWidgetProviderInfo.provider.getPackageName();
        } else if (object instanceof ResolveInfo) {
            ResolveInfo resolveInfo = (ResolveInfo) object;
            packageName = resolveInfo.activityInfo.packageName;
        }else if(object instanceof EditWidgetGroup){
            packageName = ((EditWidgetGroup) object).getPkgName();
        }
        return packageName;
    }

    private static void putObject(Context context,
                                  TreeMap<String, ArrayList<Object>> sortPackages, Object object) {
        String packageName = null;
        boolean isShortCuts = false;
        if (object instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo appWidgetProviderInfo = (AppWidgetProviderInfo) object;
            packageName = appWidgetProviderInfo.provider.getPackageName();
        } else if (object instanceof ResolveInfo) {
            isShortCuts = true;
            ResolveInfo resolveInfo = (ResolveInfo) object;
            packageName = resolveInfo.activityInfo.packageName;
        }
        if (TextUtils.isEmpty(packageName))
            return;
        String key = null;
        if (!isShortCuts && isSystemApplication(context, packageName) && !exclude(context,packageName)) {
            key = SYSTEMGADGETS;
        } else {
            key = packageName;
        }
        ArrayList<Object> lists = sortPackages.get(key);

        if (lists != null) {
            EditModeUtils.logE(TAG, "putObject list is already exist");
            lists = sortPackages.get(key);
            lists.add(object);
        } else {
            EditModeUtils.logE(TAG, "putObject list is null");
            lists = new ArrayList<Object>();
            lists.add(object);
            sortPackages.put(key, lists);
        }
    }

    /**
     * system widget not to expand,origin category is system widget all expand,but sometime is not beautiful
     * @param context
     * @param pkgName
     * @return
     */
    private static boolean exclude(Context context,String pkgName) {
        List<String> orderList = Arrays.asList(context.getResources().getStringArray(R.array.expand_exclude));
        return orderList.contains(pkgName);
    }

    public static boolean isSupportWidgetBind(PackageManager pm) {
        Intent intent = new Intent(ReflUtils.ACTION_APPWIDGET_BIND);
        ResolveInfo info = pm.resolveActivity(intent, 0);
        return info != null;
    }

    /*
     * 获取程序的名字
     */
    public static String getAppName(Context context, String packname) {
        String name = null;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packname, 0);
            name = info.loadLabel(pm).toString();
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            if (packname.equals(SYSTEMGADGETS))
                name = context.getString(R.string.system_gadgets);
            e.printStackTrace();

        }
        return name;
    }

    private static class widgetAndShortcutsComparetor implements
            Comparator<String> {
        private Collator mCollator;
        private Context mContext;

        public widgetAndShortcutsComparetor(Context context) {
            mCollator = Collator.getInstance();
            mContext = context;
        }

        @Override
        public int compare(String a, String b) {
            // TODO Auto-generated method stub
            boolean isaSystemGadgets = isSystemGadgets(a);
            boolean isbSystemGadgets = isSystemGadgets(b);
            if (isaSystemGadgets && !isbSystemGadgets) {
                return -1;
            } else if (!isaSystemGadgets && isbSystemGadgets) {
                return 1;
            }
            if (!isaSystemGadgets && !isbSystemGadgets) {
                boolean isaSystemApp = isSystemApplication(mContext, a);
                boolean isbSystemApp = isSystemApplication(mContext, b);
                if (isaSystemApp && !isbSystemApp) {
                    return -1;
                } else if (!isaSystemApp && isbSystemApp) {
                    return 1;
                }
            }
            String aLabel = getAppName(mContext, a);
            String bLabel = getAppName(mContext, b);
            return mCollator.compare(aLabel, bLabel);
        }
    }

    private static boolean isSystemGadgets(String pkg) {
        if (pkg != null)
            return pkg.equals(SYSTEMGADGETS);
        return false;
    }

    public static int getIconDPI(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getLauncherLargeIconDensity();
    }

    public static Drawable zoomDrawable(Drawable drawable, int w, int h) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        Bitmap oldbmp = drawableToBitmap(drawable);
        Matrix matrix = new Matrix();
        float scaleWidth = ((float) w / width);
        float scaleHeight = ((float) h / height);
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height,
                matrix, true);
        oldbmp.recycle();
        return new BitmapDrawable(newbmp);
    }

    public static Bitmap zoomBitmap(Bitmap oldbmp, int w, int h) {
        int width = oldbmp.getWidth();
        int height = oldbmp.getHeight();
        Matrix matrix = new Matrix();
        float scaleWidth = ((float) w / width);
        float scaleHeight = ((float) h / height);
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height,
                matrix, true);
//        oldbmp.recycle();
        return newbmp;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                : Bitmap.Config.RGB_565;
        Bitmap bitmap = Bitmap.createBitmap(width, height, config);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    public static int getDrawableId(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable",
                "com.lewa.launcher");
    }

    private static int screenshotWidth, screenshotHeight;

    public static BitmapDrawable getBlurScreenshot(Context mContext) {
        if (screenshotWidth == 0) {
            Display display = ((WindowManager) mContext
                    .getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            screenshotWidth = Math.min(display.getWidth(), display.getHeight()) / 2;
            screenshotHeight = Math
                    .max(display.getWidth(), display.getHeight()) / 2;
        }
        Bitmap mBackground = null;
        if (mBackground == null) {
            Bitmap shotBitmap = SurfaceWrapper.screenshot(screenshotWidth,
                    screenshotHeight);
            Bitmap b = Bitmap.createScaledBitmap(shotBitmap,shotBitmap.getWidth() / 2 , shotBitmap.getHeight() / 2,true);
            if (b != null && b.getWidth() > 1) {
                mBackground = Bitmap.createBitmap(b.getWidth(),
                        b.getHeight(), Bitmap.Config.ARGB_8888);
                mBackground.eraseColor(0xFF000000);
                ImageUtils.fastBlur(b, mBackground, 20);
                b.recycle();
            }

            if (mBackground != null) {
                return new BitmapDrawable(mContext.getResources(), mBackground);
            }
        }
        return null;
    }

    public static Bitmap getStorageBg(int height, int x, int y, Context context, Bitmap src) {
        Resources res = context.getResources();
        Bitmap mask0 = BitmapFactory.decodeResource(res, R.drawable.widget_storage_shade);
        Bitmap mask1 = BitmapFactory.decodeResource(res, R.drawable.widget_storage_light);
        if (mask0 == null || mask1 == null)
            return null;
        Bitmap newBitmap = Bitmap.createBitmap(src, x / 4, y / 4, src.getWidth() - x / 2, height / 4);
        int w = newBitmap.getWidth();
        int h = newBitmap.getHeight();
        int mask_w = mask0.getWidth();
        int mask_h = mask0.getHeight();
        Bitmap b = Bitmap.createBitmap(mask_w, mask_h, Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(newBitmap, new Rect(0, 0, w, h), new Rect(0, 0, mask_w, mask_h), null);
        Paint sPaint = new Paint();
        sPaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));

        canvas.drawBitmap(mask0, 0, 0, sPaint);
        sPaint.setXfermode(null);
        canvas.drawBitmap(mask1, 0, 0, sPaint);
        src.recycle();
        mask0.recycle();
        mask1.recycle();
        return b;
    }

    public static Bitmap getMultiBg(Context context, Bitmap src) {
        if (src == null)
            return null;
        Resources res = context.getResources();
        Bitmap preference_bg = BitmapFactory.decodeResource(res, R.drawable.preference_bg);
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap b = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint();
        canvas.drawBitmap(preference_bg, new Rect(0, 0, width, height), new Rect(0, 0, width, height), paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.DARKEN));
        canvas.drawBitmap(src, 0, 0, paint);
        src.recycle();
        preference_bg.recycle();
        return b;
    }

    public static void recyleBitmapDrawable(BitmapDrawable bitmapDrawable) {
        if (bitmapDrawable != null) {
            Bitmap b = bitmapDrawable.getBitmap();
            recyleBitmap(b);
        }
    }

    public static void recyleBitmap(Bitmap b) {
        if (b != null && !b.isRecycled()) {
            b.recycle();
        }
    }


    /**
     * Copy data from a source stream to destFile.
     * Return true if succeed, return false if failed.
     */
    public static boolean copyToFile(InputStream inputStream, File destFile) {
        try {
            if (destFile.exists()) {
                destFile.delete();
            }
            FileOutputStream out = new FileOutputStream(destFile);
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } finally {
                out.flush();
                try {
                    out.getFD().sync();
                } catch (IOException e) {
                }
                out.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public static NetBaseParam initWallpaperUrl() {
        return UrlParam.newUrlParam(UrlParam.WALLPAPER, DESKTOP_WALLPAPER);
    }

    public static boolean isWallpaperExists(String pkgName) {
        File file = new File(Constants.WALLPAPER_FULL_PATH.concat("/").concat(pkgName).concat(Constants.JPEG));
        boolean exist = file.exists();
        file = null;
        return exist;
    }

    public static String getOnlineWallpaperSavedPath(OnlineWallpaper onlineWallpaper) {
        String pkgName = onlineWallpaper.getPackageName();
        if (pkgName != null)
            return Constants.WALLPAPER_FULL_PATH.concat("/").concat(pkgName).concat(Constants.JPEG);
        return null;
    }

    public static boolean checkNetWork(Context context) {
        boolean valid = isNetworkValid(context);
        if (!valid) {
            Toast.makeText(context, context.getString(R.string.no_available_network), 0).show();
        }
        return valid;
    }


    /**
     * @param context
     * @return
     */
    public static boolean isNetworkValid(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info != null) {
            return info.isAvailable();
        }
        return false;
    }

    public static void MeasureListView(ListView listView) {
        ListAdapter mAdapter = listView.getAdapter();
        if (mAdapter == null) {
            return;
        }
        int totalHeight = 0;
        for (int i = 0; i < mAdapter.getCount(); i++) {
            View mView = mAdapter.getView(i, null, listView);
            mView.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            // mView.measure(0, 0);
            totalHeight += mView.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight
                + (listView.getDividerHeight() * (mAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }


    public static Animator alphaAnimate(View view, int durationMillis, long startOffset,float fromAlpha,float toAlpha,AnimatorListener listener) {
        if (view != null) {
            PropertyValuesHolder holder = PropertyValuesHolder.ofFloat("alpha", fromAlpha, toAlpha);
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view, holder);
            anim.setStartDelay(startOffset);
            anim.setDuration(durationMillis);
            if(listener != null){
                anim.addListener(listener);
            }
            anim.start();
            return anim;
        }
        return null;
    }

    public static Animator translateAnimate(Property<?, Float> property, WeakReference<View> ref, int durationMillis, long startOffset, float fromXDelta, float toXDelta, AnimatorListener listener, Interpolator interpolator) {
        if (ref != null) {
            View v = ref.get();
            if (v == null) {
                return null;
            }
            Animator animator = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(property, fromXDelta, toXDelta));
            animator.setDuration(durationMillis);
            if (interpolator != null) {
                animator.setInterpolator(interpolator);
            } else {
                animator.setInterpolator(decelerateInterpolator);
            }
            animator.setStartDelay(startOffset);
            if (listener != null) {
                animator.addListener(listener);
            }
            animator.start();
            return animator;
        }
        return null;
    }

    public static Animator getAnimate(Property<?, Float> property, WeakReference<View> ref, int durationMillis, long startOffset, float fromXDelta, float toXDelta, AnimatorListener listener, Interpolator interpolator) {
        if (ref != null) {
            View v = ref.get();
            if (v == null) {
                return null;
            }
            Animator animator = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(property, fromXDelta, toXDelta));
            animator.setDuration(durationMillis);
            if (interpolator != null) {
                animator.setInterpolator(interpolator);
            } else {
                animator.setInterpolator(decelerateInterpolator);
            }
            animator.setStartDelay(startOffset);
            if (listener != null) {
                animator.addListener(listener);
            }
            return animator;
        }
        return null;
    }

    public static void startLoading(View v) {
        if (v != null) {
            v.setVisibility(View.VISIBLE);
            PropertyValuesHolder holder = PropertyValuesHolder.ofFloat("rotation", 0, 360);
            ObjectAnimator loading_anim = ObjectAnimator.ofPropertyValuesHolder(v, holder);
            loading_anim.setRepeatCount(Animation.INFINITE);
            loading_anim.setRepeatMode(Animation.RESTART);
            loading_anim.setInterpolator(new LinearInterpolator());
            loading_anim.setDuration(800);
            loading_anim.start();
            v.setTag(loading_anim);
        }
    }

    public static void stopLoading(View v) {
        if (v != null) {
            v.setVisibility(View.GONE);
        }
        Object object = v.getTag();
        if (object != null && object instanceof ObjectAnimator) {
            ((ObjectAnimator)object).cancel();
        }
    }

    /**
     * interface for mms #60986
     * @param context
     * @param in
     */
    public static void setInEditMode(final Context context, final boolean in){
        new Thread("setInEditModeThread"){
            @Override
            public void run() {
                Settings.System.putString(context.getContentResolver(),Constants.EDITMODE_SETTING_KEY,String.valueOf(in));
            }
        }.start();
    }

    public static boolean isKitKat() {
        return Build.VERSION.SDK_INT >= 19;
    }

    public static String getPathFromUri(final Context context, final Uri uri) {
        String authority = uri.getAuthority();
        // ExternalStorageProvider
        if ("com.android.externalstorage.documents".equals(authority)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            }
        }
        // DownloadsProvider
        else if ("com.android.providers.downloads.documents".equals(authority)) {
            final String id = DocumentsContract.getDocumentId(uri);
            final Uri contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    Long.valueOf(id));
            return getDataColumn(context, contentUri, null, null);
        }
        // MediaProvider
        else if ("com.android.providers.media.documents".equals(authority)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            final String selection = "_id=?";
            final String[] selectionArgs = new String[] { split[1] };
            return getDataColumn(context, contentUri, selection,
                    selectionArgs);
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri,
                                       String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
