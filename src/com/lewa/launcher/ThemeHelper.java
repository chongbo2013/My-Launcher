
package com.lewa.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lewa.content.res.IconCustomizer;

public class ThemeHelper {
    private static final String ICONS_NAME = "icons";
    private static final String THEME_PATH = "data/system/face/icons";
    public static void checkTheme(Context context) {
        boolean system = LauncherApplication.isSystemApp();
        IconCustomizer.setContext(context);
        boolean customTheme = isCustomTheme();
        if (isVersionChanged(context) || (!isResourceExists(context)) || !customTheme) {
            IconCustomizer.clearCustomizedIcons(null);
//            if (!system) {
                extractThemeResources(context);
//            }
        }
    }

    private static void extractThemeResources(Context context) {
        extractAsset(context, ICONS_NAME, context.getFilesDir().getAbsolutePath() + '/'
                + ICONS_NAME);
    }

    private static boolean extractAsset(Context context, String assetName, String path) {
        InputStream is = null;
        OutputStream os = null;
        try {
            AssetManager assetManager = context.getAssets();
            is = assetManager.open(assetName);
            os = new FileOutputStream(path);
            int n;
            byte[] buf = new byte[4096];
            while ((n = is.read(buf)) >= 0) {
                os.write(buf, 0, n);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	try {
        		if (is != null) {
        			is.close();
        		}
        		if (os != null) {
        			os.close();
			    }
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        return false;
    }

    private static boolean isResourceExists(Context context) {
        return new File(context.getFilesDir().getAbsolutePath() + '/' + ICONS_NAME).exists();
    }

    private static boolean isVersionChanged(Context context) {
        final String TAG_VERSION = "version";
        boolean upgraded = false;
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            int curVersion = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
            int perVersion = sp.getInt(TAG_VERSION, -1);
            upgraded = curVersion != perVersion;
            if (upgraded)
                sp.edit().putInt(TAG_VERSION, curVersion).commit();
        } catch (Exception e) {
        }
        return upgraded;
    }
    
    private static boolean isCustomTheme(){
        return new File(THEME_PATH).exists();
    }
}
