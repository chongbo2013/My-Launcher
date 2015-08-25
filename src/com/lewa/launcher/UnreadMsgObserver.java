package com.lewa.launcher;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

public class UnreadMsgObserver extends ContentObserver {
    private Context mContext;
    private static final String SMS_UNREAD_SELECTION = "(type=1 AND read=0)";    
    // 130 : PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND
    // 132 : PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
    public static final String MMS_UNREAD_SELECTION = "(msg_box = 1  AND read = 0 AND (m_type=130 OR m_type=132) AND thread_id in ( select _id from threads))";
    public static final Uri MMS_CONTENT_URI = Uri.parse("content://mms/inbox");
    public static final Uri SMS_CONTENT_URI = Uri.parse("content://sms/inbox");
    
    public UnreadMsgObserver(Context context, Handler handler) {
        super(handler);

        mContext = context;
    }

    @Override
    public void onChange(boolean selfChange) {
        int totalCnt = getSmsUnreadCnt() + getMmsUnreadCnt();
        Intent intent = new Intent();
        intent.setAction(MessageModel.UPDATE_REQUEST);
        intent.putExtra("type", MessageModel.TYPE_MESSAGE);
        intent.putExtra("count", totalCnt);
        mContext.sendBroadcast(intent);
    }
    
    public int getSmsUnreadCnt() {
        int smsCnt = 0;
        try {
            Cursor smsCur = mContext.getContentResolver().query(SMS_CONTENT_URI, null, SMS_UNREAD_SELECTION, null, null);
            if (smsCur != null) {
                try {
                    smsCnt = smsCur.getCount();
                } finally {
                    smsCur.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return smsCnt;
    }

    public int getMmsUnreadCnt() {
        int mmsCnt = 0;
        try {
            Cursor mmsCur = mContext.getContentResolver().query(MMS_CONTENT_URI, null, MMS_UNREAD_SELECTION, null, null);
            if (mmsCur != null) {
                try {
                    mmsCnt = mmsCur.getCount();
                } finally {
                    mmsCur.close();
                }
            }
        } catch (Exception e) { //pr70370,this exception maybe some else update the db and forget to close,so try catch it to avoid fc
            e.printStackTrace();
        }
        return mmsCnt;
    }
}