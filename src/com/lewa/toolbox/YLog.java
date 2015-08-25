package com.lewa.toolbox;

import android.util.Log;

/**
 * Created by Fan.Yang on 2014/8/5.
 */
public class YLog {

    private static final String TAG = "simply";
    private static final boolean isPrintLog = true;

    public static void d(String print) {
        if (isPrintLog) {
            Log.d(TAG, print);
        }
    }

    public static void e(String print) {
        if (isPrintLog) {
            Log.e(TAG, print);
        }
    }
}
