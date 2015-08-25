package com.lewa.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lewa.content.res.IconCustomizer;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.reflection.ReflUtils;

public class WidgetsAdapter extends BaseAdapter {
	private static final String LOG_TAG = "WidgetsAdapter";
	private static final boolean DEBUG = false;
	private static final int CACHE_SIZE_LIMIT_PERCENT = 10;
	private static final float WIDGET_SCALE = 0.7f;
	private Launcher mLauncher;
	private String mDimensionsFormatString;
	private int mAppIconSize;
	private final float sWidgetPreviewIconPaddingPercentage = 0.20f;
	private final PackageManager mPackageManager;
	private ArrayList<Object> mItems = new ArrayList<Object>();
	private Drawable mDefaultWidgetBackgroundTwo; //mDefaultWidgetBackground
	private PreviewImageLoader mImageLoader;
	private boolean mBusy = false;
	private int mCellWidth;
	private int mCellHeight;
	private Bitmap[] mDefaultPreviews;
	private CellLayout.CellInfo mLongClickCell;
	private static Boolean mIsSupportWidgetBind;
	private int mCellCountX;
	private int mCellCountY;

	public WidgetsAdapter(Launcher launcher, CellLayout.CellInfo longClickCell) {
		super();

		mLauncher = launcher;
		mLongClickCell = longClickCell;
		mPackageManager = launcher.getPackageManager();
		Resources res = launcher.getResources();
		mDimensionsFormatString = res.getString(R.string.widget_dims_format);
		mAppIconSize = (int)(IconCache.getAppIconSize(res) * 1.14);
		mDefaultWidgetBackgroundTwo = new ColorDrawable(android.R.color.transparent);
		//mDefaultWidgetBackground = launcher.getResources().getDrawable(R.drawable.default_widget_preview_holo);
		DisplayMetrics display = res.getDisplayMetrics();
		mCellWidth = (int) (display.widthPixels * WIDGET_SCALE);
		mCellHeight = (int) (display.widthPixels * WIDGET_SCALE);
		mCellCountX = PreferencesProvider.getCellCountX(launcher);
		mCellCountY = PreferencesProvider.getCellCountY(launcher);
		updatePackages();
		mImageLoader = new PreviewImageLoader(launcher);
		
		mDefaultPreviews = new Bitmap[mCellCountY];
		for (int i = 0; i < mCellCountY; i++) {
			mDefaultPreviews[i] = createDefaultWidgetPreview(1, i + 1);
		}
	}

	public void setFlagBusy(boolean busy) {
		this.mBusy = busy;
	}

	public void updatePackages() {
		List<AppWidgetProviderInfo> widgets = AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
		if (!LauncherApplication.isSystemApp() && !isSupportWidgetBind()) {
		    Intent thirdPartyIntent = new Intent();
		    thirdPartyIntent.setClassName(mLauncher.getPackageName(), "com.lewa.launcher.ThirdPartyWidgets");
		    List<ResolveInfo> thirdList = mPackageManager.queryIntentActivities(thirdPartyIntent, 0);
		    mItems.addAll(thirdList);
		} else {
			for (AppWidgetProviderInfo widget : widgets) {
			    int[] spanXY = Launcher.getSpanForWidget(mLauncher, widget);
				if (widget.minWidth > 0 && widget.minHeight > 0 && spanXY[0] <= mCellCountX && spanXY[1] <= mCellCountY) {
					mItems.add(widget);
				} else {
					Log.e(LOG_TAG, "Widget " + widget.provider + " has invalid dimensions (" + widget.minWidth + ", "
							+ widget.minHeight + ")");
				}
			} 
		}

		Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
		List<ResolveInfo> shortcuts = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
		mItems.addAll(shortcuts);
		Collections.sort(mItems, new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		
		if (convertView == null) {
			convertView = LayoutInflater.from(mLauncher).inflate(R.layout.add_widget_item, parent, false);
		}
		Object rawInfo = getItem(position);
		if (rawInfo == null || mItems == null) {
		    return convertView;
		}
		TextView title = (TextView) convertView.findViewById(R.id.widget_title);
		TextView dimen = (TextView) convertView.findViewById(R.id.widget_dimen);
		ImageView preview = (ImageView) convertView.findViewById(R.id.widget_preview);
		ImageView addBtn = (ImageView) convertView.findViewById(R.id.add_button);

		PendingAddItemInfo createItemInfo = null;
		if (rawInfo instanceof AppWidgetProviderInfo) {
			AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
			createItemInfo = new PendingAddWidgetInfo(info, null, null);
			int[] spanXY = Launcher.getSpanForWidget(mLauncher, info);
			createItemInfo.spanX = spanXY[0];
			createItemInfo.spanY = spanXY[1];
			int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, info);
			createItemInfo.minSpanX = minSpanXY[0];
			createItemInfo.minSpanY = minSpanXY[1];
			title.setText(info.label);
			dimen.setText(String.format(mDimensionsFormatString, spanXY[0], spanXY[1]));
			convertView.setTag(createItemInfo);
		} else if (rawInfo instanceof ResolveInfo) {
			ResolveInfo info = (ResolveInfo) rawInfo;
			createItemInfo = new PendingAddShortcutInfo(((ResolveInfo) rawInfo).activityInfo);
			createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
			createItemInfo.spanX = 1;
			createItemInfo.spanY = 1;
			createItemInfo.componentName = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
			CharSequence label = info.loadLabel(mPackageManager);
			title.setText(label);
			dimen.setText(String.format(mDimensionsFormatString, 1, 1));
		}
		//preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup., height));
		preview.getLayoutParams().height = getViewHeight(createItemInfo.spanY);
		preview.setMaxWidth(mCellWidth);
		preview.setMaxHeight(mCellHeight);
		final PendingAddItemInfo addItemInfo = createItemInfo;
		addBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    if (isThirdPartyWidget(addItemInfo)) {
			        mLauncher.addAppWidgetFromPick();
			    } else {
			        mLauncher.addItemFromPick(addItemInfo, mLongClickCell, null);
			    }
			}
		});

		
		if (rawInfo instanceof AppWidgetProviderInfo) {
			AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
			title.setText(info.label);
			dimen.setText(String.format(mDimensionsFormatString, createItemInfo.spanX, createItemInfo.spanY));
			convertView.setTag(createItemInfo);
		} else if (rawInfo instanceof ResolveInfo) {
			ResolveInfo info = (ResolveInfo) rawInfo;
			CharSequence label = info.loadLabel(mPackageManager);
			title.setText(label);
			dimen.setText(String.format(mDimensionsFormatString, 1, 1));

		}
		if (!mBusy) {
			mImageLoader.DisplayImage(createItemInfo, preview, false);
		} else {
			mImageLoader.DisplayImage(createItemInfo, preview, true);

		}
		ViewHolder viewHolder = new ViewHolder();
		viewHolder.title = title;
		viewHolder.dimen = dimen;
		viewHolder.itemInfo = createItemInfo;
		viewHolder.preview = preview;
		viewHolder.addBtn = addBtn;
		convertView.setTag(createItemInfo);
		return convertView;
	}

	static class ViewHolder {
		TextView title;
		TextView dimen;
		ImageView preview;
		ImageView addBtn;
		ItemInfo itemInfo;
	}

	public int getCount() {
	    if (mItems != null) {
	        return mItems.size();
	    }
	    return 0;
	}

	public Object getItem(int position) {
	    if (mItems != null) {
	        return mItems.get(position);
	    }
	    return null;
	}

	public long getItemId(int position) {
		return position;
	}

	int getViewHeight (int spanY) {
		int[] wh = mLauncher.getWorkspace().estimateItemSize(1, spanY, null, false);
		int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
		int targetHeight = (int)(wh[1] * WIDGET_SCALE);
		
		targetHeight = Math.max(targetHeight, 2*minOffset + mAppIconSize);
		
		return targetHeight;
	}
	
	private MyBitmap getShortcutPreview(ActivityInfo info, int cellWidth, int cellHeight) {
		// Render the background

		// Render the icon
		LauncherApplication app = ((LauncherApplication) mLauncher.getApplication());
		Drawable icon = app.getIconCache().getFullResIcon(info);
		icon = IconCustomizer.generateIconDrawable(icon);
		//renderDrawableToBitmap(icon, preview, offset, offset, mAppIconSize, mAppIconSize);
		return new MyBitmap(((BitmapDrawable)icon).getBitmap(), true);
	}

	private MyBitmap getWidgetPreview(PendingAddItemInfo itemInfo, int cellHSpan, int cellVSpan) {
		// Load the preview image if possible
		PendingAddWidgetInfo info = (PendingAddWidgetInfo) itemInfo;

		String packageName = info.componentName.getPackageName();
		Drawable drawable = null;
		Bitmap preview = null;
		MyBitmap result = new MyBitmap(null, false);
		if (info.previewImage != 0) {
			drawable = mPackageManager.getDrawable(packageName, info.previewImage, null);
			if (drawable != null) {
				preview = ((BitmapDrawable)drawable).getBitmap();
			}
		}

		// Generate a preview image if we couldn't load one
		if (drawable == null) {
			
			try {
				Drawable icon = null;
				
				if (info.icon > 0) {
					icon = mPackageManager.getDrawable(packageName, info.icon, null);
				}
				if (icon == null) {
					preview = getDefaultPreview(itemInfo);
				} else {
					preview = createPreviewBitmap(icon, cellHSpan, cellVSpan);
					result.created = true;
				}

			} catch (Resources.NotFoundException e) {

			}
		}
		result.bitmap = preview;
		return result;
	}
	
	
	Bitmap createPreviewBitmap(Drawable d, int spanX, int spanY) {
		Bitmap preview = null;
		int sourceHeight = d.getIntrinsicHeight();
		int sourceWidth = d.getIntrinsicWidth();
		int width, height;
		height = width = mAppIconSize;
		
		final float ratio = (float) sourceWidth / sourceHeight;
        if (sourceWidth > sourceHeight) {
            height = (int) (width / ratio);
        } else if (sourceHeight > sourceWidth) {
            width = (int) (height * ratio);
        }
        int yoffset = (getViewHeight(spanY) - height)/2;
		preview = Bitmap.createBitmap(width, getViewHeight(spanY), Bitmap.Config.ARGB_8888);
		renderDrawableToBitmap(d, preview, 0, yoffset, width, height);
		return preview;
	}


	private Bitmap createDefaultWidgetPreview(int cellHSpan, int cellVSpan) {

		Bitmap preview = null;
		Resources resources = mLauncher.getResources();

		try {
			Drawable icon = null;
			icon = resources.getDrawable(R.drawable.ic_launcher_application);
			preview = createPreviewBitmap(icon, cellHSpan, cellVSpan);
			
		} catch (Resources.NotFoundException e) {

		}

		return preview;
	}

	private Bitmap getDefaultPreview(PendingAddItemInfo itemInfo) {
		if (mDefaultPreviews != null) {
			int spanY = itemInfo.spanY;
			if (mDefaultPreviews.length >= spanY && mDefaultPreviews[spanY - 1] != null
					&&!mDefaultPreviews[spanY - 1].isRecycled()) {
				return mDefaultPreviews[spanY - 1];
			}
		}
		return null;
	}

	private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h) {
		renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f);
	}

	private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h, float scale) {
		if (bitmap != null) {
			Canvas c = new Canvas(bitmap);
			c.scale(scale, scale);
			Rect oldBounds = d.copyBounds();
			d.setBounds(x, y, x + w, y + h);
			d.draw(c);
			d.setBounds(oldBounds); // Restore the bounds
			c.setBitmap(null);
		}
	}

	public void clear() {
		if (mImageLoader != null) {
			mImageLoader.clearCache();
			mImageLoader = null;
		}
		if (mDefaultPreviews != null) {
			int size = mDefaultPreviews.length;
			for (int i = 0; i < size; i++) {
				if (mDefaultPreviews[i] != null && !mDefaultPreviews[i].isRecycled()) {
					mDefaultPreviews[i].recycle();
				}
			}
			mDefaultPreviews = null;
		}
		if (mItems != null) {
		    mItems.clear();
		    mItems = null;
		}
		System.gc();
		// mLauncher = null;
	}

	class PreviewImageLoader {

		private MemoryCache mMemoryCache = new MemoryCache();
		private Map<ImageView, ComponentName> mImageViews = Collections
				.synchronizedMap(new WeakHashMap<ImageView, ComponentName>());

		private ExecutorService mExecutorService;

		public PreviewImageLoader(Context context) {
			mExecutorService = Executors.newFixedThreadPool(1);
		}

		public void DisplayImage(PendingAddItemInfo itemInfo, ImageView imageView, boolean isLoadOnlyFromCache) {
			mImageViews.put(imageView, itemInfo.componentName);

			Bitmap bitmap = mMemoryCache.get(itemInfo.componentName);
			if (bitmap != null && !bitmap.isRecycled())
				imageView.setImageBitmap(bitmap);
			else if (!isLoadOnlyFromCache) {
				Bitmap defaultPreview = getDefaultPreview(itemInfo);
				if (defaultPreview != null) {
					imageView.setImageBitmap(defaultPreview);
				}
				queueImage(itemInfo, imageView);
			}
		}

		private void queueImage(PendingAddItemInfo itemInfo, ImageView imageView) {
			ImageToLoad p = new ImageToLoad(itemInfo, imageView);
			mExecutorService.submit(new ImageLoadTask(p));
		}

		private MyBitmap getMyBitmap(PendingAddItemInfo itemInfo) {

			if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {

				return getShortcutPreview(((PendingAddShortcutInfo) itemInfo).shortcutActivityInfo, itemInfo.spanX,
						itemInfo.spanY);

			} else if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {

				return getWidgetPreview(itemInfo, itemInfo.spanX, itemInfo.spanY);
			}
			return null;
		}

		// Task for the queue
		private class ImageToLoad {
			public PendingAddItemInfo itemInfo;
			public ImageView imageView;

			public ImageToLoad(PendingAddItemInfo itemInfo, ImageView i) {
				this.itemInfo = itemInfo;
				imageView = i;
			}
		}

		class ImageLoadTask implements Runnable {
			private ImageToLoad mImageToLoad;

			ImageLoadTask(ImageToLoad imageToLoad) {
				this.mImageToLoad = imageToLoad;
			}

			@Override
			public void run() {
				if (mImageViews == null || mImageToLoad == null || imageViewReused(mImageToLoad))
					return;
				MyBitmap myBmp = getMyBitmap(mImageToLoad.itemInfo);
				if (myBmp == null) {
					return;
				}
				if (myBmp.created) {
					mMemoryCache.put(mImageToLoad.itemInfo.componentName, myBmp.bitmap);
				}
				if (imageViewReused(mImageToLoad))
					return;
				BitmapDisplayer bd = new BitmapDisplayer(myBmp.bitmap, mImageToLoad);
				Activity a = (Activity) mImageToLoad.imageView.getContext();
				a.runOnUiThread(bd);
				mImageToLoad = null;
			}
		}

		boolean imageViewReused(ImageToLoad imageToLoad) {
			ComponentName cn = mImageViews.get(imageToLoad.imageView);
			if (cn == null || !cn.equals(imageToLoad.itemInfo.componentName)) {
				return true;
			}
			return false;
		}

		class BitmapDisplayer implements Runnable {
			private Bitmap mBitmap;
			private ImageToLoad mImageToLoad;

			public BitmapDisplayer(Bitmap b, ImageToLoad p) {
				mBitmap = b;
				mImageToLoad = p;
			}

			public void run() {
				if (mImageToLoad == null || mImageViews == null || imageViewReused(mImageToLoad)) {
					return;
				}
				if (mBitmap != null) {
					mImageToLoad.imageView.setImageBitmap(mBitmap);
					mBitmap = null;
				}
				if (mImageToLoad != null) {
				    mImageToLoad.imageView = null;
				    mImageToLoad.itemInfo = null;
				}
			}
		}

		public void clearCache() {
			mMemoryCache.clear();
			if (mImageViews != null) {
			    mImageViews.clear();
			    mImageViews = null;
			}
			mExecutorService = null;
		}

	}

	class MyBitmap {
		Bitmap bitmap;
		boolean created;
		
		MyBitmap(Bitmap bitmap, boolean created) {
			this.bitmap = bitmap;
			this.created = created;
		}
	}
	
	class MemoryCache {

		private Map<ComponentName, Bitmap> mCache = Collections
				.synchronizedMap(new LinkedHashMap<ComponentName, Bitmap>(10, 1.5f, true));

		private long mSize = 0;// current allocated size

		private long mLimit = 1000000;// max memory in bytes

		public MemoryCache() {
			// use 25% of available heap size
			int cacheSize = CACHE_SIZE_LIMIT_PERCENT;
			if (CACHE_SIZE_LIMIT_PERCENT > 100) {
				cacheSize = 100;
			} else if (CACHE_SIZE_LIMIT_PERCENT < 1) {
				cacheSize = 1;
			}
			setLimit(Runtime.getRuntime().maxMemory() * cacheSize / 100);
		}

		public void setLimit(long new_limit) {
			mLimit = new_limit;
			if (DEBUG)
				Log.i(LOG_TAG, "MemoryCache will use up to " + mLimit / 1024. / 1024. + "MB");
		}

		public Bitmap get(ComponentName cn) {
			try {
				if (!mCache.containsKey(cn))
					return null;
				return mCache.get(cn);
			} catch (NullPointerException ex) {
				return null;
			}
		}

		public void put(ComponentName cn, Bitmap bitmap) {
			try {
				if (mCache.containsKey(cn)) {
					mSize -= getSizeInBytes(mCache.get(cn));
				}
				mCache.put(cn, bitmap);
				mSize += getSizeInBytes(bitmap);
				if (mSize >= CACHE_SIZE_LIMIT_PERCENT) {
					checkSize();
				}
			} catch (Throwable th) {
				th.printStackTrace();
			}
		}

		private void checkSize() {
			if (DEBUG)
				Log.i(LOG_TAG, "cache size=" + mSize + " length=" + mCache.size());
			if (mSize > mLimit) {
				Iterator<Entry<ComponentName, Bitmap>> iter = mCache.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<ComponentName, Bitmap> entry = iter.next();
					mSize -= getSizeInBytes(entry.getValue());
					recycle(entry.getValue());
					iter.remove();
					if (mSize <= mLimit)
						break;
				}
				if (DEBUG)
					Log.i(LOG_TAG, "GC cache. New size " + mCache.size());
			}
		}

		public void clear() {
			for (Entry<ComponentName, Bitmap> entry : mCache.entrySet()) {
				recycle(entry.getValue());
			}
			mCache.clear();
		}
		
		long getSizeInBytes(Bitmap bitmap) {
			if (bitmap == null)
				return 0;
			return bitmap.getRowBytes() * bitmap.getHeight();
		}
		
		void recycle(Bitmap bitmap) {
			if (bitmap == null || bitmap.isRecycled())
				return;
			bitmap.recycle();
		}
	}
	
	interface OnWidgetAddedListener{
		public void onAdded(PendingAddItemInfo itemInfo);
	}
	
	public boolean isThirdPartyWidget(PendingAddItemInfo addItemInfo ) {

		if(addItemInfo == null || addItemInfo.componentName == null) {
			return false;
		}
        return mLauncher.getPackageName().equals(addItemInfo.componentName.getPackageName()) 
                && "com.lewa.launcher.ThirdPartyWidgets".equals(addItemInfo.componentName.getClassName());
    }
	
	public boolean isSupportWidgetBind() {
		if( mIsSupportWidgetBind == null) {
			Intent intent = new Intent(ReflUtils.ACTION_APPWIDGET_BIND);
			ResolveInfo info = mPackageManager.resolveActivity(intent, 0);
			if (info == null) {
				mIsSupportWidgetBind =  false;
			} else {
				mIsSupportWidgetBind = true;
			}
		}
		return mIsSupportWidgetBind;
	}
}
