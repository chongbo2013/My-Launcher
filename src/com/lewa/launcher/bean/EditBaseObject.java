package com.lewa.launcher.bean;

import com.lewa.launcher.PendingAddItemInfo;

public class EditBaseObject {
    PendingAddItemInfo createItemInfo;
    String label;
    String pkgName;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public PendingAddItemInfo getCreateItemInfo() {
        return createItemInfo;
    }

    public void setCreateItemInfo(PendingAddItemInfo createItemInfo) {
        this.createItemInfo = createItemInfo;
    }

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }


}
