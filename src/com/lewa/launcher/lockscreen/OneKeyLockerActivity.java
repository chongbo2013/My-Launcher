package com.lewa.launcher.lockscreen;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import com.lewa.launcher.R;
import com.lewa.reflection.ReflUtils;

public class OneKeyLockerActivity extends Activity {
    private static final int REQUEST_DEVICE_POLICY = 3;
    
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        
        KeyguardManager km = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock keyguardLock = km.newKeyguardLock(getPackageName());
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName componentName = new ComponentName(this, LockScreenAdmin.class);
        //for one key lockscreen default on launcher-lqwang-modify begin
        String action = getIntent().getAction();
        boolean isAdimnActive = dpm.isAdminActive(componentName);
        if (Intent.ACTION_CREATE_SHORTCUT.equals(action) || (!isAdimnActive && Intent.ACTION_VIEW.equals(action))) {  // add shortcut
            if (isAdimnActive) {
        //for one key lockscreen default on launcher-lqwang-modify end
                createShortcut();
            } else {
                Intent intent = new Intent("android.app.action.ADD_DEVICE_ADMIN");
                intent.putExtra("android.app.extra.DEVICE_ADMIN", componentName);
                intent.putExtra("android.app.extra.ADD_EXPLANATION", getString(R.string.admin_description));
                startActivityForResult(intent, REQUEST_DEVICE_POLICY);
            }
        } else { // onClick
            if (isAdimnActive) {
                String mtkPlatform = ReflUtils.SystemProperties.get("ro.mediatek.platform", null);
                if (("MT6577".equals(mtkPlatform) && Build.VERSION.SDK_INT == 16)
                    || ("MT6575".equals(mtkPlatform) && Build.VERSION.SDK_INT == 15)) { // MTK6577 4.1 and MTK6575 4.0 go this way
                    dpm.lockNow();
                } else {
                    if (keyguardLock != null) {
                        keyguardLock.disableKeyguard();
                    }
                    dpm.lockNow();
                    if (keyguardLock != null) {
                        keyguardLock.reenableKeyguard();
                    }
                }
            }     
            finish();
        } 
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DEVICE_POLICY) {
            if (resultCode == RESULT_OK) {
                createShortcut();
            } else {
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }
    
    void createShortcut() {
        Intent shortcutIntent = new Intent(this, this.getClass());
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getResourceName(R.string.onekey_lockscreen));
        Parcelable shortIcon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_onekey_lockscreen);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortIcon);
        setResult(RESULT_OK, intent);
        finish();
    }
}
