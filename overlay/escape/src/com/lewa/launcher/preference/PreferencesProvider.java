package com.lewa.launcher.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import lewa.provider.ExtraSettings;

import com.lewa.launcher.LauncherApplication;
import com.lewa.launcher.R;
import com.lewa.launcher.ShakeListener;
import com.lewa.launcher.Workspace;

public final class PreferencesProvider {
    private static SharedPreferences sp;
    public static final String CHANGED = "prefs_changed";
    public static final String PASSWORD = "hidden_password";
    public static final String FIRST_TIME_USE = "first_use_hidden";
    public static final String DEFAULT_SCREEN_LAYOUT = "4|4";
    public static final String LARGE_SCREEN_LAYOUT = "4|5";
    public static final String EX_LARGE_SCREEN_LAYOUT = "5|5";
    public static final String TABLET_SCREEN_LAYOUT = "4|6";
	//zwsun@letek.com 20150108 start
    public static final int DEFAULT_SCREEN = 0;
	public static boolean PIFLOW = false;
	//zwsun@letek.com 20150108 end
    public static final int MIN_SCREEN_COUNT = 1;
    public static final int MAX_SCREEN_COUNT = 100;
  //yixiao@lewatek.com A:piflow 2015.1.9
    public static String PIFLOW_OPENED = "PIFLOW_OPENED";
    public static SharedPreferences getSharedPreferences(Context context) {
        if (sp == null) {
            sp = PreferenceManager.getDefaultSharedPreferences(context);
			//zwsun@letek.com 20150108 start
            PIFLOW = context.getResources().getBoolean(R.bool.config_piflow_default);
            //zwsun@letek.com 20150108 end
	    }
        return sp;
    }
    
    public static boolean isFirstRun(Context context, String key) {
        SharedPreferences sp = getSharedPreferences(context);
        boolean isFirstRun = sp.getBoolean(key, true);
        if (isFirstRun) {
            sp.edit().putBoolean(key, false).commit();
        }
        return isFirstRun;
    }

    public static int getScreenCount(Context context) {
        return getSharedPreferences(context).getInt("screen_count", MIN_SCREEN_COUNT);
    }

    public static void setScreenCount(Context context, int screenCount) {
        getSharedPreferences(context).edit().putInt("screen_count", screenCount).commit();
    }
    
    public static int getDefaultScreen(Context context) {
        return getSharedPreferences(context).getInt("default_screen", DEFAULT_SCREEN);
    }
    
    public static void setDefaultScreen(Context context,int screen) {
        getSharedPreferences(context).edit().putInt("default_screen", screen).commit();
    }
    
	//zwsun@letek.com 20150108 start
    public static boolean piflowEnabled(Context context) {
        return getSharedPreferences(context).getBoolean("piflow", PIFLOW);
    }
	//zwsun@letek.com 20150108 end
	 
    public static float getIconSize(Context context) {
        String value = getSharedPreferences(context).getString("pref_icon_size", "1.0f");
        float tempValue = 1.0f;
        try {
            tempValue = Float.parseFloat(value);
        } catch (NumberFormatException e) {
            
        }
        String scrLayout = getSharedPreferences(context).getString("pref_screen_layout", LARGE_SCREEN_LAYOUT);
        if (EX_LARGE_SCREEN_LAYOUT.equals(scrLayout)) {
            return tempValue == 1.0f ? 0.85f : 0.75f;
        }
        return tempValue;
    }
    
    public static String getDefaultScreenLayoutValue(Context context) {
        String defVal;
        if (LauncherApplication.isTablet(context)) {
            defVal = TABLET_SCREEN_LAYOUT;
        } else if (LauncherApplication.isNormalScreen(context)) {
            defVal = DEFAULT_SCREEN_LAYOUT;
        } else if (LauncherApplication.isExLargeScreen(context)) {
            defVal = LARGE_SCREEN_LAYOUT;   // Now ex-large screen also use 4*5 as default
        } else {
            defVal = LARGE_SCREEN_LAYOUT;  
        }
        return defVal;
    }

    public static int getCellCountX(Context context) {
    	String defVal = getDefaultScreenLayoutValue(context);
        String[] values = getSharedPreferences(context).getString("pref_screen_layout", defVal).split("\\|");
        try {
            return Integer.parseInt(values[0]);
        } catch (NumberFormatException e) {
            return 4;
        }
    }
    
    public static int getCellCountY(Context context) {
        String defVal = getDefaultScreenLayoutValue(context);
        String[] values = getSharedPreferences(context).getString("pref_screen_layout", defVal).split("\\|");
        try {
            return Integer.parseInt(values[1]);
        } catch (NumberFormatException e) {
            return 4;
        }
    }
    
    public static boolean getScrollWallpaper(Context context) {
        return getSharedPreferences(context).getBoolean("wallpaper_scrolling", true);
    }

    public static boolean getSwipeUpDown(Context context) {
        return getSharedPreferences(context).getBoolean("action_updown", true);
//        final int value = Settings.System.getInt(context.getContentResolver(), ExtraSettings.System.LAUNCHER_ACTION_UP_DOWN, 1);
//        return (value == 0) ? false : true;
    }
    
    public static boolean isSupportShake(Context context) {
        return getSharedPreferences(context).getBoolean("shake_arrange_apps", false);
    }

    public static boolean isRecommendOn(Context context) {
        return getSharedPreferences(context).getBoolean("apps_recommend", context.getResources().getBoolean(R.bool.config_apps_recommend_default));
    }
    
    public static boolean isSmartSortOn(Context context) {
        return getSharedPreferences(context).getBoolean("apps_smart_sort", context.getResources().getBoolean(R.bool.config_apps_smart_default));
    }
    
    public static void setSmartSortOn(Context context, boolean on) {
        getSharedPreferences(context).edit().putBoolean("apps_smart_sort", on).commit();
    }

    public static boolean isRecommendOrSmartSortOn(Context context){
        return isRecommendOn(context) || isSmartSortOn(context);
    }
    
    public static int getShakeDegree(Context context) {
        return getSharedPreferences(context).getInt("shake_degree", ShakeListener.DEFAULT_SHAKE_DEGREE);
    }
    
    public static void setShakeDegree(Context context, int degree) {
        getSharedPreferences(context).edit().putInt("shake_degree", degree).commit();
    }
    
    public static boolean getScreenCycle(Context context){
        return getSharedPreferences(context).getBoolean("screen_cycle", false);
    }

    public static float getAnimScale(Context context) {
        return getSharedPreferences(context).getFloat("anim_scale", 3.0f);
    }

    public static void setAnimScale(Context context, float scale) {
        getSharedPreferences(context).edit().putFloat("anim_scale", scale).commit();
    }

    public static int getAnimDuration(Context context) {
        return getSharedPreferences(context).getInt("anim_duration", 300);
    }

    public static void setAnimDuration(Context context, int duration) {
        getSharedPreferences(context).edit().putInt("anim_duration", duration).commit();
    }

    public static Workspace.TransitionEffect getTransitionEffect(Context context) {
        String def = context.getResources().getString(R.string.config_workspaceDefaultTransitionEffect);
        String val = getSharedPreferences(context).getString("pref_key_effects", def);
        return Workspace.TransitionEffect.valueOf(val);
    }
	
	 public static void setTransitionEffect(Context context,String value) {
       getSharedPreferences(context).edit().putString("pref_key_effects", value).commit();
    }
    
    public static void putStringValue(Context context,String key,String value){
        getSharedPreferences(context).edit().putString(key, value).commit();
    }
    
    public static String getStringValue(Context context,String key,String def){
    	return getSharedPreferences(context).getString(key, def);
    }

    public static void putFloatValue(Context context,String key,float value){
        getSharedPreferences(context).edit().putFloat(key,value).commit();
    }

    public static float getFloatValue(Context context,String key,float def){
        return getSharedPreferences(context).getFloat(key,def);
    }

    public static void removeValue(Context context,String key){
        getSharedPreferences(context).edit().remove(key).commit();
    }
}
