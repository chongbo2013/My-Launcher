package lewa.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;

// built into framework.jar
interface IIconManager {
    Bitmap loadIconByResolveInfo(in ResolveInfo ri);
    Bitmap loadIconByApplcationInfo(in ApplicationInfo ai);
    void clearCustomizedIcons(in String packageName);
    void reset();
    void checkModIcons();
    String getFancyIconRelativePath(in String packageName, in String className);
}
