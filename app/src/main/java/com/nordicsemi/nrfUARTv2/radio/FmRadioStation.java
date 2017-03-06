package com.nordicsemi.nrfUARTv2.radio;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

public class FmRadioStation {
    public static final String AUTHORITY = "com.nordicsemi.nrfUARTv2.radio.FmRadioContentProvider";
    static final String[] COLUMNS;
    private static final String CURRENT_STATION_NAME = "FmDfltSttnNm";
    private static final boolean DEFAULT_AF_ENABLED = false;
    private static final boolean DEFAULT_PSRT_ENABLED = false;
    private static final boolean DEFAULT_TA_ENABLED = false;
    public static final int RDS_SETTING_FREQ_AF = 2;
    public static final int RDS_SETTING_FREQ_PSRT = 1;
    public static final int RDS_SETTING_FREQ_TA = 3;
    public static final String RDS_SETTING_VALUE_DISABLED = "DISABLED";
    public static final String RDS_SETTING_VALUE_ENABLED = "ENABLED";
    public static final String STATION = "station";
    public static final int STATION_TYPE_CURRENT = 1;
    public static final int STATION_TYPE_FAVORITE = 2;
    private static final int STATION_TYPE_RDS_SETTING = 4;
    public static final int STATION_TYPE_SEARCHED = 3;
    private static final String TAG = "FmRx/Station";

    public static final class Station implements BaseColumns {
        public static final String COLUMN_STATION_FREQ = "COLUMN_STATION_FREQ";
        public static final String COLUMN_STATION_NAME = "COLUMN_STATION_NAME";
        public static final String COLUMN_STATION_TYPE = "COLUMN_STATION_TYPE";
        public static final Uri CONTENT_URI;

        static {
            CONTENT_URI = Uri.parse("content://com.nordicsemi.nrfUARTv2.radio.FmRadioContentProvider/station");
        }
    }

    static {
        String[] strArr = new String[STATION_TYPE_RDS_SETTING];
        strArr[0] = "_id";
        strArr[STATION_TYPE_CURRENT] = Station.COLUMN_STATION_NAME;
        strArr[STATION_TYPE_FAVORITE] = Station.COLUMN_STATION_FREQ;
        strArr[STATION_TYPE_SEARCHED] = Station.COLUMN_STATION_TYPE;
        COLUMNS = strArr;
    }

    public static void initFmDatabase(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Station.CONTENT_URI;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = Station.COLUMN_STATION_FREQ;
        String[] strArr2 = new String[STATION_TYPE_CURRENT];
        strArr2[0] = String.valueOf(STATION_TYPE_CURRENT);
        Cursor cur = contentResolver.query(uri, strArr, "COLUMN_STATION_TYPE=?", strArr2, null);
        if (cur != null) {
            try {
                if (!cur.moveToFirst()) {
                    ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
                    values.put(Station.COLUMN_STATION_NAME, CURRENT_STATION_NAME);
                    values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(FmRadioUtils.DEFAULT_STATION));
                    values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(STATION_TYPE_CURRENT));
                    context.getContentResolver().insert(Station.CONTENT_URI, values);
                }
                cur.close();
            } catch (Throwable th) {
                cur.close();
            }
        }
        int[] types = new int[]{STATION_TYPE_CURRENT, STATION_TYPE_FAVORITE, STATION_TYPE_SEARCHED};
        boolean[] enables = new boolean[]{DEFAULT_TA_ENABLED, DEFAULT_TA_ENABLED, DEFAULT_TA_ENABLED};
        for (int i = 0; i < types.length; i += STATION_TYPE_CURRENT) {
            contentResolver = context.getContentResolver();
            uri = Station.CONTENT_URI;
            strArr = new String[STATION_TYPE_CURRENT];
            strArr[0] = Station.COLUMN_STATION_NAME;
            cur = contentResolver.query(uri, strArr, "COLUMN_STATION_FREQ=" + String.valueOf(types[i]), null, null);
            if (cur != null) {
                try {
                    if (!cur.moveToFirst()) {
                        ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
                        values.put(Station.COLUMN_STATION_NAME, enables[i] ? RDS_SETTING_VALUE_ENABLED : RDS_SETTING_VALUE_DISABLED);
                        values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(types[i]));
                        values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(STATION_TYPE_RDS_SETTING));
                        context.getContentResolver().insert(Station.CONTENT_URI, values);
                    }
                    cur.close();
                } catch (Throwable th2) {
                    cur.close();
                }
            }
        }
        Log.d(TAG, "FmRadioStation.initFmDatabase");
    }

    public static void insertStationToDb(Context context, String stationName, int stationFreq, int stationType) {
        Log.d(TAG, "FmRadioStation.insertStationToDb start");
        ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
        values.put(Station.COLUMN_STATION_NAME, stationName);
        values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(stationFreq));
        values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(stationType));
        context.getContentResolver().insert(Station.CONTENT_URI, values);
        Log.d(TAG, "FmRadioStation.insertStationToDb end");
    }

    public static void updateStationToDb(Context context, String stationName, int oldStationFreq, int newStationFreq, int stationType) {
        ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
        values.put(Station.COLUMN_STATION_NAME, stationName);
        values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(newStationFreq));
        values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(stationType));
        String[] strArr = new String[STATION_TYPE_FAVORITE];
        strArr[0] = String.valueOf(oldStationFreq);
        strArr[STATION_TYPE_CURRENT] = String.valueOf(stationType);
        context.getContentResolver().update(Station.CONTENT_URI, values, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE=?", strArr);
        Log.d(TAG, "FmRadioStation.updateStationToDb: name = " + stationName + ", new freq = " + newStationFreq);
    }

    public static boolean isDefaultStation(Context context, int iStation) {
        return isStationExist(context, iStation, STATION_TYPE_SEARCHED);
    }

    public static void updateStationToDb(Context context, String newStationName, int newStationType, int stationFreq) {
        ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
        values.put(Station.COLUMN_STATION_NAME, newStationName);
        values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(stationFreq));
        values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(newStationType));
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = String.valueOf(stationFreq);
        context.getContentResolver().update(Station.CONTENT_URI, values, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE<>1", strArr);
        Log.d(TAG, "FmRadioStation.updateStationToDb: new name = " + newStationName + ", new freq type = " + newStationType);
    }

    public static void deleteStationInDb(Context context, int stationFreq, int stationType) {
        String[] strArr = new String[STATION_TYPE_FAVORITE];
        strArr[0] = String.valueOf(stationFreq);
        strArr[STATION_TYPE_CURRENT] = String.valueOf(stationType);
        context.getContentResolver().delete(Station.CONTENT_URI, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE=?", strArr);
        Log.d(TAG, "FmRadioStation.deleteStationInDb: freq = " + stationFreq + ", type = " + stationType);
    }

    public static boolean isStationExist(Context context, int stationFreq, int stationType) {
        Log.d(TAG, ">>> isStationExist: stationFreq=" + stationFreq + ",stationType=" + stationType);
        boolean isExist = DEFAULT_TA_ENABLED;
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Station.CONTENT_URI;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = Station.COLUMN_STATION_NAME;
        String[] strArr2 = new String[STATION_TYPE_FAVORITE];
        strArr2[0] = String.valueOf(stationFreq);
        strArr2[STATION_TYPE_CURRENT] = String.valueOf(stationType);
        Cursor cur = contentResolver.query(uri, strArr, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE=?", strArr2, null);
        if (cur != null) {
            try {
                if (cur.moveToFirst()) {
                    isExist = true;
                }
                cur.close();
            } catch (Throwable th) {
                cur.close();
            }
        }
        Log.d(TAG, "<<< isStationExist: " + isExist);
        return isExist;
    }

    public static boolean isStationExistInChList(Context context, int stationFreq) {
        Log.d(TAG, ">>> isStationExist: stationFreq=" + stationFreq);
        boolean isExist = DEFAULT_TA_ENABLED;
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Station.CONTENT_URI;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = Station.COLUMN_STATION_NAME;
        String[] strArr2 = new String[STATION_TYPE_CURRENT];
        strArr2[0] = String.valueOf(stationFreq);
        Cursor cur = contentResolver.query(uri, strArr, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE<>1", strArr2, null);
        if (cur != null) {
            try {
                if (cur.moveToFirst()) {
                    isExist = true;
                }
                cur.close();
            } catch (Throwable th) {
                cur.close();
            }
        }
        Log.d(TAG, "<<< isStationExist: " + isExist);
        return isExist;
    }

    public static int getCurrentStation(Context context) {
        int currentStation = FmRadioUtils.DEFAULT_STATION;
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Station.CONTENT_URI;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = Station.COLUMN_STATION_FREQ;
        String[] strArr2 = new String[STATION_TYPE_CURRENT];
        strArr2[0] = String.valueOf(STATION_TYPE_CURRENT);
        Cursor cur = contentResolver.query(uri, strArr, "COLUMN_STATION_TYPE=?", strArr2, null);
        if (cur != null) {
            try {
                if (cur.moveToFirst()) {
                    currentStation = cur.getInt(0);
                    if (!FmRadioUtils.isValidStation(currentStation)) {
                        currentStation = FmRadioUtils.DEFAULT_STATION;
                        setCurrentStation(context, FmRadioUtils.DEFAULT_STATION);
                        Log.w(TAG, "current station is invalid, use default!");
                    }
                }
                cur.close();
            } catch (Throwable th) {
                cur.close();
            }
        }
        Log.d(TAG, "FmRadioStation.getCurrentStation: " + currentStation);
        return currentStation;
    }

    public static void setCurrentStation(Context context, int station) {
        Log.d(TAG, "FmRadioStation.setCurrentStation start");
        ContentValues values = new ContentValues(STATION_TYPE_SEARCHED);
        values.put(Station.COLUMN_STATION_NAME, CURRENT_STATION_NAME);
        values.put(Station.COLUMN_STATION_FREQ, Integer.valueOf(station));
        values.put(Station.COLUMN_STATION_TYPE, Integer.valueOf(STATION_TYPE_CURRENT));
        String[] strArr = new String[STATION_TYPE_FAVORITE];
        strArr[0] = CURRENT_STATION_NAME;
        strArr[STATION_TYPE_CURRENT] = String.valueOf(STATION_TYPE_CURRENT);
        context.getContentResolver().update(Station.CONTENT_URI, values, "COLUMN_STATION_NAME=? AND COLUMN_STATION_TYPE=?", strArr);
        Log.d(TAG, "FmRadioStation.setCurrentStation end");
    }

    public static void cleanSearchedStations(Context context) {
        Log.d(TAG, "FmRadioStation.cleanSearchedStations start");
        context.getContentResolver().delete(Station.CONTENT_URI, "COLUMN_STATION_TYPE=" + String.valueOf(STATION_TYPE_SEARCHED), null);
        Log.d(TAG, "FmRadioStation.cleanSearchedStations end");
    }

    public static String getStationName(Context context, int stationFreq, int stationType) {
        Log.d(TAG, "FmRadioStation.getStationName: type = " + stationType + ", freq = " + stationFreq);
        String stationName = "";
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Station.CONTENT_URI;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = Station.COLUMN_STATION_NAME;
        String[] strArr2 = new String[STATION_TYPE_FAVORITE];
        strArr2[0] = String.valueOf(stationFreq);
        strArr2[STATION_TYPE_CURRENT] = String.valueOf(stationType);
        Cursor cur = contentResolver.query(uri, strArr, "COLUMN_STATION_FREQ=? AND COLUMN_STATION_TYPE=?", strArr2, null);
        if (cur != null) {
            try {
                if (cur.moveToFirst()) {
                    stationName = cur.getString(0);
                }
                cur.close();
            } catch (Throwable th) {
                cur.close();
            }
        }
        Log.d(TAG, "FmRadioStation.getStationName: stationName = " + stationName);
        return stationName;
    }

    public static boolean isFavoriteStation(Context context, int iStation) {
        return isStationExist(context, iStation, STATION_TYPE_FAVORITE);
    }

    public static int getStationCount(Context context, int stationType) {
        Log.d(TAG, "FmRadioStation.getStationCount Type: " + stationType);
        int stationNus = 0;
        String[] strArr = new String[STATION_TYPE_CURRENT];
        strArr[0] = String.valueOf(stationType);
        Cursor cur = context.getContentResolver().query(Station.CONTENT_URI, COLUMNS, "COLUMN_STATION_TYPE=?", strArr, null);
        if (cur != null) {
            try {
                stationNus = cur.getCount();
            } finally {
                cur.close();
            }
        }
        Log.d(TAG, "FmRadioStation.getStationCount: " + stationNus);
        return stationNus;
    }

    public static void cleanAllStations(Context context) {
        Cursor cur = context.getContentResolver().query(Station.CONTENT_URI, COLUMNS, null, null, null);
        if (cur != null) {
            try {
                cur.moveToFirst();
                while (!cur.isAfterLast()) {
                    context.getContentResolver().delete(ContentUris.appendId(Station.CONTENT_URI.buildUpon(), (long) cur.getInt(cur.getColumnIndex("_id"))).build(), null, null);
                    cur.moveToNext();
                }
            } finally {
                cur.close();
            }
        }
    }
}
