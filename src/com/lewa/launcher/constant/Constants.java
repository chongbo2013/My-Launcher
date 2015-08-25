package com.lewa.launcher.constant;

import android.os.Environment;

public class Constants {
    public static final String WALLPAPER_THUMB = "Thumbnailpath";
    public static final String WALLPAPER_ATTACHMENT = "Attachment";
    public static final String WALLPAPER_PACKAGENAME = "Packagename";

    public static final String UTF8 = "UTF-8";
    public static final String SDCARD_ROOT_PATH = Environment.getExternalStorageDirectory().getPath();
    public static final String WALLPAPER_PATH = "LEWA/theme/deskwallpaper";
    public static final String WALLPAPER_FULL_PATH = SDCARD_ROOT_PATH + "/LEWA/theme/deskwallpaper";
    public static final String JPEG = ".jpg";

    public static final int ACTION_DOWNLOAD_UPDATEING = 300;

    //for gridview animation
    public static final int ONLINE_PAGESIZE = 5;
    public static final int ANIM_OUT_DURATION = 800;
    public static final int ANIM_IN_DURATION = 110;
    public static final int ANIM_IN_DURATION_DELAY = 150;
    public static final int LOCAL_ANIM_COUNT = 5;

    public static final String NEWAPPSKEY = "new_add_apps";

    public static final int ENTER_FROM_LAUNCHER = 1;

    public static final String LAUNCHER_FIRST_RUN = "launcher_first_run";
    public static final int FIRST_RUN = 0 ;
    public static final int ALREADY_RUN = 1 ;
    //lqwang - PR63468 - modify begin
    public static final String ACTION_CHANGE_THEME = "com.lewa.intent.action.CHANGE_THEME";
    public static final String RECOMMEND_DOWNLOADING_KEY = "recommend_downloading";
    //lqwang - PR63468 - modify end
    public static final String WALLPAPER_FINALX = "wallpaper_finalX";
    public static final float WALLPAPER_DEFAULT_FINALX = -1;

    public static final String START_APPLICATION_ACTION = "com.lewa.perm.start_application";//lqwang - PR63179 - add

    public static final String EDITMODE_SETTING_KEY = "isEditMode";

    public static final int FULL_WIDGET_COUNT = 1;

    public static final String APPS_SORTED = "apps_sorted";
}
