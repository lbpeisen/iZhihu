package com.gracecode.iZhihu.api;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.gracecode.iZhihu.BuildConfig;
import com.gracecode.iZhihu.util.Helper;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * <p/>
 * User: mingcheng
 * Date: 13-4-27
 */
public class Requester {
    private static final String TAG = Requester.class.getName();

    private static final String URL_DOMAIN = "http://z.ihu.im";
    private static final String URL_SYNC_REQUEST
            = URL_DOMAIN + "/?method=sync&timestamp=%s&sign=%s&start=%d&device=%s";
    private static final String URL_GET_FAVOURITES
            = URL_DOMAIN + "/?method=get-favourites&timestamp=%s&sign=%s&device=%s&platform=android";
    private static final String URL_SAVE_FAVOURITES
            = URL_DOMAIN + "/?method=save-favourites&timestamp=%s&sign=%s&device=%s&favourites=%s&platform=android";

    private static final String DEVICE_UUID = android.os.Build.SERIAL;
    private static final String APP_KEY = "133ff1e10a8b244767ef734fb86f37fd";
    public static final int DEFAULT_START_OFFSET = -1;
    private static final int TIME_STAMP_LENGTH = 10;
    private static final String KEY_LAST_QUERY_TIMESTAMP = "last_query_timestamp";
    private static final int HTTP_STATUS_OK = 200;
    private static final int TIMEOUT_SECONDS = 5;

    private static Context context;
    private final SharedPreferences sharedPreferences;

    public Requester(Context context) {
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(getClass().getName(), Context.MODE_PRIVATE);
    }

    void saveSharedPreference(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    String getSharedPreference(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    public Long getLastRequestTimeStamp() {
        String value = getSharedPreference(KEY_LAST_QUERY_TIMESTAMP, String.valueOf(0));
        return Long.parseLong(value);
    }

    private static String md5(String string) throws RuntimeException {
        byte[] hash;

        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Huh, MD5 should be supported?", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Huh, UTF-8 should be supported?", e);
        }

        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            int i = (b & 0xFF);
            if (i < 0x10) hex.append('0');
            hex.append(Integer.toHexString(i));
        }

        return hex.toString();
    }

    synchronized public JSONArray saveFavourites(Serializable favourites)
            throws IOException, NetworkErrorException, JSONException {

        return null;
    }


    synchronized public JSONArray sync(Integer offset) throws IOException, NetworkErrorException, JSONException {
        JSONObject jsonObject = requestJSONObjectFromServer(getSyncRequestUrl(offset));
        if (jsonObject.getInt("success") != 1) {
            throw new JSONException(jsonObject.getString("message"));
        }

        markRequestTimestamp(System.currentTimeMillis());
        return jsonObject.getJSONArray("data");
    }


    private JSONObject requestJSONObjectFromServer(String url)
            throws IOException, NetworkErrorException, JSONException {
        return new JSONObject(requestStringFromServer(url));
    }

    /**
     * Request string from server
     *
     * @param url
     * @return
     * @throws IOException
     * @throws NetworkErrorException
     */
    private String requestStringFromServer(String url) throws IOException, NetworkErrorException {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "The request URL is " + url);
        }

        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Platform", "Android");
        httpGet.addHeader("Accept-Encoding", "gzip, deflate");

        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

        HttpParams httpParams = defaultHttpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_SECONDS * 1000);
        HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_SECONDS * 1000);

        HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
        if (httpResponse.getStatusLine().getStatusCode() == HTTP_STATUS_OK) {

            InputStream instream = httpResponse.getEntity().getContent();
            Header contentEncoding = httpResponse.getFirstHeader("Content-Encoding");
            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                instream = new GZIPInputStream(instream);
            }

            String responseString = Helper.inputStream2String(instream);

            if (BuildConfig.DEBUG) {
                Log.v(context.getPackageName(), responseString);
            }

            return responseString;
        } else {
            throw new NetworkErrorException(httpResponse.getStatusLine().getStatusCode() + "");
        }
    }


    synchronized public JSONArray fetch() throws JSONException, IOException, NetworkErrorException {
        return sync(DEFAULT_START_OFFSET);
    }

    void markRequestTimestamp(Long timestamp) {
        saveSharedPreference(KEY_LAST_QUERY_TIMESTAMP, String.valueOf(timestamp));
    }

    public void clearRequestTimestamp() {
        markRequestTimestamp(0l);
    }

    private String getTimeStampString() {
        return String.valueOf(System.currentTimeMillis()).substring(0, TIME_STAMP_LENGTH);
    }


    private String getUrlSaveFavourites(Serializable data) {
        String timeStampString = getTimeStampString();
        String signString = getSignString(timeStampString, "save-favourites");

//        "/?method=save-favourites&timestamp=%s&sign=%s&device=%s&favourites=%s&platform=android";
        return String.format(URL_GET_FAVOURITES,
                timeStampString,
                signString,
                DEVICE_UUID,
                data.toString());
    }


    private String getUrlGetFavourites() {
        String timeStampString = getTimeStampString();
        String signString = getSignString(timeStampString, "get-favourites");

        //  "/?method=get-favourites&timestamp=%s&sign=%s&device=%s&platform=android";
        return String.format(URL_GET_FAVOURITES,
                timeStampString,
                signString,
                DEVICE_UUID);
    }

    private String getSyncRequestUrl(int offset) {
        String timeStampString = getTimeStampString();
        String signString = getSignString(timeStampString, "sync");

        return String.format(URL_SYNC_REQUEST,
                timeStampString,
                signString,
                offset,
                DEVICE_UUID);
    }

    private String getSignString(String stamp, String method) {
        return md5(APP_KEY + stamp + method);
    }
}
