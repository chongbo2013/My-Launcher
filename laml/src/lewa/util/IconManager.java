
package lewa.util;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import lewa.util.IIconManager;

/*
 * built into framework.jar
 *
 */
public class IconManager {
    private final static String TAG = "IconManager";

    public static final String CUSTOMIZED_ICON_PATH = "/data/system/customized_icons/";

    private static final HashMap<String, WeakReference<Bitmap>> sIconCache = new HashMap<String, WeakReference<Bitmap>>();

    private static boolean FANCY_ICON = false;

    private final IIconManager mService;

    public IconManager() {
        mService = IIconManager.Stub.asInterface(ServiceManager.getService("iconmanager"));
    }

    public static void setEnableFancyDrawable(boolean enable) {
        FANCY_ICON = enable;
    }

    public void clearCustomizedIcons(String packageName) {
        try {
            mService.clearCustomizedIcons(packageName);
        } catch (RemoteException e) {
        }
    }

    public void clearMemoryCacheCustomizedIcons() {
        synchronized (sIconCache) {
            sIconCache.clear();
        }
    }
    public void reset() {
        try {
            mService.reset();
        } catch (RemoteException e) {
        }
    }

    public void checkModIcons() {
        try {
            mService.checkModIcons();
        } catch (RemoteException e) {
        }
    }

    public Drawable loadIcon(ResolveInfo ri) {
        try {
            String packageName;
            String className;
            if (ri.activityInfo != null) {
                packageName = ri.activityInfo.packageName;
                className = ri.activityInfo.name;
            } else if (ri.serviceInfo != null) {
                packageName = ri.serviceInfo.packageName;
                className = ri.serviceInfo.name;
            } else {
                return null;
            }
            if (FANCY_ICON) {
                String path = mService.getFancyIconRelativePath(packageName, className);
                if (path != null) {
                    Drawable d = getFancyIcon(path);
                    if (d != null)
                        return d;
                }
            }
            String key = packageName + className;
            BitmapDrawable d = getDrawableFromMemoryCache(key);
            if (d == null) {
                d = getCustomizedIconFromStaticCache(packageName, className, false);
                if (d == null && ri.getIconResource() == 0) {
                    d = getDrawableFromStaticCache("lewa.png");
                }
                if (d == null) {
                    Bitmap bmp = mService.loadIconByResolveInfo(ri);
                    if (null != bmp) {
                        d = getDrawble(bmp);
                    }
                }
                if (d != null) {
                    synchronized (sIconCache) {
                        sIconCache.put(key, new WeakReference<Bitmap>(d.getBitmap()));
                    }
                }
            }
            return d;
        } catch (Exception e) {
            Log.e(TAG, "Dead object in loadIcon", e);
        }
        return null;
    }

    public Drawable loadIcon(ApplicationInfo ai) {
        try {
            String packageName = ai.packageName;
            String className = ai.className;
            if (FANCY_ICON) {
                String path = mService.getFancyIconRelativePath(packageName, className);
                if (path != null) {
                    Drawable d = getFancyIcon(path);
                    if (d != null)
                        return d;
                }
            }
            String key = packageName + className;
            BitmapDrawable d = getDrawableFromMemoryCache(key);
            if (d == null) {
                d = getCustomizedIconFromStaticCache(packageName, className, true);
                if (d == null && ai.icon == 0) {
                    d = getDrawableFromStaticCache("lewa.png");
                }
                if (d == null) {
                    Bitmap bmp = mService.loadIconByApplcationInfo(ai);
                    if (null != bmp) {
                        d = getDrawble(bmp);
                    }
                    if (d != null) {
                        synchronized (sIconCache) {
                            sIconCache.put(key, new WeakReference<Bitmap>(d.getBitmap()));
                        }
                    }
                }
            }
            return d;
        } catch (Exception e) {
            Log.e(TAG, "Dead object in loadIcon", e);
        }
        return null;
    }

     public static BitmapDrawable getCustomizedIconFromStaticCache(String packageName,
            String className) {
        return getCustomizedIconFromStaticCache(packageName, className, true);
    }
     
    public static BitmapDrawable getCustomizedIconFromStaticCache(String packageName,
            String className, boolean usePackageName) {
        BitmapDrawable d = getDrawableFromStaticCache(getFileName(packageName, className));
        if (d == null && usePackageName)
            d = getDrawableFromStaticCache(packageName + ".png");
        if (d == null && className != null)
            d = getDrawableFromStaticCache(className.replace('.', '_') + ".png");
        if (d == null)
            d = getDrawableFromStaticCache(packageName.replace('.', '_') + ".png");
        return d;
    }

    private static BitmapDrawable getDrawableFromStaticCache(String filename) {
        String pathName = CUSTOMIZED_ICON_PATH + filename;
        File iconFile = new File(pathName);
        if (iconFile.exists()) {
            try {
                return getDrawble(BitmapFactory.decodeFile(pathName));
            } catch (OutOfMemoryError e) {
            } catch (Exception e) {
                iconFile.delete();
            }
        }
        return null;
    }

    private static String getFileName(String packageName, String className) {
        if (className == null)
            return packageName + ".png";
        if (className.startsWith(packageName))
            return className + ".png";
        else
            return packageName + '#' + className + ".png";
    }

    private static BitmapDrawable getDrawableFromMemoryCache(String name) {
        synchronized (sIconCache) {
            WeakReference<Bitmap> ref = sIconCache.get(name);
            if (ref != null)
                return getDrawble(ref.get());
        }
        return null;
    }

    private static BitmapDrawable getDrawble(Bitmap bitmap) {
        return bitmap == null ? null : new BitmapDrawable(bitmap);
    }

    private Drawable getFancyIcon(String name) {
        try {
            return (Drawable) Class.forName("lewa.laml.util.AppIconsHelper")
                    .getMethod("getIconDrawable", Context.class, String.class)
                    .invoke(null, ActivityThread.currentApplication(), name);
        } catch (Exception e) {
            Log.e(TAG, "getFancyIcon", e);
        }
        return null;
    }
}
