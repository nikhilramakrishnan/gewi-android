package com.nordicsemi.nrfUARTv2.radio;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class FmRadioContentProvider extends ContentProvider {
    private static final String DATABASE_NAME = "FmRadio.db";
    private static final int DATABASE_VERSION = 1;
    private static final int STATION_FREQ = 1;
    private static final int STATION_FREQ_ID = 2;
    private static final String TABLE_NAME = "StationList";
    private static final String TAG = "FmRx/Provider";
    private static final UriMatcher URI_MATCHER;
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mSqlDb;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, FmRadioContentProvider.DATABASE_NAME, null, FmRadioContentProvider.STATION_FREQ);
        }

        public void onCreate(SQLiteDatabase db) {
            Log.d(FmRadioContentProvider.TAG, "DatabaseHelper.onCreate");
            db.execSQL("Create table StationList(_id INTEGER PRIMARY KEY AUTOINCREMENT,COLUMN_STATION_NAME TEXT,COLUMN_STATION_FREQ INTEGER,COLUMN_STATION_TYPE INTEGER);");
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(FmRadioContentProvider.TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS StationList");
            onCreate(db);
        }
    }

    public FmRadioContentProvider() {
        this.mSqlDb = null;
        this.mDbHelper = null;
    }

    static {
        URI_MATCHER = new UriMatcher(-1);
        URI_MATCHER.addURI(FmRadioStation.AUTHORITY, FmRadioStation.STATION, STATION_FREQ);
        URI_MATCHER.addURI(FmRadioStation.AUTHORITY, "station/#", STATION_FREQ_ID);
    }

    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "FmRadioContentProvider.delete");
        this.mSqlDb = this.mDbHelper.getWritableDatabase();
        int rows;
        switch (URI_MATCHER.match(uri)) {
            case STATION_FREQ /*1*/:
                rows = this.mSqlDb.delete(TABLE_NAME, selection, selectionArgs);
                getContext().getContentResolver().notifyChange(uri, null);
                return rows;
            case STATION_FREQ_ID /*2*/:
                String str;
                String stationID = (String) uri.getPathSegments().get(STATION_FREQ);
                SQLiteDatabase sQLiteDatabase = this.mSqlDb;
                String str2 = TABLE_NAME;
                StringBuilder append = new StringBuilder().append("_id=").append(stationID);
                if (TextUtils.isEmpty(selection)) {
                    str = "";
                } else {
                    str = " AND (" + selection + ")";
                }
                rows = sQLiteDatabase.delete(str2, append.append(str).toString(), selectionArgs);
                getContext().getContentResolver().notifyChange(uri, null);
                return rows;
            default:
                Log.e(TAG, "Error: Unkown URI to delete: " + uri);
                return 0;
        }
    }

    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "FmRadioContentProvider.insert");
        this.mSqlDb = this.mDbHelper.getWritableDatabase();
        ContentValues v = new ContentValues(values);
        if (v.containsKey(FmRadioStation.Station.COLUMN_STATION_NAME) && v.containsKey(FmRadioStation.Station.COLUMN_STATION_FREQ) && v.containsKey(FmRadioStation.Station.COLUMN_STATION_TYPE)) {
            long rowId = this.mSqlDb.insert(TABLE_NAME, null, v);
            if (rowId <= 0) {
                Log.e(TAG, "Error: Failed to insert row into " + uri);
            }
            Uri rowUri = ContentUris.appendId(FmRadioStation.Station.CONTENT_URI.buildUpon(), rowId).build();
            getContext().getContentResolver().notifyChange(rowUri, null);
            return rowUri;
        }
        Log.e(TAG, "Error: Invalid values.");
        return null;
    }

    public boolean onCreate() {
        this.mDbHelper = new DatabaseHelper(getContext());
        return this.mDbHelper != null;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = this.mDbHelper.getReadableDatabase();
        qb.setTables(TABLE_NAME);
        if (STATION_FREQ_ID == URI_MATCHER.match(uri)) {
            qb.appendWhere("_id = " + ((String) uri.getPathSegments().get(STATION_FREQ)));
        }
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.d(TAG, "FmRadioContentProvider.update");
        this.mSqlDb = this.mDbHelper.getWritableDatabase();
        int rows;
        switch (URI_MATCHER.match(uri)) {
            case STATION_FREQ /*1*/:
                rows = this.mSqlDb.update(TABLE_NAME, values, selection, selectionArgs);
                getContext().getContentResolver().notifyChange(uri, null);
                return rows;
            case STATION_FREQ_ID /*2*/:
                String str;
                String stationID = (String) uri.getPathSegments().get(STATION_FREQ);
                SQLiteDatabase sQLiteDatabase = this.mSqlDb;
                String str2 = TABLE_NAME;
                StringBuilder append = new StringBuilder().append("_id=").append(stationID);
                if (TextUtils.isEmpty(selection)) {
                    str = "";
                } else {
                    str = " AND (" + selection + ")";
                }
                rows = sQLiteDatabase.update(str2, values, append.append(str).toString(), selectionArgs);
                getContext().getContentResolver().notifyChange(uri, null);
                return rows;
            default:
                Log.e(TAG, "Error: Unkown URI to update: " + uri);
                return 0;
        }
    }

    public String getType(Uri uri) {
        return null;
    }
}
