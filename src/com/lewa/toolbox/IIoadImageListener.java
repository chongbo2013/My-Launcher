package com.lewa.toolbox;

import android.graphics.Bitmap;

public interface IIoadImageListener {
	public void onLoadStart();
	public void onLoadComplete(Bitmap b);
	public void onLoadFail();
}
