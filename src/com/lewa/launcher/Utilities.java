package com.lewa.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.lewa.launcher.ShellUtils.CommandResult;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
public final class Utilities {
    private static final String TAG = "Launcher.Utilities";
    
    private static int sIconWidth = -1;
    private static int sIconHeight = -1;
    private static int sIconTextureWidth = -1;
    private static int sIconTextureHeight = -1;
    
    private static final Paint sBlurPaint = new Paint();
    private static final Paint sGlowColorPressedPaint = new Paint();
    private static final Paint sGlowColorFocusedPaint = new Paint();
    private static final Paint sDisabledPaint = new Paint();
    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();
    public static final boolean useResizeFrame = true;
    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }
    static int sColors[] = { 0xffff0000, 0xff00ff00, 0xff0000ff };
    static int sColorIndex = 0;

    /**
     * Returns a bitmap suitable for the all apps view. Used to convert pre-ICS
     * icon bitmaps that are stored in the database (which were 74x74 pixels at hdpi size)
     * to the proper size (48dp)
     */
    static Bitmap createIconBitmap(Bitmap icon, Context context) {
        int textureWidth = sIconTextureWidth;
        int textureHeight = sIconTextureHeight;
        int sourceWidth = icon.getWidth();
        int sourceHeight = icon.getHeight();
        if (sourceWidth > textureWidth && sourceHeight > textureHeight) {
            // Icon is bigger than it should be; clip it (solves the GB->ICS migration case)
        return Bitmap.createBitmap(icon,
                    (sourceWidth - textureWidth) / 2,
                    (sourceHeight - textureHeight) / 2,
                    textureWidth, textureHeight);
        } else if (sourceWidth == textureWidth && sourceHeight == textureHeight) {
            // Icon is the right size, no need to change it
            return icon;
        } else {
            // Icon is too small, render to a larger bitmap
            final Resources resources = context.getResources();
            return createIconBitmap(new BitmapDrawable(resources, icon), context);
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    static Bitmap createIconBitmap(Drawable icon, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            int width = sIconWidth;
            int height = sIconHeight;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();
            if (sourceWidth > 0 && sourceHeight > 0) {
                // There are intrinsic sizes.
                if (width < sourceWidth || height < sourceHeight) {
                    // It's too big, scale it down.
                    final float ratio = (float) sourceWidth / sourceHeight;
                    if (sourceWidth > sourceHeight) {
                        height = (int) (width / ratio);
                    } else if (sourceHeight > sourceWidth) {
                        width = (int) (height * ratio);
                    }
                } else if (sourceWidth < width && sourceHeight < height) {
                    // Don't scale up the icon
                    width = sourceWidth;
                    height = sourceHeight;
                }
            }

            // no intrinsic size --> use default size
            int textureWidth = sIconTextureWidth;
            int textureHeight = sIconTextureHeight;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;
            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            icon.draw(canvas);
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    /**
     * Returns a Bitmap representing the thumbnail of the specified Bitmap.
     * The size of the thumbnail is defined by the dimension
     * android.R.dimen.launcher_application_icon_size.
     *
     * @param bitmap The bitmap to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified bitmap or the bitmap itself if the
     *         thumbnail could not be created.
     */
    static Bitmap resampleIconBitmap(Bitmap bitmap, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            if (bitmap.getWidth() == sIconWidth && bitmap.getHeight() == sIconHeight) {
                return bitmap;
            } else {
                final Resources resources = context.getResources();
                return createIconBitmap(new BitmapDrawable(resources, bitmap), context);
            }
        }
    }

    private static void initStatics(Context context) {
        final Resources resources = context.getResources();
        final DisplayMetrics metrics = resources.getDisplayMetrics();
        final float density = metrics.density;
        
        sIconWidth = sIconHeight = IconCache.getAppIconSize(resources);
        sIconTextureWidth = sIconTextureHeight = sIconWidth;
        
        sBlurPaint.setMaskFilter(new BlurMaskFilter(5 * density, BlurMaskFilter.Blur.NORMAL));
        sGlowColorPressedPaint.setColor(0xffffc300);
        sGlowColorFocusedPaint.setColor(0xffff8e00);
        
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.2f);
        sDisabledPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        sDisabledPaint.setAlpha(0x88);
    }

    /** Only works for positive numbers. */
    static int roundToPow2(int n) {
        int orig = n;
        n >>= 1;
        int mask = 0x8000000;
        while (mask != 0 && (n & mask) == 0) {
            mask >>= 1;
        }
        while (mask != 0) {
            n |= mask;
            mask >>= 1;
        }
        n += 1;
        if (n != orig) {
            n <<= 1;
        }
        return n;
    }
    
    static int generateRandomId() {
        return new Random(System.currentTimeMillis()).nextInt(1 << 24);
    }
    
    public static boolean isSystemApp(PackageInfo packageInfo){
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return false;
        }
        return (packageInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
    }
    
    public static boolean isSystemApp(ResolveInfo resolvedInfo) {
        if (resolvedInfo == null || resolvedInfo.activityInfo == null
            || resolvedInfo.activityInfo.applicationInfo == null) {
            return false;
        }
        return (resolvedInfo.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
    }
    
    public static String convertStr(Context context, String resourceName) {
        if (resourceName != null && resourceName.startsWith("com.lewa.launcher")) {
            Resources res = context.getResources();
            final int id = res.getIdentifier(resourceName, null, null);
            return res.getString(id);
        }
         return resourceName;
     }
     
    public static class PackageInstalledTimeComparator implements Comparator<Object> {
        private final PackageManager mPackageManager;
        private final HashMap<Object, PackageInfo> mPackageInfoCache;
        int order = 1;
        
        PackageInstalledTimeComparator (PackageManager pm) {
            mPackageManager = pm;
            mPackageInfoCache = new HashMap<Object, PackageInfo>();
        }
        
        PackageInstalledTimeComparator (PackageManager pm, boolean descending) {
            mPackageManager = pm;
            mPackageInfoCache = new HashMap<Object, PackageInfo>();
            if (descending) {
                order = -1;
            }
        }
        
        public final int compare(Object a, Object b) {
            PackageInfo infoA = getPackageInfo(a); 
            PackageInfo infoB = getPackageInfo(b);
            int result = 0;
            if (infoA == null || infoB == null) {
            	result =  0;
            } else if(isSystemApp(infoA) && !isSystemApp(infoB)) {
            	result = -1;
            } else if (!isSystemApp(infoA) && isSystemApp(infoB)) {
            	result = 1;
            } else if (infoA.firstInstallTime > infoB.firstInstallTime) { 
            	result = 1;
            } else if (infoA.firstInstallTime < infoB.firstInstallTime) { 
            	result = -1;
            }
            return result * order;
        }
        
        private PackageInfo getPackageInfo(final Object a) {
            String packageName = null ;
            if (a == null) {
            	return null;
            }
            if (mPackageInfoCache.containsKey(a)) {
                return mPackageInfoCache.get(a);
            } 
            
            if (a instanceof AppWidgetProviderInfo){
            	packageName = ((AppWidgetProviderInfo) a).provider .getPackageName() ;
            } else if (a instanceof ResolveInfo) {
                packageName = ((ResolveInfo) a).activityInfo.packageName ;
            } else if (a instanceof String) {
            	packageName = (String)a;
            } else {
            	Log.e(TAG, "can't get PackageInfo for :" + a.getClass().getName());
            	return null;
            }
        
            if (packageName == null || packageName.isEmpty() || packageName.equals("")) {
                return null ;
            }
            try {
                PackageInfo pkgInfo = mPackageManager.getPackageInfo(packageName, 0);
                mPackageInfoCache.put(a, pkgInfo);
                return pkgInfo;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return  null ;
        }
    }
    
    /**
     * uninstall package silent by root
     * <ul>
     * <strong>Attentions:</strong>
     * <li>Don't call this on the ui thread, it costs some times.</li>
     * <li>You should add <strong>android.permission.UNINSTALL_PACKAGES</strong> in manifest, so no need to request root
     * permission, if you are system app.</li>
     * </ul>
     * 
     * @param context 
     * @param package name of the package
     * @return {@link PackageUtils#DELETE_SUCCEEDED} means uninstall success, other means failed. details see
     * {@link PackageUtils#INSTALL_FAILED_*}. same to {@link PackageManager#INSTALL_*}
     */
    public static boolean uninstallSilent(Context context, String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
    
        /**
        *if context is system app, don,t need root permission, but should add <uses-permission
        *android:name="android.permission.INSTALL_PACKAGES" /> in mainfest
        */
        //zwsun@lewatek.com PR69544 2015.02.09 start
        StringBuilder command = new StringBuilder().append("LD_LIBRARY_PATH=/vendor/lib64:/system/lib64:/vendor/lib:/system/lib pm uninstall ").append(packageName);
        CommandResult commandResult = ShellUtils.execCommand(command.toString(), !LauncherApplication.isSystemApp( ), true);
        if (commandResult.successMsg != null && (commandResult.successMsg.contains("Success") || commandResult.successMsg.contains("success"))) {
            return true;
        }

        Log.e(TAG, new StringBuilder().append("successMsg:").append(commandResult.successMsg).append(", ErrorMsg:").append(commandResult.errorMsg).toString());
        return false;
    }
    
    static String getInstallLocationParams() {
        //zwsun@lewatek.com PR69544 2015.02.09 start
        CommandResult commandResult = ShellUtils.execCommand("LD_LIBRARY_PATH=/vendor/lib64:/system/lib64:/vendor/lib:/system/lib pm get-install-location", false, true);
        if (commandResult.result == 0 && commandResult.successMsg != null && commandResult.successMsg.length() > 0) {
            String location = commandResult.successMsg.substring(0, 1);
            if ("1".equals(location)) { // APP_INSTALL_INTERNAL
                return "-f";
            } else if ("2".equals(location)) {  // APP_INSTALL_EXTERNAL
                return "-s";
            }
        }
        return "";
    }
    
    public static ArrayList<String> mDownloadedPackages = new ArrayList<String>();
    public static List<String> mAddedPackages = Collections.synchronizedList(new ArrayList<String>());
    public static FolderInfo isRecommendPackage(Context context, ApplicationInfo info) {
        ArrayList<String> packages = Utilities.mDownloadedPackages;
        String packageName = info.componentName.getPackageName();
        boolean isRecommended = false;
        for (String pkg : packages) {
            if (pkg.equals(packageName)) {
                isRecommended = true;
                break;
            }
        }
        if (isRecommended) {
            return LauncherModel.getFolderbyPackageName(context, packageName);
        }
        return null;
    }

    public static final boolean install(Context context, String filePath, String packageName) {
        if (filePath == null) {
            return false;
        }
        mDownloadedPackages.add(packageName);
        if (LauncherApplication.isSystemApp() || ShellUtils.checkRootPermission()) {
            return installSilent(context, filePath, " -r " + getInstallLocationParams());
        }
        return installNormal(context, filePath);
    }

    public static boolean installNormal(Context context, String filePath) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        File file = new File(filePath);
        if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
            return false;
        }
        i.setDataAndType(Uri.parse("file://" + filePath), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
        return true;
    }

    public static boolean installSilent(Context context, String filePath, String location) {
        //zwsun@lewatek.com PR69544 2015.02.09 start
        StringBuilder command = new StringBuilder()
                .append("LD_LIBRARY_PATH=/vendor/lib64:/system/lib64:/vendor/lib:/system/lib pm install ")
                .append(location == null ? "" : location).append(" ")
                .append(filePath.replace(" ", "\\ "));
        CommandResult commandResult = ShellUtils.execCommand(command.toString(), !LauncherApplication.isSystemApp(), true);
        if (commandResult.successMsg != null && (commandResult.successMsg.contains("Success") 
                || commandResult.successMsg.contains("success"))) {
            Log.e(TAG, "installSilent success!");
            return true;
        }
        Log.e(TAG, "installSilent failed :"+ commandResult.errorMsg);
        return false;
    }
    
    public static boolean isOnSDCardOrDisabled(PackageManager pm, ComponentName cn) {
        android.content.pm.ApplicationInfo appInfo;
        if (pm == null || cn == null) {
            return false;
        }
        try {
            appInfo = pm.getApplicationInfo(cn.getPackageName(), PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_DISABLED_COMPONENTS);
            if (appInfo != null && (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                return true;
            } else {
                ActivityInfo activityInfo = pm.getActivityInfo(cn, PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_DISABLED_COMPONENTS);
                Log.i(TAG, "enabled:"+ activityInfo.enabled + " flag:"+Integer.toHexString(appInfo.flags) + " info:"+appInfo);
                if (activityInfo != null && !activityInfo.enabled) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            
    	}
    	return false;
    }
    
    public static boolean isAppAlreadyInstalled(Context context, String pkgName) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (NameNotFoundException e) {
            packageInfo = null;
        }
        return packageInfo != null;
    }
    
    public static int getNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            return info.getType();
        }
        return -1;
    }

    //for new app tip-lqwang-add begin
    public static void updateNewAddSymbol(View v,String pkgName){
        if(v instanceof ShortcutIcon){
            ShortcutIcon icon = ((ShortcutIcon)v);
            if(icon.mInfo.isNewAdded){
                icon.mInfo.isNewAdded = false;
                icon.setIsNewAdded(false);
                LauncherModel.removeNewAdded(v.getContext(),pkgName);
            }
        }
    }
    //for new app tip-lqwang-add end

    public static int getResourceId(Context context, String type , String name) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    //lqwang - pr962172 - add begin
    public static void startActivitySafely(Context context,Intent intent){
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }
    //lqwang - pr962172 - add end
}
