package com.lewa.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.SearchManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;

import com.lewa.launcher.LauncherApplication;

public class ReflUtils {
	public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 0x00000002;
	public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x00000400;
	public static final String ACTION_APPWIDGET_BIND = "android.appwidget.action.APPWIDGET_BIND";
	public static final String EXTRA_APPWIDGET_PROVIDER = "appWidgetProvider";
	public final static String INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED = "android.search.action.GLOBAL_SEARCH_ACTIVITY_CHANGED";

	private static Method sMupdateAppWidgetSize = null;
	private static Method sMgetImportantForAccessibility = null;
	private static Method sMsetImportantForAccessibility = null;
	private static Method sMgetGlobalSearchActivity = null;
	private static Method sMbindAppWidgetIdIfAllowed2 = null;
	private static Method sMbindAppWidgetIdIfAllowed3 = null;
	private static Method sMhasResourceWallpaper = null;
	private static Method sMmakeScaleUpAnimation = null;
	private static Method sMtoBundle = null;
	private static Method sMstartActivity = null;
	private static Method sMgetCurrentSizeRange = null;
	private static Method sMbindAppWidgetId = null;
	private static Method sMSystemProperties_get = null;
	private static Method sMSystemProperties_getLong = null;
	private static Method sMSystemProperties_getInt = null;
	private static Method sMSystemProperties_getBoolean = null;
	private static Method sMDisableStatusBarBackground = null;

	public static boolean isInstance(String className, Object obj) {
		Class<?> cls = null;
		try {
			cls = Class.forName(className);
			if (cls != null) {
				return cls.isInstance(obj);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static void updateAppWidgetSize(AppWidgetHostView widgetView,
			Bundle newOptions, int minWidth, int minHeight, int maxWidth,
			int maxHeight) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMupdateAppWidgetSize == null) {
					sMupdateAppWidgetSize = AppWidgetHostView.class
							.getDeclaredMethod("updateAppWidgetSize",
									Bundle.class, Integer.TYPE, Integer.TYPE,
									Integer.TYPE, Integer.TYPE);
				}
				if (sMupdateAppWidgetSize != null) {
					sMupdateAppWidgetSize.invoke(widgetView, newOptions,
							minWidth, minHeight, maxWidth, maxHeight);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
	}

	public static int getImportantForAccessibility(View view) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMgetImportantForAccessibility == null) {
					sMgetImportantForAccessibility = View.class
							.getDeclaredMethod("getImportantForAccessibility");
				}
				if (sMgetImportantForAccessibility != null) {
					return (Integer) (sMgetImportantForAccessibility
							.invoke(view));
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return IMPORTANT_FOR_ACCESSIBILITY_NO;
	}

	public static void setImportantForAccessibility(View view, int important) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMsetImportantForAccessibility == null) {
					sMsetImportantForAccessibility = View.class
							.getDeclaredMethod("setImportantForAccessibility",
									Integer.TYPE);
				}
				if (sMsetImportantForAccessibility != null) {
					sMsetImportantForAccessibility.invoke(view, important);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
	}

	public static ComponentName getGlobalSearchActivity(SearchManager sm) {
		try {
			if (sMgetGlobalSearchActivity == null) {
				sMgetGlobalSearchActivity = SearchManager.class
						.getDeclaredMethod("getGlobalSearchActivity");
			}
			if (sMgetGlobalSearchActivity != null) {
				return (ComponentName) sMgetGlobalSearchActivity.invoke(sm);
			}
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static boolean bindAppWidgetIdIfAllowed(
			AppWidgetManager widgetManager, int appWidgetId,
			ComponentName provider) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMbindAppWidgetIdIfAllowed2 == null) {
					sMbindAppWidgetIdIfAllowed2 = AppWidgetManager.class
							.getDeclaredMethod("bindAppWidgetIdIfAllowed",
									Integer.TYPE, ComponentName.class);
				}
				if (sMbindAppWidgetIdIfAllowed2 != null) {
					return (Boolean) sMbindAppWidgetIdIfAllowed2.invoke(
							widgetManager, appWidgetId, provider);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return false;

	}

	public static boolean bindAppWidgetIdIfAllowed(
			AppWidgetManager widgetManager, int appWidgetId,
			ComponentName provider, Bundle options) {
		if (Build.VERSION.SDK_INT >= 17) {
			try {
				if (sMbindAppWidgetIdIfAllowed3 == null) {
					sMbindAppWidgetIdIfAllowed3 = AppWidgetManager.class
							.getDeclaredMethod("bindAppWidgetIdIfAllowed",
									Integer.TYPE, ComponentName.class,
									Bundle.class);
				}
				if (sMbindAppWidgetIdIfAllowed3 != null) {
					return (Boolean) sMbindAppWidgetIdIfAllowed3.invoke(
							widgetManager, appWidgetId, provider, options);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	public static boolean bindAppWidgetId(
			AppWidgetManager widgetManager, int appWidgetId,
			ComponentName provider) {
		if (Build.VERSION.SDK_INT <= 15) {
			try {
				if (sMbindAppWidgetId == null) {
					sMbindAppWidgetId = AppWidgetManager.class
							.getDeclaredMethod("bindAppWidgetId",
									Integer.TYPE, ComponentName.class);
				}
				if (sMbindAppWidgetId != null) {
					return (Boolean) sMbindAppWidgetId.invoke(
							widgetManager, appWidgetId, provider);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static boolean hasResourceWallpaper(WallpaperManager wpm, int resId) {
		if (Build.VERSION.SDK_INT >= 17) {
			try {
				if (sMhasResourceWallpaper == null) {
					sMhasResourceWallpaper = WallpaperManager.class
							.getDeclaredMethod("hasResourceWallpaper",
									Integer.TYPE);
				}
				if (sMhasResourceWallpaper != null) {
					return (Boolean) sMhasResourceWallpaper.invoke(wpm, resId);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static Bundle makeScaleUpAnimationBundle(View source, int startX,
			int startY, int startWidth, int startHeight) {
		Object activityOptions;

		if (Build.VERSION.SDK_INT >= 16) {
			try {
				Class<?> cls = Class.forName("android.app.ActivityOptions");
				if (cls == null) {
					return null;
				}
				// View source, int startX, int startY, int startWidth, int
				// startHeight

				if (sMmakeScaleUpAnimation == null) {
					sMmakeScaleUpAnimation = cls.getDeclaredMethod(
							"makeScaleUpAnimation", View.class, Integer.TYPE,
							Integer.TYPE, Integer.TYPE, Integer.TYPE);
				}
				if (sMmakeScaleUpAnimation == null) {
					return null;
				}
				if (sMtoBundle == null) {
					sMtoBundle = cls.getDeclaredMethod("toBundle");
				}
				if (sMtoBundle == null) {
					return null;
				}
				activityOptions = sMmakeScaleUpAnimation.invoke(null, source,
						startX, startY, startWidth, startHeight);
				return (Bundle) sMtoBundle.invoke(activityOptions);

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static void startActivity(Context context, Intent intent,
			Bundle options) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMstartActivity == null) {
					sMstartActivity = Context.class.getDeclaredMethod(
							"startActivity", Intent.class, Bundle.class);
				}
				if (sMstartActivity != null) {
					sMstartActivity.invoke(context, intent, options);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
	}

	public static void getCurrentSizeRange(Display display,
			Point outSmallestSize, Point outLargestSize) {
		if (Build.VERSION.SDK_INT >= 16) {
			try {
				if (sMgetCurrentSizeRange == null) {
					sMgetCurrentSizeRange = Display.class
							.getDeclaredMethod("getCurrentSizeRange",
									Point.class, Point.class);
				}
				if (sMgetCurrentSizeRange != null) {
					sMgetCurrentSizeRange.invoke(display, outSmallestSize, outLargestSize);
				}
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}else {
			DisplayMetrics displayMetrics = new DisplayMetrics();
			display.getMetrics(displayMetrics);
			
	        final int maxDim = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
	        final int minDim = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
		}
	} 
	
    public static void showNotifications(Context context) {
        try {
            Object service = context.getSystemService("statusbar");
            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");

            Method expand = null;
            if (Build.VERSION.SDK_INT >= 17) {
                expand = statusbarManager.getMethod("expandNotificationsPanel");
            } else {
                expand = statusbarManager.getMethod("expand");
            }
            if (expand != null) {
                expand.invoke(service);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //
    // StatusBarManager statusBar = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
    // statusBar.disable(StatusBarManager.DISABLE_NONE);
    // ExtraStatusBarManager.DISABLE_BACKGROUND
    public final static int STATUSBAR_DISABLE_BACKGROUND = 0x40000000;
    // StatusBarManager.DISABLE_NONE
    public final static int STATUSBAR_DISABLE_NONE = 0x00000000;
    public static void disableStatusBarBackground(Context context, int what) {
        try {
            
            if (!LauncherApplication.isSystemApp()) {
                return;
            }
            Object service = context.getSystemService("statusbar");
            

            sMDisableStatusBarBackground = null;
            if (sMDisableStatusBarBackground == null) {
                Class<?> statusbarManagerCls = Class.forName("android.app.StatusBarManager");
                sMDisableStatusBarBackground = statusbarManagerCls.getMethod("disable", Integer.TYPE);
            }
            if (sMDisableStatusBarBackground != null) {
                sMDisableStatusBarBackground.invoke(service, what);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
    public static class SystemProperties {
        public static String get(String key, String def) {
            try {
                if (sMSystemProperties_get == null) {
                    Class<?> systemPropertiesClz = Class.forName("android.os.SystemProperties");
                    sMSystemProperties_get = systemPropertiesClz.getMethod("get", String.class, String.class);
                }
                if (sMSystemProperties_get != null) {
                    return (String)sMSystemProperties_get.invoke(null, key, def);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return def;
        }
        
        
        public static long getLong(String key, long def) {
            try {
                if (sMSystemProperties_getLong == null) {
                    Class<?> systemPropertiesClz = Class.forName("android.os.SystemProperties");
                    sMSystemProperties_getLong = systemPropertiesClz.getMethod("getLong", String.class, Long.TYPE);
                }
                if (sMSystemProperties_getLong != null) {
                    return (Long)sMSystemProperties_getLong.invoke(null, key, def);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return def;
        }
        
        public static int getInt(String key, int def) {
            try {
                if (sMSystemProperties_getInt == null) {
                    Class<?> systemPropertiesClz = Class.forName("android.os.SystemProperties");
                    sMSystemProperties_getInt = systemPropertiesClz.getMethod("getInt", String.class, Integer.TYPE);
                }
                if (sMSystemProperties_getInt != null) {
                    return (Integer)sMSystemProperties_getInt.invoke(null, key, def);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return def;
        }
        
        public static boolean getBoolean(String key, boolean def) {
            try {
                if (sMSystemProperties_getBoolean == null) {
                    Class<?> systemPropertiesClz = Class.forName("android.os.SystemProperties");
                    sMSystemProperties_getBoolean = systemPropertiesClz.getMethod("getBoolean", String.class, Boolean.TYPE);
                }
                if (sMSystemProperties_getBoolean != null) {
                    return (Boolean)sMSystemProperties_getBoolean.invoke(null, key, def);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return def;
        }
    }

}
