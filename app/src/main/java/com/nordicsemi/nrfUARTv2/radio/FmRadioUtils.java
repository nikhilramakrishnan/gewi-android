package com.nordicsemi.nrfUARTv2.radio;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.Log;
import java.util.Locale;

public class FmRadioUtils {
    private static final int CONVERT_RATE = 10;
    public static final int DEFAULT_STATION = 1000;
    public static final float DEFAULT_STATION_FLOAT;
    private static final int HIGHEST_STATION = 1080;
    private static final boolean IS_FM_SHORT_ANTENNA_SUPPORT;
    private static final boolean IS_FM_SUSPEND_SUPPORT;
    private static final int LOWEST_STATION = 875;
    public static final long LOW_SPACE_THRESHOLD = 524288;
    private static final int STEP = 1;
    private static final String TAG = "FmRx/Utils";
    private static StorageManager sStorageManager;

    static {
        DEFAULT_STATION_FLOAT = computeFrequency(DEFAULT_STATION);
        sStorageManager = null;
        IS_FM_SHORT_ANTENNA_SUPPORT = Boolean.parseBoolean(System.getProperty("ro.mtk_fm_short_antenna_support"));
        IS_FM_SUSPEND_SUPPORT = Boolean.parseBoolean(System.getProperty("ro.mtk_tc1_fm_at_suspend"));
    }

    public static boolean isValidStation(int station) {
        boolean isValid = (station < LOWEST_STATION || station > HIGHEST_STATION) ? IS_FM_SUSPEND_SUPPORT : true;
        Log.v(TAG, "isValidStation: freq = " + station + ", valid = " + isValid);
        return isValid;
    }

    public static int computeIncreaseStation(int station) {
        int result = station + STEP;
        if (result > HIGHEST_STATION) {
            return LOWEST_STATION;
        }
        return result;
    }

    public static int computeDecreaseStation(int station) {
        int result = station - 1;
        if (result < LOWEST_STATION) {
            return HIGHEST_STATION;
        }
        return result;
    }

    public static int computeStation(float frequency) {
        return (int) (10.0f * frequency);
    }

    public static float computeFrequency(int station) {
        return ((float) station) / 10.0f;
    }

    public static String formatStation(int station) {
        Object[] objArr = new Object[STEP];
        objArr[0] = Float.valueOf(((float) station) / 10.0f);
        return String.format(Locale.ENGLISH, "%.1f", objArr);
    }

    public static String getDefaultStoragePath() {
        return Environment.getExternalStorageDirectory().getPath();
    }


    private static void ensureStorageManager(Context context) {
        if (sStorageManager == null) {
            sStorageManager = (StorageManager) context.getSystemService("storage");
        }
    }

    public static boolean hasEnoughSpace(String recordingSdcard) {
        try {
            StatFs fs = new StatFs(recordingSdcard);
            long spaceLeft = ((long) fs.getAvailableBlocks()) * ((long) fs.getBlockSize());
            Log.d(TAG, "hasEnoughSpace: available space=" + spaceLeft);
            return spaceLeft > LOW_SPACE_THRESHOLD ? true : IS_FM_SUSPEND_SUPPORT;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "sdcard may be unmounted:" + recordingSdcard);
            return IS_FM_SUSPEND_SUPPORT;
        }
    }

    public static boolean isFmShortAntennaSupport() {
        return IS_FM_SHORT_ANTENNA_SUPPORT;
    }

    public static boolean isFmSuspendSupport() {
        return IS_FM_SUSPEND_SUPPORT;
    }
}
