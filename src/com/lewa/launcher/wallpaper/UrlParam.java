/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lewa.launcher.wallpaper;

import com.lewa.launcher.EditModeLayer;
/**
 * @author Administrator
 */
public class UrlParam extends NetBaseParam {

    public final String CONST_PAGING = "_paging";
    public final String CONST_PAGE = "page";
    public final String CONST_COUNT = "_total";
    public int pageSize;
    public String orderField = "";
    public String order = "";
    public int pageNo = 1;
    public static final int DEFAULT_PAGE_SIZE = 4;

    public UrlParam(String type) {
        super(type);
    }

    public static UrlParam newUrlParam(String type) {
        UrlParam netBaseParam = new UrlParam(type);
        netBaseParam.mType = type;
        netBaseParam.screenSchema = NetBaseParam.getCurrentScreenSchema();
        netBaseParam.screenDpi = EditModeLayer.screenDPI;
        netBaseParam.pageSize = DEFAULT_PAGE_SIZE;
        netBaseParam.pageNo = 1;
        return netBaseParam;
    }

    public static UrlParam newUrlParam(String type, int typeId) {
        UrlParam netBaseParam = new UrlParam(type);
        netBaseParam.mType = type;
        netBaseParam.screenSchema = NetBaseParam.getCurrentScreenSchema();
        netBaseParam.screenDpi = EditModeLayer.screenDPI;
        netBaseParam.pageSize = DEFAULT_PAGE_SIZE;
        netBaseParam.pageNo = 1;
        netBaseParam.typeId = typeId;
        return netBaseParam;
    }

    public static UrlParam newUrlParam(String themeId, boolean isGetTheme) {
        UrlParam netBaseParam = new UrlParam("themepackage");
        netBaseParam.isGetTheme = true;
        netBaseParam.themeId = themeId;
        netBaseParam.screenDpi = EditModeLayer.screenDPI;
        return netBaseParam;
    }

    public String toPagingUrl() {
        String resolutionParam;
        resolutionParam = resolveResolutionParam();
        String pagingStr = "";
        String pagingParam = "";
        if (pageSize != -1) {
            pagingStr = CONST_PAGING;
            if (pageNo != 1) {
                pagingParam = "&" + CONST_PAGE + "=" + pageNo;
            }
        }
        return NetBaseParam.PrefixUrl + "/" + mType + pagingStr + "?" + resolutionParam
                + pagingParam + "&" + CONST_SYS_VERSION + "=" + NetBaseParam.SYS_VERSION;
    }

    public String toCountUrl() {
        String resolutionParam;
        resolutionParam = resolveResolutionParam();
        String countStr = CONST_COUNT;
        return NetBaseParam.PrefixUrl + "/" + "module" + countStr + "?" + resolutionParam + "&" + "moduleid=" + typeId + "&" + CONST_SYS_VERSION + "=" + NetBaseParam.SYS_VERSION;
    }
}
