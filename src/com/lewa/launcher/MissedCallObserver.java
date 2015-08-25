package com.lewa.launcher;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.CallLog.Calls;

public class MissedCallObserver extends ContentObserver {
    private Context mContext;

    public MissedCallObserver(Context context, Handler handler) {
        super(handler);
        
        mContext = context;
    }

    @Override
    public void onChange(boolean selfChange) {
        int missedCnt = getMissedCallCount();       
        Intent intent = new Intent();
        intent.setAction(MessageModel.UPDATE_REQUEST);
        intent.putExtra("type",MessageModel.TYPE_CALL);
        intent.putExtra("count", missedCnt);
        mContext.sendBroadcast(intent);
    }
    
    public int getMissedCallCount() {
        int missedCallCount = 0;
        Cursor callCursor = mContext.getContentResolver().query(
                Calls.CONTENT_URI,
                new String[] { Calls.NUMBER, Calls.TYPE, Calls.NEW }, null,
                null, Calls.DEFAULT_SORT_ORDER);
        if (callCursor != null) {
            int typeIndex = callCursor.getColumnIndex(Calls.TYPE);
            int newIndex = callCursor.getColumnIndex(Calls.NEW);
            while (callCursor.moveToNext()) {
                int type = callCursor.getInt(typeIndex);
                switch (type) {
                case Calls.MISSED_TYPE:
                    if (callCursor.getInt(newIndex) == 1) {
                        missedCallCount++;
                    }
                    break;
                }
            }
            //zwsun@lewatek.com PR945586 2015.03.18 start
            callCursor.close();
        }
        //zwsun@lewatek.com PR945586 2015.03.18 end
        return missedCallCount;
    }
}