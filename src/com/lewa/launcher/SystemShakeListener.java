package com.lewa.launcher;

import com.lewa.toolbox.EditModeUtils;

import android.content.Context;
import android.os.Vibrator;
import lewa.provider.SensorProviderListener;
import lewa.provider.SensorProviderListener.OnShakeAndShakeListener;

public class SystemShakeListener implements OnShakeAndShakeListener {
    private static final String TAG = "SystemShakeListener";
    private Launcher mLauncher;
    private Vibrator mVibrator;
    private SensorProviderListener mSensorProviderListener;
    public SystemShakeListener(Context context) {
        mLauncher = (Launcher)context;
        
        mVibrator = (Vibrator) mLauncher.getSystemService(Context.VIBRATOR_SERVICE);
        
        mSensorProviderListener = new SensorProviderListener(mLauncher.getApplicationContext());
        
        mSensorProviderListener.registerSensorEventerListener(SensorProviderListener.SHAKE_SENSOR);
        
        mSensorProviderListener.setOnShakeListener(this);
    }
    @Override
    public void onShake() {
        // TODO Auto-generated method stub
        EditModeUtils.logE(TAG, "onShake");
        if (!mLauncher.inPreviewMode() && !mLauncher.isFloating() && !mLauncher.isHiddenOpened() && !mLauncher.isEditMode()
                //yixiao add #953280 2015.3.31
                && !mLauncher.getWorkspace().isPiflowPage()) {
            mVibrator.vibrate(100);
            //yixiao Add for piflow 2015.1.14 begin
            if(mLauncher.getWorkspace().isPiflowPage()){
                return;
            }
            //yixiao Add for piflow 2015.1.14 end
            mLauncher.getWorkspace().reArrangeApps();
        }
    }
    
    public void pause() {
        if(mSensorProviderListener != null){
            mSensorProviderListener.unregisterSensorEventerListener(SensorProviderListener.SHAKE_SENSOR);
        }
    }

}
