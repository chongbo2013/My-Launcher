package lewa.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

public class SousouUtils {
	
    public static int                    connectTimeout                = 5000;
    public static int                    readTimeout                   = 0;
    public static final SimpleDateFormat GMT_FORMAT               = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z",
            Locale.ENGLISH);

	/**
     * @see {@link #httpPost(Context, String, String)}
     * @param context
     * @param httpUrl
     * @param parasMap paras map, key is para name, value is para value. will be transfrom to String by
     * {@link SousouUtils#getParas(Map)}
     * @return
     */
    public static String httpPost(Context context, String httpUrl,
                                        Map<String, String> parasMap) {
        return httpPost(context, httpUrl, getParas(parasMap));
    }
    
    
    /**
     * join paras
     * 
     * @param parasMap paras map, key is para name, value is para value
     * @return
     */
    public static String getParas(Map<String, String> parasMap) {
        if (parasMap == null || parasMap.size() == 0) {
            return null;
        }

        StringBuilder paras = new StringBuilder();
        Iterator<Map.Entry<String, String>> ite = parasMap.entrySet().iterator();
        while (ite.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry<String, String>)ite.next();
            paras.append(entry.getKey()).append("=").append(entry.getValue());
            if (ite.hasNext()) {
                paras.append("&");
            }
        }
        return paras.toString();
    }
    
    
    /**
     * http post
     * <ul>
     * <li>use gzip compression default</li>
     * <li>use bufferedReader to improve the reading speed</li>
     * </ul>
     * 
     * @param context
     * @param httpUrl
     * @param paras
     * @return {@link HttpResponse#response} is response content, {@link HttpResponse#expires} is expires time
     */
    public static String httpPost(Context context, String httpUrl, String paras) {
        if (isEmpty(httpUrl) || paras == null) {
            return null;
        }

        BufferedReader input = null;
        StringBuilder sb = null;
        String expires = null;
        URL url = null;
        HttpURLConnection con = null;
        try {
            url = new URL(httpUrl);
            try {
                // default gzip encode
                con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                if (connectTimeout != 0) {
                    con.setConnectTimeout(connectTimeout);
                }
                if (readTimeout != 0) {
                    con.setReadTimeout(readTimeout);
                }
                con.getOutputStream().write(paras.getBytes());
                input = new BufferedReader(new InputStreamReader(con.getInputStream()));
                sb = new StringBuilder();
                String s;
                while ((s = input.readLine()) != null) {
                    sb.append(s).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } finally {
            // close buffered
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // disconnecting releases the resources held by a connection so they may be closed or reused
            if (con != null) {
                con.disconnect();
            }
        }
        return sb == null ? null : sb.toString();
    }
    
    
    /**
     * http get
     * <ul>
     * <li>use {@link LewaUtils#getUserAgent(Context)} as user-agent</li>
     * <li>use gzip compression default</li>
     * <li>use bufferedReader to improve the reading speed</li>
     * </ul>
     * 
     * @param context
     * @param httpUrl
     * @return {@link HttpResponse#response} is response content, {@link HttpResponse#expires} is expires time
     */
    public static String[] httpGet(String httpUrl) {
        BufferedReader input = null;
        StringBuilder sb = null;
        int responseCode = -1;
        String expires = null;
        URL url = null;
        HttpURLConnection con = null;
        try {
            url = new URL(httpUrl);
            try {
                // default gzip encode
                con = (HttpURLConnection)url.openConnection();
//                con.setRequestProperty("User-Agent", LewaUtils.getUserAgent(context));
                if (connectTimeout != 0) {
                    con.setConnectTimeout(connectTimeout);
                }
                if (readTimeout != 0) {
                    con.setReadTimeout(readTimeout);
                }
                input = new BufferedReader(new InputStreamReader(con.getInputStream()));
                responseCode = con.getResponseCode();
                sb = new StringBuilder();
                String s;
                while ((s = input.readLine()) != null) {
                    sb.append(s).append("\n");
                }
                expires = con.getHeaderField("Expires");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } finally {
            // close buffered
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // disconnecting releases the resources held by a connection so they may be closed or reused
            if (con != null) {
                con.disconnect();
            }
        }

        return new String[]{sb == null ? "" : sb.toString(), expires==null?"":expires};
    }
    
    
    public static boolean isEmpty(String str) {
        return (str == null || str.length() == 0);
    }
    
    public static boolean isConnected(Context context){
    	ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = connectivityManager.getActiveNetworkInfo(); 
		
		if(info != null)
		{
			return info.isAvailable();
		}
		return false;
    }
    
    public static boolean isPhoneNum(String num){
		if(!TextUtils.isEmpty(num)){
			num = num.replaceAll("[^0-9]", "");
			String exp_mobile="^((13[0-9])|(15[^4,\\D])|(18[0-2,5-9]))\\d{8}$";
			String exp_tel="^(\\(\\d{3,4}\\)|\\d{3,4}-?)?\\d{7,8}$";
			Pattern pattern_mobile=Pattern.compile(exp_mobile);
			Pattern pattern_tel=Pattern.compile(exp_tel);
			Matcher matcher_mobile=pattern_mobile.matcher(num);
			Matcher matcher_tel=pattern_tel.matcher(num);
			return matcher_mobile.matches()||matcher_tel.matches();
		}else{
		    return false;
		}
	}
    
    public static boolean isEmail(String s){
		if(!TextUtils.isEmpty(s)){
			String rex ="^\\s*\\w+(?:\\.{0,1}[\\w-]+)*@[a-zA-Z0-9]+(?:[-.][a-zA-Z0-9]+)*\\.[a-zA-Z]+\\s*$";
			Pattern pattern=Pattern.compile(rex);
			Matcher matcher=pattern.matcher(s);
			return matcher.matches();
		}else{
			return false;
		}
	}
    
    
    /**
     * parse gmt time to long
     * 
     * @param gmtTime likes Thu, 11 Apr 2013 10:20:30 GMT
     * @return -1 represents exception
     */
    public static long parseGmtTime(String gmtTime) {
        try {
            return GMT_FORMAT.parse(gmtTime).getTime();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }
    
}
