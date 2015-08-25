package com.lewa.launcher;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.SuperscriptSpan;

class UnreadSupportShortcut {
    int unreadNum;
    int unreadType;
    ComponentName component;

    public UnreadSupportShortcut(ComponentName com, int type) {
        component = com;
        unreadNum = 0;
        unreadType = type;
    }
}

public class MessageModel extends BroadcastReceiver {
    public static final int TYPE_CALL = 0;
    public static final int TYPE_MESSAGE = 1;
    public static final int TYPE_UPDATER = 2;
    public static final int TYPE_GAME = 3;
    public static final int TYPE_APP_STORE = 4;
    public static final int TYPE_EMAIL = 5;
    
    private Context mContext;
    private static int sUnreadSupportShortcutsNum = 0;
    private static final Object mLogLock = new Object();
    private WeakReference<MessageCallbacks> mCallbacks;
    public static final String UPDATE_REQUEST = "android.intent.action.UPDATE_REQUEST";
    
    private static final SpannableStringBuilder sExceedString = new SpannableStringBuilder("99+");
    private static final ArrayList<UnreadSupportShortcut> sUnreadSupportShortcuts = new ArrayList<UnreadSupportShortcut>();

    public MessageModel(Context context) {
        mContext = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (UPDATE_REQUEST.equals(intent.getAction())) {
            int unreadNum = intent.getIntExtra("count", -1);
            int unreadType = intent.getIntExtra("type", -1);
            for (UnreadSupportShortcut uss : sUnreadSupportShortcuts) {
                if (unreadType != -1 && uss.unreadType == unreadType) {
                    final ComponentName componentName = uss.component;
                    if (mCallbacks != null && componentName != null && unreadNum != -1) {
                        final int index = supportUnreadFeature(componentName);
                        if (index >= 0) {
                            setUnreadNumberAt(index, unreadNum);
                            final MessageCallbacks callbacks = mCallbacks.get();
                            if (callbacks != null) {
                                callbacks.bindComponentUnreadChanged(componentName, unreadNum);
                            }
                        }
                    }
                }
            }
        }
    }

    public void initialize(MessageCallbacks callbacks) {
        mCallbacks = new WeakReference<MessageCallbacks>(callbacks);
    }

    void loadAndInitUnreadShortcuts() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                loadUnreadSupportShortcuts();
                initUnreadNumberFromSystem();
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (mCallbacks != null) {
                    MessageCallbacks callbacks = mCallbacks.get();
                    if (callbacks != null) {
                        callbacks.bindUnreadInfoIfNeeded();
                    }
                }
            }
        }.execute();
    }

    /**
     * Initialize unread number by querying system settings provider.
     */
    private void initUnreadNumberFromSystem() {
        final ContentResolver cr = mContext.getContentResolver();
        for (int i = 0; i < sUnreadSupportShortcutsNum && i < sUnreadSupportShortcuts.size(); i++) {
            final UnreadSupportShortcut shortcut = sUnreadSupportShortcuts.get(i);
            try {
                //lqwang - PR65523 - modify begin
                if(shortcut.unreadType == TYPE_CALL){
                    shortcut.unreadNum = ((LauncherApplication)mContext.getApplicationContext()).getMissedCallCnt();
                }else if(shortcut.unreadType == TYPE_MESSAGE){
                    shortcut.unreadNum = ((LauncherApplication)mContext.getApplicationContext()).getUnreadMsgCnt();
                }else{
                    shortcut.unreadNum = Settings.System.getInt(cr, shortcut.component.getClassName());
                }
                //lqwang - PR65523 - modify end
            } catch (Settings.SettingNotFoundException e) {
            }
        }
    }

    public void loadUnreadSupportShortcuts() {
        synchronized (mLogLock) {
            sUnreadSupportShortcuts.clear();
            
            PackageManager pm = mContext.getPackageManager();
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            List<ResolveInfo> dialInfos = pm.queryIntentActivities(dialIntent, 0);
            
            for (ResolveInfo dInfo : dialInfos) {
                if ((dInfo.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    ComponentName com = LauncherProvider.findLaunchComp(pm, dInfo);
                    if (com != null) {
                        UnreadSupportShortcut shortcut = new UnreadSupportShortcut(com, TYPE_CALL);
                        sUnreadSupportShortcuts.add(shortcut);
                    }
                }
            }
            
            Intent msgIntent = new Intent(Intent.ACTION_MAIN);
            msgIntent.setType("vnd.android-dir/mms-sms");
            List<ResolveInfo> msgInfos = pm.queryIntentActivities(msgIntent, 0);
            for (ResolveInfo mInfo : msgInfos) {
                if ((mInfo.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    ComponentName com = LauncherProvider.findLaunchComp(pm, mInfo);
                    if (com != null) {
                        UnreadSupportShortcut shortcut = new UnreadSupportShortcut(com, TYPE_MESSAGE);
                        sUnreadSupportShortcuts.add(shortcut);
                    }
                }
            }
            
            UnreadSupportShortcut update = new UnreadSupportShortcut(
                    new ComponentName("com.lewa.updater", "com.lewa.updater.UpdaterActivity"), TYPE_UPDATER);
            sUnreadSupportShortcuts.add(update);

            UnreadSupportShortcut game = new UnreadSupportShortcut(
                    new ComponentName("com.lewa.gamecenter", "com.lewa.gamecenter.SplashGuideActivity"), TYPE_GAME);
            sUnreadSupportShortcuts.add(game);

            UnreadSupportShortcut store = new UnreadSupportShortcut(
                    new ComponentName("com.lewa.appstore", "com.lewa.appstore.MainActivity"), TYPE_APP_STORE);
            sUnreadSupportShortcuts.add(store);

            UnreadSupportShortcut email = new UnreadSupportShortcut(
                    new ComponentName("com.android.email", "com.android.email.activity.Welcome"), TYPE_EMAIL);
            sUnreadSupportShortcuts.add(email);
        }
        sUnreadSupportShortcutsNum = sUnreadSupportShortcuts.size();
    }
    
    public static boolean isLewaUpdater(ComponentName com) {
        return new ComponentName("com.lewa.updater",
                "com.lewa.updater.UpdaterActivity").equals(com);
    }

    static int supportUnreadFeature(ComponentName component) {
        if (component == null) {
            return -1;
        }

        for (int i = 0, sz = sUnreadSupportShortcuts.size(); i < sz; i++) {
            if (sUnreadSupportShortcuts.get(i).component.equals(component)) {
                return i;
            }
        }

        return -1;
    }

    synchronized static void setUnreadNumberAt(int index, int unreadNum) {
        if (index >= 0 || index < sUnreadSupportShortcutsNum) {
            sUnreadSupportShortcuts.get(index).unreadNum = unreadNum;
        }
    }

    synchronized static int getUnreadNumberAt(int index) {
        if (index < 0 || index >= sUnreadSupportShortcutsNum) {
            return 0;
        }
        return sUnreadSupportShortcuts.get(index).unreadNum;
    }

    static int getUnreadNumberOfComponent(ComponentName component) {
        final int index = supportUnreadFeature(component);
        return getUnreadNumberAt(index);
    }

    static CharSequence getExceedText() {
        return sExceedString;
    }
    
    static CharSequence getDisplayText(int unreadNum) {
        if (unreadNum > 99) {
            return getExceedText(); 
        } else {
            return String.valueOf(unreadNum);
        }
    }

    /**
     * Generate a text contains specified span to display the unread information
     * when the value is more than 99, do not use toString to convert it to
     * string, that may cause the span invalid.
     */
    static {
        sExceedString.setSpan(new SuperscriptSpan(), 3, 3,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sExceedString.setSpan(new AbsoluteSizeSpan(10), 3, 3,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public interface MessageCallbacks {
        public void bindComponentUnreadChanged(ComponentName component, int unreadNum);
        public void bindUnreadInfoIfNeeded();
    }
}
