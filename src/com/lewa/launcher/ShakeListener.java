package com.lewa.launcher;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;

import com.lewa.launcher.preference.PreferencesProvider;

public class ShakeListener implements SensorEventListener {
    public static final int DEFAULT_SHAKE_DEGREE = 12;
    private Launcher mLauncher;
    private SensorManager mSensorMgr;
    private Vibrator mVibrator;
    private int mShakeThreshold;
    
    private long mLastTime;
    private float mLastX, mLastY, mLastZ;

    public ShakeListener(Context context) {
        mLauncher = (Launcher)context;
        
        mVibrator = (Vibrator) mLauncher.getSystemService(Context.VIBRATOR_SERVICE);
        mSensorMgr = (SensorManager) mLauncher.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorMgr != null) {  
            mSensorMgr.registerListener(this, mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_GAME); 
        }

        mShakeThreshold = PreferencesProvider.getShakeDegree(context) * 420;
    }

    public void pause() {
        if (mSensorMgr != null) {  
            mSensorMgr.unregisterListener(this);  
        } 
    }
    
    @Override  
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long now = System.currentTimeMillis();
            if ((now - mLastTime) > 100) {
                long diffTime = now - mLastTime;
                mLastTime = now;
                float values[] = event.values;  
                float x = values[SensorManager.DATA_X];
                float y = values[SensorManager.DATA_Y];
                float z = values[SensorManager.DATA_Z];
                float speed = Math.abs(x + y + z - mLastX - mLastY - mLastZ) / diffTime * 10000;
                if (speed > mShakeThreshold) {
                    if (!mLauncher.inPreviewMode() && !mLauncher.isFloating() && !mLauncher.isHiddenOpened() && !mLauncher.isEditMode()) {
                        mVibrator.vibrate(100);
						//zwsun@lewatek.com PR900831 20150114 start
						try{
                        mLauncher.getWorkspace().reArrangeApps();
						}catch(Exception e)
						{
						}
						//zwsun@lewatek.com PR900831 20150114 end
                    }
                }
                mLastX = x;
                mLastY = y;
                mLastZ = z;
            }
        }
    }  

    @Override  
    public void onAccuracyChanged(Sensor sensor, int accuracy) {  

    }  
}
