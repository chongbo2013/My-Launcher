/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lewa.launcher;

import java.util.ArrayList;

import android.content.ContentValues;

/**
 * Represents a folder containing shortcuts or apps.
 */
class FolderInfo extends ItemInfo {

    /**
     * Whether this folder has been opened
     */
    boolean opened;

    /**
     * The apps and shortcuts
     */
    ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();
    ArrayList<ShortcutInfo> recommendApps = new ArrayList<ShortcutInfo>();

    ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

    FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(ShortcutInfo item) {
        contents.add(item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onAdd(item);
        }
        itemsChanged();
    }

    public void add(ArrayList<ShortcutInfo> items) {
        for (int i = 0; i < items.size(); i++) {
            contents.add(items.get(i));
        }
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onAdd(items);
        }
        itemsChanged();
    }

    public void add(ShortcutInfo item, int index, boolean notify) {
        contents.add(index, item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onAdd(item);
        }
        itemsChanged();
        for (int i = 0; i < listeners.size() && notify; i++) {
            listeners.get(i).onUpdate();
        }
    }

    /**
     * Add an recommend app
     *
     * @param item
     */
    public void clearRecommendApp() {
        recommendApps.clear();
    }
    
    /**
     * Add an recommend app or shortcut
     *
     * @param item
     */
    public void addRecommendApp(ShortcutInfo item) {
        recommendApps.add(item);
    }
    
    /**
     * check if there is a item as same as specify.
     *
     * @param item
     */
    public boolean has(ShortcutInfo item) {
        if (contents != null) {
            for (ShortcutInfo shorcut : contents) {
                if (item.id == shorcut.id) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(ShortcutInfo item) {
        contents.remove(item);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onRemove(item);
        }
        itemsChanged();
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onTitleChanged(title);
        }
    }
    
    public int getVisibleCnt() {
        int visibleCnt = 0;
        for (ShortcutInfo info : contents) {
            if (info.state == ShortcutInfo.STATE_OK && !info.isHidden()) {
                visibleCnt++;
            }
        }
        return visibleCnt;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);
        values.put(LauncherSettings.Favorites.TITLE, title.toString());
    }

    void addListener(FolderListener listener) {
        listeners.add(listener);
    }

    void removeListener(FolderListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    void itemsChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onItemsChanged();
        }
    }
    
    void reBind() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onReBind();
        }
    }

    @Override
    void unbind() {
        super.unbind();
        listeners.clear();
    }

    interface FolderListener {
        public void onAdd(ShortcutInfo item);
        // #60465 Add by Fan.Yang
        public void onAdd(ArrayList<ShortcutInfo> items);
        public void onRemove(ShortcutInfo item);
        public void onTitleChanged(CharSequence title);
        public void onItemsChanged();
        public void onReBind();
        public void onUpdate();
    }
}
