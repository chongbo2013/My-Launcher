package com.lewa.toolbox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.text.TextUtils;

/**
 * LewaUtils, You can get some info about lewaos from this
 * <ul>
 * <li>{@link #getBiClientId(Context)} get biclient id, only init once</li>
 * <li>{@link #getLewaOSVersion()} get lewa os version, only init once</li>
 * <li>{@link #getPartner()} get ro.sys.partner, only init once</li>
 * </ul>
 * 
 * @author gxwu@lewatek.com 2013-8-24
 */
public class LewaUtils {

    private static final String CLASS_NAME_BIAGENT            = "lewa.bi.BIAgent";
    private static final String METHOD_NAME_GET_CLIENT_ID     = "getBIClientId";
    private static final String CLASS_NAME_LEWA_BUILD         = "lewa.os.Build";
    private static final String FIELD_NAME_LEWA_BUILD_VERSION = "LEWA_VERSION";
    private static final String CLASS_NAME_SYSTEM_PROPERTIES  = "android.os.SystemProperties";
    private static final String METHOD_NAME_GET_STRING        = "get";
    private static final String STRING_SYS_PARTNER            = "ro.sys.partner";
    public static final String  DEFAULT_SYS_PARTNER           = "Lewa";
    public static final String  USER_AGENT_PREFIX             = "LewaApi/1.0-1";
    public static final String  PACKAGE_NAME                  = "com.lewa.launcher";

    private static String       userAgent                     = null;
    /** android:versionCode in AndroidManifest.xml **/
    private static String       appVersionCode                = null;
    /** biclient id defined by lewa.bi.BIAgent.getBIClientId(Context context) method **/
    private static String       biClientId                    = null;
    /** lewa os version defined by lewa.os.Build.LEWA_VERSION **/
    private static String       lewaBuildVersion              = null;
    /** get ro.sys.partner by SystemProperties.get(property, ""); **/
    private static String       systemPartner                 = null;

    private static boolean      initBiClientId                = false;
    private static boolean      initLewaBuildVersion          = false;
    private static boolean      initPartner                   = false;
    private static boolean      initUserAgent                 = false;
    private static boolean      initAppVersionCode            = false;

    /**
     * get biclient id, only init once
     * 
     * @param context
     * @return null if not lewa os
     */
    public static String getBiClientId(Context context) {
        if (!initBiClientId && context != null) {
            initBiClientId = true;
            // get biclient id by lewa.bi.BIAgent.getBIClientId(Context context) method
            Class<?> demo = null;
            try {
                demo = Class.forName(CLASS_NAME_BIAGENT);
                Method method = demo.getMethod(METHOD_NAME_GET_CLIENT_ID, Context.class);
                return (biClientId = (String)method.invoke(demo.newInstance(), context));
            } catch (Exception e) {
                /**
                 * accept all exception, include ClassNotFoundException, NoSuchMethodException,
                 * InvocationTargetException, NullPointException
                 */
                e.printStackTrace();
            }
        }
        return biClientId;
    }

    /**
     * get lewa os version, only init once
     * 
     * @return null if not lewa os
     */
    public static String getLewaOSVersion() {
        if (!initLewaBuildVersion) {
            initLewaBuildVersion = true;
            // get lewa os version by lewa.os.Build.LEWA_VERSION
            Class<?> demo = null;
            try {
                demo = Class.forName(CLASS_NAME_LEWA_BUILD);
                Field field = demo.getField(FIELD_NAME_LEWA_BUILD_VERSION);
                return lewaBuildVersion = (String)field.get(demo.newInstance());
            } catch (Exception e) {
                /**
                 * accept all exception, include ClassNotFoundException, NoSuchFieldException, InstantiationException,
                 * IllegalArgumentException, IllegalAccessException, NullPointException
                 */
                e.printStackTrace();
            }
        }
        return lewaBuildVersion;
    }

    /**
     * get ro.sys.partner, only init once
     * 
     * @return "Lewa" if not lewa os or b2c, otherwise if lewa b2b os
     */
    public static String getPartner() {
        if (!initPartner) {
            initPartner = true;
            // get ro.sys.partner by SystemProperties.get(property, "");
            Class<?> demo = null;
            try {
                demo = Class.forName(CLASS_NAME_SYSTEM_PROPERTIES);
                Method method = demo.getMethod(METHOD_NAME_GET_STRING, String.class, String.class);
                systemPartner = (String)method.invoke(demo.newInstance(), STRING_SYS_PARTNER, "");
            } catch (Exception e) {
                /**
                 * accept all exception, include ClassNotFoundException, NoSuchMethodException,
                 * InvocationTargetException, NullPointException
                 */
                e.printStackTrace();
            }
        }

        if (TextUtils.isEmpty(systemPartner) || DEFAULT_SYS_PARTNER.equalsIgnoreCase(systemPartner)) {
            systemPartner = DEFAULT_SYS_PARTNER;
        }
        return systemPartner;
    }

    /**
     * get app version code, only init once
     * 
     * @param context
     * @return
     */
    public static String getAppVersionCode(Context context) {
        if (!initAppVersionCode && context != null) {
            initAppVersionCode = true;
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                PackageInfo pi;
                try {
                    pi = pm.getPackageInfo(context.getPackageName(), 0);
                    if (pi != null) {
                        return (appVersionCode = Integer.toString(pi.versionCode));
                    }
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return appVersionCode;
    }

    /**
     * get http user agent
     * 
     * @param context
     * @return
     */
    public static synchronized String getUserAgent(Context context) {
        if (!initUserAgent && context != null) {
            initUserAgent = true;
            StringBuilder s = new StringBuilder(256);
            s.append(USER_AGENT_PREFIX);
            s.append(" (Android ").append(Build.VERSION.RELEASE);
            String model = Build.MODEL;
            if (model != null) {
                s.append("; Model ").append(model.replace(" ", "_"));
            }
            String lewaOSVersion = LewaUtils.getLewaOSVersion();
            if (lewaOSVersion != null && lewaOSVersion.length() > 0) {
                s.append("; ").append(lewaOSVersion);
            }
            s.append(") ");
            if (!initAppVersionCode) {
                getAppVersionCode(context);
            }
            if (appVersionCode != null) {
                s.append(LewaUtils.PACKAGE_NAME).append("/").append(appVersionCode);
            }
            String biClientId = LewaUtils.getBiClientId(context);
            if (biClientId != null && biClientId.length() > 0) {
                s.append(" ClientID/").append(biClientId);
            }
            return (userAgent = s.toString());
        }
        return userAgent;
    }
    
    public static String getUserAgent(){
    	return userAgent;
    }
}
