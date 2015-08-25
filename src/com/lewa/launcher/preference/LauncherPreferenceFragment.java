package com.lewa.launcher.preference;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.lewa.launcher.Launcher;
import com.lewa.launcher.LauncherApplication;
import com.lewa.launcher.LauncherModel;
import com.lewa.launcher.Utilities;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.pulltorefresh.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;

import com.lewa.launcher.R;
/**
 * Created by lqwang on 15-4-3.
 */
public class LauncherPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private Context mContext;
    private SwitchPreference mKeepResident;
    private ListPreference mScreenLayout;
    private int mShakeProgress;
    private static final String KEEP_LAUNCHER_RESIDENT = "keep_launcher_resident";
    private AlertDialog mConfirmSortDialog;
    private ProgressDialog mLoadingDialog;
    private SwitchPreference mWallpaperScroll;
    private float wallpaperScrollFinalX;
    //zwsun@letek.com 20150108 start
    private SwitchPreference mPiflow = null;
    private int mDefaultPage;
    private Context applicationContext;
    private final int BADNETWORK = 0x123;
    Handler mHandler = new Handler(){
      public void handleMessage(android.os.Message msg) {
          switch (msg.what) {
        case BADNETWORK:
            if(mLoadingDialog.isShowing()){
                mLoadingDialog.dismiss();
                Toast.makeText(mContext, R.string.sort_app_message_timeout, 0).show();
            }
            break;
        }
      };  
    };
    //zwsun@letek.com 20150108 end
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        applicationContext = getActivity().getApplicationContext();
        mContext = getActivity();
        //lqwang - PR69928 - modify
        removeDisabledSettings();

        mScreenLayout = (ListPreference) findPreference("pref_screen_layout");
        initScreenLayout();

        mKeepResident = (SwitchPreference) findPreference("keep_resident");
        if (!isFeatrueKeepResidentEnabled()) {
            getPreferenceScreen().removePreference(mKeepResident);
        } else {
            int flag = Settings.System.getInt(getActivity().getContentResolver(), KEEP_LAUNCHER_RESIDENT, 0);
            mKeepResident.setChecked(flag != 0);
            mKeepResident.setOnPreferenceChangeListener(this);
        }
        //lqwang - PR64141 - add begin
        mWallpaperScroll = (SwitchPreference)findPreference("wallpaper_scrolling");
        mWallpaperScroll.setOnPreferenceChangeListener(this);
        wallpaperScrollFinalX = getActivity().getIntent().getFloatExtra(Constants.WALLPAPER_FINALX,Constants.WALLPAPER_DEFAULT_FINALX);
        //lqwang - PR64141 - add end

        //zwsun@letek.com 20150108 start
        mPiflow = (SwitchPreference) findPreference("piflow");
        if(mPiflow != null){
            mPiflow.setOnPreferenceChangeListener(this);
        }
        //zwsun@letek.com 20150108 end
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (key.equals("reset_launcher")) {
            resetLauncher();
            return true;
        } else if (key.equals("change_wallpaper")) {
            Intent intent = new Intent(Launcher.ACTION_SET_WALLPAPER);
            getActivity().sendBroadcast(intent);
            return true;
        } else if (key.equals("apps_smart_sort")) {
            mConfirmSortDialog = createConfirmDialog().create();
            mConfirmSortDialog.show();
            return true;
        }
        else if (key.equals("shake_degree")) {
            View view = View.inflate(mContext,R.layout.shake_degree_set, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(view);
            SeekBar seek = (SeekBar) view.findViewById(R.id.shake_seekbar);
            seek.setMax(4);
            mShakeProgress = PreferencesProvider.getShakeDegree(getActivity());
            seek.setProgress(mShakeProgress - 10);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    mShakeProgress = progress + 10;
                }
            });

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    PreferencesProvider.setShakeDegree(mContext, mShakeProgress);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            builder.show();
        }
        return false;
    }

    //lqwang - PR69928 - modify begin
    private void removeDisabledSettings() {
        try {
            String[] key = getResources().getStringArray(R.array.config_settings_key);
            String[] switchs =  getResources().getStringArray(R.array.config_settings_show);
            if(key.length != switchs.length){
                return;
            }
            for(int i = 0 ; i < key.length ; i ++){
                Preference preference = findPreference(key[i]);
                if(!Boolean.valueOf(switchs[i])){
                    getPreferenceScreen().removePreference(preference);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //lqwang - PR69928 - modify end


    void initScreenLayout() {
        boolean isScreenExLarge = LauncherApplication.isExLargeScreen(applicationContext);
        int entriesId = isScreenExLarge ? R.array.ex_large_screen_layout : R.array.large_screen_layout;
        int entriesValueId = isScreenExLarge ? R.array.ex_large_screen_layout_value : R.array.large_screen_layout_value;
        mScreenLayout.setEntries(getResources().getStringArray(entriesId));
        mScreenLayout.setEntryValues(getResources().getStringArray(entriesValueId));
        if (mScreenLayout.getValue() == null) {
            mScreenLayout.setValue(PreferencesProvider.getDefaultScreenLayoutValue(applicationContext));
        }
        mScreenLayout.setSummary(mScreenLayout.getEntry());
        mScreenLayout.setOnPreferenceChangeListener(this);
    }

    private boolean isFeatrueKeepResidentEnabled() {
        try {
            Class<?> amClz = Class.forName("com.android.server.am.ActivityManagerService");
            Field f = amClz.getDeclaredField("ENABLE_FEATRUE_KEEP_LAUNCHER_RESIDENT");
            f.setAccessible(true);
            Boolean b = (Boolean) (f.get(null));
            return b;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(mScreenLayout)) {
            updateSummary((ListPreference) preference, newValue);
        } else if (preference.equals(mKeepResident)) {
            Settings.System.putInt(getActivity().getContentResolver(), KEEP_LAUNCHER_RESIDENT, ((Boolean) newValue) ? 1 : 0);
            //zwsun@letek.com 20150108 start
        } else if(mPiflow != null && preference.equals(mPiflow)){
            //yixiao@lewatek.com add for piflow 20141223
            mDefaultPage = PreferencesProvider.getDefaultScreen(mContext);
            boolean enable = (Boolean) newValue;
            mPiflow.setChecked(enable);

            if (enable) {
                PreferencesProvider
                        .setDefaultScreen(mContext, mDefaultPage + 1);
                PreferencesProvider.getSharedPreferences(mContext).edit()
                        .putBoolean(PreferencesProvider.PIFLOW_OPENED, true).commit();
            } else {
                LauncherModel.deleteCustromScreen(applicationContext, 0);
                PreferencesProvider.getSharedPreferences(mContext).edit()
                        .putBoolean(PreferencesProvider.PIFLOW_OPENED, false).commit();
                // LauncherModel.updateScreenItem(this);
                // yixiao@lewatek.com add for piflow 20141223
                PreferencesProvider
                        .setDefaultScreen(mContext, Math.max(0, mDefaultPage - 1));
            }

            SharedPreferences prefs = PreferencesProvider
                    .getSharedPreferences(mContext);
            prefs.edit().putBoolean(PreferencesProvider.CHANGED, true).commit();
            //zwsun@letek.com 20150108 end
        }
        //lqwang - PR64141 - modify begin
        else if (preference.equals(mWallpaperScroll)) {
            updateWallpaperFinalX(newValue);
        }
        //lqwang - PR64141 - modify end
        return true;
    }


    //lqwang - PR64141 - add begin
    private void updateWallpaperFinalX(Object newValue) {
        boolean allow_scroll = (Boolean)newValue;
        if(!allow_scroll){
            PreferencesProvider.putFloatValue(mContext,Constants.WALLPAPER_FINALX,wallpaperScrollFinalX);
        }else{
            PreferencesProvider.removeValue(mContext,Constants.WALLPAPER_FINALX);
        }
    }
    //lqwang - PR64141 - add end

    @Override
    public void onPause() {
        super.onPause();
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }

    private void showProgress() {
        if (mLoadingDialog == null) {
            mLoadingDialog = ProgressDialog.show(mContext, null, getString(R.string.loading_prompt), true, false);
            mLoadingDialog.setCancelable(true);
        }
        try {
            mLoadingDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private AlertDialog.Builder createConfirmDialog() {
        final LauncherApplication application = (LauncherApplication) applicationContext;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = LayoutInflater.from(mContext);
        LinearLayout mConfirmSortLayout = (LinearLayout) inflater.inflate(
                R.layout.smart_sort_layout, null);
        TextView sortLayout = (TextView) mConfirmSortLayout.findViewById(R.id.smart_sort);
        TextView restoreLayout = (TextView) mConfirmSortLayout.findViewById(R.id.restore_sort);
        //lqwang - pr962152 - modify begin
//        final boolean isEmpty = application.mModel.isShortcutBackupEmpty();
        final boolean isSorted = PreferencesProvider.getSharedPreferences(mContext).getBoolean(Constants.APPS_SORTED,false);
        if (!isSorted) {
        //lqwang - pr962152 - modify end
            int color = mContext.getResources().getColor(R.color.text_disable);
            restoreLayout.setTextColor(color);
        }

        sortLayout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (mConfirmSortDialog != null) {
                    mConfirmSortDialog.dismiss();
                    if (!application.mModel.isInfoDownloaded(applicationContext)) {
                        application.setWaitToSort(true);//lqwang - pr962182 - modify
                        if(Utilities.getNetworkType(mContext) != -1){
                            showProgress();
                            new Timer().schedule(new TimerTask() {
                                
                                @Override
                                public void run() {
                                    // TODO Auto-generated method stub
                                    SharedPreferences sp = PreferencesProvider.getSharedPreferences(mContext);
                                    long lastUpdateTime = sp.getLong("last_get_category_time", 0);
                                    if(lastUpdateTime == 0){
                                        application.setWaitToSort(false);
                                        mHandler.sendEmptyMessage(BADNETWORK);
                                    }
                                    
                                }
                            }, 5000);
                        }
                        return;
                    }
                    application.mModel.startSortTask(applicationContext, 0x1);
                    showProgress();
                }
            }
        });
        restoreLayout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (mConfirmSortDialog != null && isSorted) {
                    mConfirmSortDialog.dismiss();
                    application.setWaitToSort(false);//lqwang - pr962182 - modify
                    application.mModel.startSortTask(applicationContext, 0x2);
                    showProgress();
                }
            }
        });
        builder.setView(mConfirmSortLayout);
        return builder;
    }


    private void resetLauncher() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.pref_title_resetlauncher)
                .setMessage(R.string.message_resetlauncher)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PreferencesProvider.getSharedPreferences(mContext).edit().clear().commit();
                                File dbFile = mContext.getDatabasePath("launcher.db");
                                if (dbFile.exists()) {
                                    dbFile.delete();
                                }
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                .create()
                .show();
    }

    void updateSummary(ListPreference preference, Object newValue) {
        final String value = newValue.toString();
        int index = preference.findIndexOfValue(value);
        preference.setSummary(preference.getEntries()[index]);
        preference.setValue(value);
        PreferencesProvider.getSharedPreferences(getActivity()).edit().putBoolean(PreferencesProvider.CHANGED, true).commit();
    }
}
