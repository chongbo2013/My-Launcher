package com.lewa.launcher.preference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.util.Log;

public class OEMAppTypeProvider {
    private static HashMap<String, String> mAppTypeNameMap;
    private static HashMap<String, String> mAppNameTypeMap;
    private static final String TAG = "ActivityTypeProvider";
    private static final String SHARED_PREFS_NAME = "app_type_prefs";
    private static final String KEY_APP_TYPE_SAVED = "is_save_app_type";
    private static Boolean mAppTypeSaved = null;

    public static ComponentName getComponentNameFromString(String compactName) {
        if (compactName == null) {
            return null;
        }
        String[] names = compactName.split("/");
        if (names.length != 2) {
            Log.e(TAG, "ERROR! getComponentNameFromString() invalid parama:" + compactName);
            return null;
        }
        if (names[0] == null || names[1] == null) {
            Log.e(TAG, "ERROR! getComponentNameFromString() invalid parama:" + compactName);
            return null;
        }
        return new ComponentName(names[0], names[1]);
    }

    private static boolean recognizeAppType(List<ResolveInfo> activityList,
            ArrayList<String> nameList, String moduleName) {
        for (ResolveInfo info : activityList) {
            for (String name : nameList) {
                if (info.activityInfo != null && info.activityInfo.name.equals(name)
                   || info.activityInfo.packageName.equals(name)) {
                    mAppTypeNameMap.put(moduleName,
                            new StringBuilder(info.activityInfo.packageName)
                                    .append('/').append(info.activityInfo.name).toString());
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAppTypeSaved(Context context) {
        if (mAppTypeSaved == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            mAppTypeSaved = sharedPrefs.getBoolean(SHARED_PREFS_NAME, false);
        }
        return mAppTypeSaved;
    }

    public static String getAppTypeByName(Context context, String packageName,
            String name) {
        if (isAppTypeSaved(context)) {
            if (mAppNameTypeMap == null) {
                loadMap(context);
            }
        } else {
            saveMap(context);
        }
        String compactName = new StringBuilder(packageName).append('/')
                .append(name).toString();
        return mAppNameTypeMap.get(compactName);
    }

    public static String getAppNameByType(Context context, String appType) {
        if (isAppTypeSaved(context)) {
            if (mAppTypeNameMap == null) {
                loadMap(context);
            }
        } else {
            saveMap(context);
        }
        return mAppTypeNameMap.get(appType);
    }

    public static ComponentName getAppComponentNameByType(Context context,
            String appType) {
        if (isAppTypeSaved(context)) {
            if (mAppTypeNameMap == null) {
                loadMap(context);
            }
        } else {
            saveMap(context);
        }
        String compactName = mAppTypeNameMap.get(appType);
        return getComponentNameFromString(compactName);

    }

    private static void loadMap(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mAppTypeSaved = sharedPrefs.getBoolean(KEY_APP_TYPE_SAVED, false);
        Map<String, ?> map = sharedPrefs.getAll();
        mAppNameTypeMap = new HashMap<String, String>();
        mAppTypeNameMap = new HashMap<String, String>();
        for (Entry<String, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof String) {
                mAppNameTypeMap.put((String) entry.getValue(), entry.getKey());
                mAppTypeNameMap.put(entry.getKey(), (String) entry.getValue());
            }
        }
    }

    private static void saveMap(Context context) {
        if (mAppTypeNameMap == null) {
            mAppTypeNameMap = new HashMap<String, String>();
        }
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.LAUNCHER");
        List<ResolveInfo> activityList = context.getPackageManager()
                .queryIntentActivities(intent, 0);
        ArrayList<String> arraylist = new ArrayList<String>();

        arraylist.add("com.lewa.PIM.contacts.activities.DialtactsActivity");
        arraylist.add("com.android.contacts.activities.TwelveKeyDialer");
        arraylist.add("com.android.contacts.TwelveKeyDialer");
        arraylist.add("com.android.phone");
        arraylist.add("com.android.dialer");//qhwu add for default phone
        arraylist.add("com.android.contacts.activities.DialtactsActivity");
        arraylist.add("com.android.providers.telephony");
        recognizeAppType(activityList, arraylist, "PHONE");
        arraylist.clear();

        arraylist.add("com.lewa.PIM.contacts.activities.ContactsEntryActivity");
        arraylist.add("com.android.contacts.activities.PeopleActivity");
        arraylist.add("com.android.contacts.DialtactsContactsEntryActivity");
        arraylist.add("com.android.contacts");
        arraylist.add("com.google.android.syncadapters.contacts");
        arraylist.add("com.android.contacts.activities.ContactsEntryActivity");
        recognizeAppType(activityList, arraylist, "CONTACTS");
        arraylist.clear();
        
        arraylist.add("com.android.browser.beta");
        arraylist.add("com.android.browser");
        arraylist.add("com.oupeng.browser");
        recognizeAppType(activityList, arraylist, "BROWSER");
        arraylist.clear();
        
        arraylist.add("com.lewa.PIM.contacts.activities.MessageActivity");
        arraylist.add("com.android.mms");
        arraylist.add("com.android.contacts.activities.MessageActivity");
        recognizeAppType(activityList, arraylist, "MMS");
        arraylist.clear();

        arraylist.add("com.motorola.Camera.Camera");
        arraylist.add("com.sec.android.app.camera.Camera");
        arraylist.add("com.android.camera.CameraEntry");
        arraylist.add("com.android.camera.CameraLauncher");
        arraylist.add("com.sonyericsson.android.camera");
        arraylist.add("com.lge.camera");
        arraylist.add("com.android.lgecamera");
        arraylist.add("com.android.hwcamera");
        arraylist.add("com.mediatek.camera");
        arraylist.add("com.baidu.camera");
        arraylist.add("com.oppo.camera");
        arraylist.add("com.oppo.camera.OppoCamera");
        arraylist.add("com.oppo.camera.activity.CameraActivity");
        arraylist.add("com.tcl.mid.android.camera");
        arraylist.add("com.android.camera.Camera");
        arraylist.add("com.google.android.camera");
        arraylist.add("com.android.camera");
        arraylist.add("com.miui.camera");
        arraylist.add("com.miui.camera");
        recognizeAppType(activityList, arraylist, "CAMERA");
        arraylist.clear();

        arraylist.add("com.android.music.MusicBrowserActivity");
        arraylist.add("com.miui.player");
        arraylist.add("com.miui.music");
        arraylist.add("com.motorola.cmp");
        arraylist.add("com.motorola.motmusic");
        arraylist.add("com.motorola.blur.music");
        arraylist.add("com.sec.android.app.music");
        arraylist.add("com.lenovo.leos.lephone.music");
        arraylist.add("com.htc.music");
        arraylist.add("com.lge.music");
        arraylist.add("com.sonyericsson.music");
        arraylist.add("com.andrew.apollo");
        arraylist.add("com.android.mediacenter");
        arraylist.add("com.ting.mp3.android");
        arraylist.add("com.oppo.music");
        arraylist.add("com.airplayme.android.phone");
        arraylist.add("com.android.bbkmusic.MusicBrowserActivity");
        arraylist.add("com.gwsoft.imusic.controller");
        arraylist.add("com.google.android.music");
        arraylist.add("com.android.music");
        recognizeAppType(activityList, arraylist, "MUSIC");
        arraylist.clear();

        arraylist.add("com.lenovomobile.deskclock");
        arraylist.add("com.sec.android.app.clockpackage");
        arraylist.add("com.htc.android.worldclock.WorldClockTabControl");
        arraylist.add("com.lge.alarm");
        arraylist.add("com.lge.clock");
        arraylist.add("com.android.alarmclock.AlarmClock");
        arraylist.add("com.motorola.blur.alarmclock");
        arraylist.add("com.sonyericsson.organizer.deskclock.DeskClock");
        arraylist.add("com.baidu.baiduclock");
        arraylist.add("zte.com.cn.alarmclock");
        arraylist.add("com.leadcore.clock");
        arraylist.add("com.android.BBKClock.Timer");
        arraylist.add("com.android.alarmclock");
        arraylist.add("com.android.deskclock.DeskClock");
        arraylist.add("com.android.deskclock");
        recognizeAppType(activityList, arraylist, "CLOCK");
        arraylist.clear();

        arraylist.add("com.when.android.calendar365.CalendarMain");
        arraylist.add("com.motorola.calendar");
        arraylist.add("com.lenovo.app.Calendar");
        arraylist.add("com.htc.calendar");
        arraylist.add("com.bbk.calendar.MainActivity");
        arraylist.add("com.yulong.android.calendar");
        arraylist.add("com.google.android.syncadapters.calendar");
        arraylist.add("com.android.providers.calendar");
        arraylist.add("com.android.calendar");
        arraylist.add("com.google.android.calendar");
        recognizeAppType(activityList, arraylist, "CALENDAR");
        arraylist.clear();

        arraylist.add("com.sec.android.app.calculator.Calculator");
        arraylist.add("com.sec.android.app.calculator");
        arraylist.add("com.sec.android.app.popupcalculator");
        arraylist.add("com.baidu.calculator2");
        arraylist.add("com.android.bbkcalculator.Calculator");
        arraylist.add("my.android.calc");
        arraylist.add("com.android.calculator2");
        arraylist.add("com.android.calculator");
        recognizeAppType(activityList, arraylist, "CALCULATOR");
        arraylist.clear();
        
        arraylist.add("com.lewa.gallery3d");
        arraylist.add("com.cooliris.media");
        arraylist.add("com.cooliris.media.Gallery");
        arraylist.add("com.motorola.blurgallery");
        arraylist.add("com.motorola.motgallery");
        arraylist.add("com.motorola.cgallery.Dashboard");
        arraylist.add("com.cooliris.media.RenderView");
        arraylist.add("com.htc.album");
        arraylist.add("com.sonyericsson.gallery");
        arraylist.add("com.sonyericsson.album");
        arraylist.add("com.sec.android.gallery3d");
        arraylist.add("com.motorola.gallery");
        arraylist.add("com.baidu.gallery3D.media");
        arraylist.add("com.oppo.cooliris.media");
        arraylist.add("com.oppo.gallery3d");
        arraylist.add("com.htc.album.AlbumTabSwitchActivity");
        arraylist.add("com.android.camera.GalleryPicker");
        arraylist.add("com.google.android.gallery3d");
        arraylist.add("com.android.gallery3d");
        arraylist.add("com.android.gallery");
        recognizeAppType(activityList, arraylist, "GALLERY");
        arraylist.clear();

        arraylist.add("com.android.settings");
        arraylist.add("com.android.settings.MiuiSettings");
        recognizeAppType(activityList, arraylist, "SETTING");
        arraylist.clear();

        // arraylist.add("com.android.camera.VideoCamera");
        // arraylist.add("com.android.camera.CamcorderEntry");
        // arraylist.add("com.motorola.Camera.Camcorder");
        // recognizeAppType(activityList, arraylist, "VIDEOCAMERA");
        // arraylist.clear();

        arraylist.add("com.android.music.VideoBrowserActivity");
        arraylist.add("com.htc.album.AllVideos");
        arraylist.add("com.sec.android.app.videoplayer.activity.VideoList");
        arraylist.add("com.cooliris.video.media.Gallery");
        arraylist.add("com.android.gallery3d.app.VideoGallery");
        arraylist.add("com.mediatek.videoplayer.MovieListActivity");
        arraylist.add("com.mediatek.videoplayer.BootActivity");
        arraylist.add("com.sonyericsson.fbmediadiscovery.MediaLoginActivity");
        arraylist.add("com.sonyericsson.video");
        arraylist.add("com.sec.android.app.videoplayer.activity.MainTab");
        arraylist.add("com.leadcore.videoplayer.VideoPlayerActivity");
        arraylist.add("com.zte.videoplayer.VideoBrowserActivity");
        arraylist.add("com.lenovo.leos.lemediacenter.LeMediaCenterActivity");
        arraylist.add("com.htc.china.videos");
        arraylist.add("com.lge.videoplayer");
        arraylist.add("com.baidu.videoplayer");
        arraylist.add("com.yulong.android.medialibrary");
        arraylist.add("com.moly.videoplayer");
        arraylist.add("com.android.video.VideoListActivity");
        arraylist.add("com.android.VideoPlayer.VideoPlayer");
        arraylist.add("com.android.VideoPlayer");
        recognizeAppType(activityList, arraylist, "VIDEO");
        arraylist.clear();

        arraylist.add("com.android.quicksearchbox.SearchActivity");
        arraylist.add("com.google.android.googlequicksearchbox.SearchActivity");
        recognizeAppType(activityList, arraylist, "QUICK_SEARCH_BOX");
        arraylist.clear();

        arraylist.add("com.android.compass.CompassActivity");
        arraylist.add("com.miui.compass.CompassActivity");
        arraylist.add("com.oppo.compass.flat.FlatCompass");
        arraylist.add("com.oppo.compass");
        arraylist.add("com.yulong.android.compass");
        arraylist.add("jlzn.com.android.compass");
        recognizeAppType(activityList, arraylist, "COMPASS");
        arraylist.clear();

        arraylist.add("com.android.soundrecorder.SoundRecorder");
        arraylist.add("com.motorola.soundrecorder");
        arraylist.add("com.htc.soundrecorder.SoundRecorderBG");
        arraylist.add("com.sec.android.app.voicerecorder");
        arraylist.add("com.android.soundrecorder.MzRecorderActivity");
        arraylist.add("com.android.bbksoundrecorder.SoundRecorder");
        arraylist.add("com.leos.soundrecorder");
        arraylist.add("com.yulong.android.soundrecorder");
        arraylist.add("com.lge.voicerecorder");
        arraylist.add("oppo.multimedia.soundrecorder");
        arraylist.add("com.oppo.soundrecorder.RecorderActivity");
        recognizeAppType(activityList, arraylist, "SOUND_RECORDER");
        arraylist.clear();

        arraylist.add("com.android.fileexplorer.FileExplorerTabActivity");
        arraylist.add("com.huawei.hidisk.Main");
        arraylist.add("com.motorola.filemanager.FileManager");
        arraylist.add("com.fihtdc.filemanager");
        arraylist.add("com.mediatek.filemanager");
        arraylist.add("com.sec.android.app.myfiles.MainActivity");
        arraylist.add("com.android.filemanager.FileManager");
        arraylist.add("com.meizu.filemanager.managefile.FileManagerActivity");
        arraylist.add("com.lenovo.FileBrowser.FileBrowser");
        arraylist.add("com.lenovo.leos.filebrowser");
        arraylist.add("zte.com.cn.filer.FilerActivity");
        arraylist.add("com.oppo.filemanager");
        arraylist.add("android.dopod.FileManager");
        arraylist.add("com.yulong.android.filebrowser");
        arraylist.add("com.gionee.filemanager");
        arraylist.add("com.tcl.File_Manager");
        arraylist.add("com.android.filemanager.FileManagerActivity");
        arraylist.add("org.openintents.filemanager.FileManagerActivity");
        recognizeAppType(activityList, arraylist, "FILE_EXPLORER");
        arraylist.clear();

        // arraylist.add("com.android.vending.AssetBrowserActivity");
        // recognizeAppType(activityList, arraylist, "GOOGLE_PLAY");
        // arraylist.clear();

        arraylist.add("com.android.voicedialer.VoiceDialerActivity");
        recognizeAppType(activityList, arraylist, "VOICE_DIALER");
        arraylist.clear();

        arraylist.add("com.android.email.activity.Welcome");
        arraylist.add("com.htc.android.mail.MailListTab");
        arraylist.add("com.htc.android.mail.MultipleActivitiesMain");
        arraylist.add("com.motorola.blur.email.mailbox.ViewFolderActivity");
        arraylist.add("com.android.email.activity.EmailActivity");
        recognizeAppType(activityList, arraylist, "EMAIL");
        arraylist.clear();

        // arraylist.add("com.google.android.gm.ConversationListActivityGmail");
        // recognizeAppType(activityList, arraylist, "G_MAIL");
        // arraylist.clear();
        //
        // arraylist.add("com.google.android.talk.SigningInActivity");
        // recognizeAppType(activityList, arraylist, "G_TALK");
        // arraylist.clear();
        //
        // arraylist.add("com.google.android.maps.MapsActivity");
        // recognizeAppType(activityList, arraylist, "MAPS");
        // arraylist.clear();
        //
        // arraylist.add("com.google.android.maps.LatitudeActivity");
        // recognizeAppType(activityList, arraylist, "LATITUDE");
        // arraylist.clear();
        //
        // arraylist.add("com.google.android.maps.PlacesActivity");
        // recognizeAppType(activityList, arraylist, "LOCAL_SEARCH");
        // arraylist.clear();
        // arraylist.add("com.google.android.maps.driveabout.app.DestinationActivity");
        // recognizeAppType(activityList, arraylist, "GPS");
        // arraylist.clear();

        // arraylist.add("com.google.android.apps.genie.geniewidget.activities.NewsActivity");
        // arraylist.add("com.miui.weather2");
        // arraylist.add("com.lenovo.weather");
        // arraylist.add("com.lenovo.android.LenovoSinaWeather");
        // arraylist.add("com.huawei.android.totemweather");
        // arraylist.add("com.android.weather");
        // arraylist.add("com.htc.Weather");
        // arraylist.add("com.oppo.weather");
        // arraylist.add("com.gionee.aora.weather");
        // arraylist.add("com.icoolme.android.weather");
        // arraylist.add("com.tclcom.weatherassistant");
        // arraylist.add("com.baidu.weather");
        // recognizeAppType(activityList, arraylist, "GENIEWIDGET");
        // arraylist.clear();

        // arraylist.add("com.google.android.carhome.CarHome");
        // recognizeAppType(activityList, arraylist, "CARHOME");
        // arraylist.clear();
        //
        // arraylist.add("com.google.android.apps.plus.phone.HomeActivity");
        // recognizeAppType(activityList, arraylist, "PLUS");
        // arraylist.clear();

        arraylist.add("com.android.updater.MainActivity");
        arraylist.add("com.sonyericsson.updatecenter");
        arraylist.add("com.oppo.ota.activity.HomeActivity");
        arraylist.add("com.oppo.ota");
        arraylist.add("com.yulong.android.ota");
        arraylist.add("gn.com.android.update");
        arraylist.add("com.mediatek.updatesystem");
        arraylist.add("com.huawei.android.hwouc.ui.activities.MainEntranceActivity");
        recognizeAppType(activityList, arraylist, "UPDATER");
        arraylist.clear();

        arraylist.add("com.android.providers.downloads.ui.DownloadList");
        arraylist.add("com.android.providers.downloads.ui.DownloadsListTab");
        recognizeAppType(activityList, arraylist, "DOWNLOADS");
        arraylist.clear();

        // arraylist.add("com.android.videoeditor.ProjectsActivity");
        // arraylist.add("com.cyberlink.MovieEditor.ProjectPicker");
        // arraylist.add("com.sec.android.app.ve.activity.ProjectListActivity");
        // recognizeAppType(activityList, arraylist, "VIDEO_EDITOR");
        // arraylist.clear();

        arraylist.add("com.sec.android.app.fm");
        arraylist.add("com.samsung.app.fmradio");
        arraylist.add("com.broadcom.bt.app.fm.rx.FmRadio");
        arraylist.add("com.mediatek.FMRadio.FMRadioActivity");
        arraylist.add("com.quicinc.fmradio.FMRadio");
        arraylist.add("com.lenovo.leos.fmradio.RadioActivity");
        arraylist.add("com.huawei.android.FMRadio");
        arraylist.add("com.motorola.motofmradio");
        arraylist.add("com.sonyericsson.fmradio");
        arraylist.add("com.fihtdc.fmradio");
        arraylist.add("com.oem.fmradio");
        arraylist.add("com.miui.fmradio");
        arraylist.add("com.lge.fmradio");
        arraylist.add("com.htc.fm.FMRadio");
        arraylist.add("com.htc.fm.activity.FMRadioMain");
        arraylist.add("com.broadcom.bt.app.fm");
        arraylist.add("com.baidu.fm");
        recognizeAppType(activityList, arraylist, "FM_RADIO");
        arraylist.clear();

        arraylist.add("com.leadcore.backup.BackupMainActivity");
        arraylist.add("com.motorola.sdcardbackuprestore");
        arraylist.add("com.sonyericsson.vendor.backuprestore");
        arraylist.add("com.lenovo.bakrestore");
        arraylist.add("com.lenovo.BackupRestore");
        arraylist.add("com.huawei.KoBackup");
        arraylist.add("com.yulong.android.backup");
        arraylist.add("com.zte.backup.mmi");
        recognizeAppType(activityList, arraylist, "BACKUP");
        arraylist.clear();

        arraylist.add("com.sec.android.app.memo");
        arraylist.add("com.leadcore.notepad.MainActivity");
        arraylist.add("com.example.android.notepad");
        arraylist.add("com.sonyericsson.notes");
        arraylist.add("com.android.notes.Notes");
        arraylist.add("com.android.notepad");
        arraylist.add("com.lenovo.notepad");
        arraylist.add("com.htc.notes");
        arraylist.add("com.marigold.android.notes");
        arraylist.add("com.snda.inote.lenovo");
        arraylist.add("zte.com.cn.notepad");
        arraylist.add("com.tcl.memo");
        arraylist.add("com.moly.note");
        arraylist.add("com.meizu.notepaper");
        arraylist.add("com.nearme.note.view.SetAndCheckPasswordActivity");
        arraylist.add("com.nearme.note");
        recognizeAppType(activityList, arraylist, "NOTE");

        SharedPreferences sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME,
                Context.MODE_PRIVATE);

        android.content.SharedPreferences.Editor editor = sharedPrefs.edit();
        mAppNameTypeMap = new HashMap<String, String>();
        for (Entry<String, String> entry : mAppTypeNameMap.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
            mAppNameTypeMap.put(entry.getValue(), entry.getKey());
        }
        mAppTypeSaved = true;
        editor.putBoolean(KEY_APP_TYPE_SAVED, true);
        editor.commit();
    }
}
