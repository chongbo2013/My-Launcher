package com.lewa.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import com.lewa.launcher.preference.PreferencesProvider;

public class ThumbnailViewAdapter extends BaseAdapter {
        private Launcher mLauncher;
        private Workspace mWorkspace;
        private ImageView mHomeView;

        public ThumbnailViewAdapter(Context context) {
            mLauncher = (Launcher)context;
            mWorkspace = mLauncher.getWorkspace();
        }
        
        public int getCount() {
            return mWorkspace.getChildCount() + 1;        // 1 for add screen
        }

        public Object getItem(int i) {
            return null;
        }

        public long getItemId(int i) {
            return -1;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int cnt = getCount();
            if (convertView == null) {
                convertView = LayoutInflater.from(mLauncher).inflate(R.layout.screen_thumbnail, null);
            }
            ImageView preView = (ImageView)convertView.findViewById(R.id.preview_screen);
            ImageView homeView = (ImageView)convertView.findViewById(R.id.home_screen);
            ImageView delView = (ImageView)convertView.findViewById(R.id.delete_screen);
            ImageView addView = (ImageView)convertView.findViewById(R.id.add_screen);
            
            if (position != cnt - 1) {
                CellLayout layout = mWorkspace.getCellLayout(position);
                long scrId = layout.getScreenId();

                preView.setOnLongClickListener(LONG_CLICK_EVENT_HANDLER);
                preView.setOnClickListener(CLICK_EVENT_HANDLER);
                preView.setTag(scrId);

                delView.setOnClickListener(CLICK_EVENT_HANDLER);
                delView.setTag(scrId);
                
                homeView.setOnClickListener(CLICK_EVENT_HANDLER);

                if (scrId == mWorkspace.getCurrentScrId()) {
                    convertView.setBackgroundResource(R.drawable.screen_current);
                }
                
                if (scrId == mWorkspace.getDefaultScrId()) {
                    mHomeView = homeView;
                    homeView.setImageResource(R.drawable.screen_home_selected);
                } 
                //yixiao add for piflow 2015.1.15
                if (layout.getShortcutsAndWidgets().getChildCount() > 0 || layout.isPifLowPage()) {
                    delView.setVisibility(View.GONE);
                    Bitmap bitmap = createThumbnail(layout, 0.33f);
                    preView.setImageBitmap(bitmap);
                }
				
		//zwsun@letek.com 20150108 start
                if(layout.isPifLowPage()){
                    homeView.setVisibility(View.GONE);
                    delView.setVisibility(View.GONE);
                    preView.setOnLongClickListener(null);
                }
                //zwsun@letek.com 20150108 end
			   
            } else {
                convertView.setBackgroundResource(R.drawable.screen_add_bg);
                homeView.setVisibility(View.GONE);
                delView.setVisibility(View.GONE);
                addView.setVisibility(View.VISIBLE);
                addView.setOnClickListener(CLICK_EVENT_HANDLER);
            }
            return convertView;
        }

        Bitmap createThumbnail(CellLayout layout, float scale) {
            float sWidth = layout.getWidth() * scale;
            float sHeight = layout.getHeight() * scale;
            Bitmap bitmap = Bitmap.createBitmap((int) sWidth, (int) sHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            layout.dispatchDraw(c);
            return bitmap;
        }
        
        OnClickListener CLICK_EVENT_HANDLER = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = (Integer) ((View)v.getParent()).getTag();
                if (v.getId() == R.id.add_screen) { // add
                    mWorkspace.addScreen(position);
                    notifyDataSetChanged();
                } else if (v.getId() == R.id.delete_screen) { // delete
                    if (mWorkspace.getChildCount() == PreferencesProvider.MIN_SCREEN_COUNT) {
                        mLauncher.makeToast(R.string.cannot_delete_more_screen);
                        //Toast.makeText(mLauncher, R.string.cannot_delete_more_screen, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mWorkspace.removeScreen(position);
                    long scrId = (Long) v.getTag();
                    if (scrId == mWorkspace.getCurrentScrId()) {
                        //yixiao modify for piflow 2015.1.15
                        mWorkspace.setCurrentPage(mLauncher.isPiflowPageEnable() ? 1 : 0);
                    }
                    if (scrId == mWorkspace.getDefaultScrId()) {
                        //yixiao modify for piflow 2015.1.15
                        mWorkspace.setDefaultPage(mLauncher.isPiflowPageEnable() ? 1 : 0);
                    }
                    notifyDataSetChanged();
                } else if (v.getId() == R.id.home_screen) {  // pin
                    if (v != mHomeView) {
                        mHomeView.setImageResource(R.drawable.screen_home_normal);
                        mHomeView = (ImageView)v;
                        mHomeView.setImageResource(R.drawable.screen_home_selected);
                        mWorkspace.setDefaultPage(position);
                    }
                } else { // exit
                    if(mLauncher.isPreviewMode() && mLauncher.isAnimating || !mLauncher.isPreviewMode())return;//lqwang - PR955333 - modify
                    mWorkspace.setCurrentPage(position);
                    mLauncher.exitPreviews();
                }
            }
        };

        OnLongClickListener LONG_CLICK_EVENT_HANDLER = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //lqwang - PR948221 - modify begin
                ThumbnailView thumbnailView = mLauncher.getThumbnailView();
                if(thumbnailView.getVisibility() == View.VISIBLE){
                    thumbnailView.onLongClick((View) v.getParent());
                }
                //lqwang - PR948221 - modify end
                return true;
            }
        };
    }
    // End