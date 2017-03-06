package com.nordicsemi.nrfUARTv2.radio;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

public class FmRadioService extends Service {
    public static final String ACTION_FROMATVSERVICE_POWERUP = "com.mediatek.app.mtv.POWER_ON";
    public static final String ACTION_TOATVSERVICE_POWERDOWN = "com.mediatek.app.mtv.ACTION_REQUEST_SHUTDOWN";
    public static final String ACTION_TOFMSERVICE_POWERDOWN = "com.nordicsemi.nrfUARTv2.radio.FMRadioService.ACTION_TOFMSERVICE_POWERDOWN";
    public static final String ACTION_TOFMTXSERVICE_POWERDOWN = "com.mediatek.FMTransmitter.FMTransmitterService.ACTION_TOFMTXSERVICE_POWERDOWN";
    public static final String ACTION_TOMUSICSERVICE_POWERDOWN = "com.android.music.musicservicecommand.pause";
    private static final String CMDPAUSE = "pause";
    private static final int CURRENT_RX_ON = 0;
    private static final int CURRENT_TX_ON = 1;
    private static final int CURRENT_TX_SCAN = 2;
    private static final String FM_FREQUENCY = "frequency";
    private static final int FOR_PROPRIETARY = 1;
    private static final int HEADSET_PLUG_IN = 1;
    private static final int NOTIFICATION_ID = 1;
    private static final String OPTION = "option";
    private static final int RDS_EVENT_AF = 128;
    private static final int RDS_EVENT_LAST_RADIOTEXT = 64;
    private static final int RDS_EVENT_PROGRAMNAME = 8;
    private static final String RECODING_FILE_NAME = "name";
    private static final boolean SHORT_ANNTENNA_SUPPORT;
    private static final String SOUND_POWER_DOWN_MSG = "com.android.music.musicservicecommand";
    private static final String TAG = "FmRx/Service";
    private static boolean sActivityIsOnStop;
    private static OnExitListener sExitListener;
    private static String sRecordingSdcard;
    private int[] defaultChannels;
    private ActivityManager mActivityManager;
    private final OnAudioFocusChangeListener mAudioFocusChangeListener;
    private AudioManager mAudioManager;
    private final IBinder mBinder;
    private FmServiceBroadcastReceiver mBroadcastReceiver;
    private Context mContext;
    private int mCurrentStation;
    private MediaPlayer mFmPlayer;
    private FmRadioServiceHandler mFmServiceHandler;
    private int mForcedUseForMedia;
    private boolean mIsAFEnabled;
    private boolean mIsAudioFocusHeld;
    private boolean mIsDeviceOpen;
    private boolean mIsInRecordingMode;
    private boolean mIsMakePowerDown;
    private boolean mIsNativeScanning;
    private boolean mIsNativeSeeking;
    private boolean mIsPSRTEnabled;
    private boolean mIsPowerUp;
    private boolean mIsPowerUping;
    private boolean mIsRdsThreadExit;
    private boolean mIsScanning;
    private boolean mIsSeeking;
    private boolean mIsServiceInited;
    private boolean mIsSpeakerUsed;
    private boolean mIsStopScanCalled;
    private String mLRTextString;
    private String mModifiedRecordingName;
    private String mPSString;
    private boolean mPausedByTransientLossOfFocus;
    private final OnErrorListener mPlayerErrorListener;
    private Thread mRdsThread;
    private int mRecordState;
    private int mRecorderErrorType;
    private ArrayList<Record> mRecords;
    private BroadcastReceiver mSdcardListener;
    private HashMap<String, Boolean> mSdcardStateMap;
    private Object mStopRecordingLock;
    private int mValueHeadSetPlug;
    private WakeLock mWakeLock;

    public interface OnExitListener {
        void onExit();
    }

    /* renamed from: com.mediatek.fmradio.FmRadioService.1 */
    class C00141 extends Thread {
        C00141() {
        }

        public void run() {
            Log.d(FmRadioService.TAG, ">>> RDS Thread run()");
            while (!FmRadioService.this.mIsRdsThreadExit) {
                int iRdsEvents = FmRadioNative.readRds();
                if (iRdsEvents != 0) {
                    Log.d(FmRadioService.TAG, "FmRadioNative.readrds events: " + iRdsEvents);
                }
                if (FmRadioService.RDS_EVENT_PROGRAMNAME == (iRdsEvents & FmRadioService.RDS_EVENT_PROGRAMNAME)) {
                    Log.d(FmRadioService.TAG, "RDS_EVENT_PROGRAMNAME");
                    byte[] bytePS = FmRadioNative.getPs();
                    if (bytePS != null) {
                        FmRadioService.this.setPS(new String(bytePS).trim());
                    }
                }
                if (FmRadioService.RDS_EVENT_LAST_RADIOTEXT == (iRdsEvents & FmRadioService.RDS_EVENT_LAST_RADIOTEXT)) {
                    Log.d(FmRadioService.TAG, "RDS_EVENT_LAST_RADIOTEXT");
                    byte[] byteLRText = FmRadioNative.getLrText();
                    if (byteLRText != null) {
                        FmRadioService.this.setLRText(new String(byteLRText).trim());
                    }
                }
                if (FmRadioService.RDS_EVENT_AF == (iRdsEvents & FmRadioService.RDS_EVENT_AF)) {
                    Log.d(FmRadioService.TAG, "RDS_EVENT_AF");
                    if (FmRadioService.this.mIsScanning || FmRadioService.this.mIsSeeking) {
                        Log.d(FmRadioService.TAG, "RDSThread. seek or scan going, no need to tune here");
                    } else if (FmRadioService.this.mIsPowerUp) {
                        int iFreq = FmRadioNative.activeAf();
                        if (FmRadioUtils.isValidStation(iFreq)) {
                            if (FmRadioService.this.mCurrentStation == iFreq) {
                                Log.w(FmRadioService.TAG, "RDSThread. the new freq is the same as current.");
                            } else {
                                FmRadioService.this.setPS("");
                                FmRadioService.this.setLRText("");
                                if (!(FmRadioService.this.mIsScanning || FmRadioService.this.mIsSeeking)) {
                                    Log.d(FmRadioService.TAG, "RDSThread. seek or scan not going,need to tune here");
                                    FmRadioService.this.tuneStationAsync(FmRadioUtils.computeFrequency(iFreq));
                                }
                            }
                        }
                    } else {
                        Log.d(FmRadioService.TAG, "RDSThread. fm is power down, do nothing.");
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d(FmRadioService.TAG, "<<< RDS Thread run()");
        }
    }

    /* renamed from: com.mediatek.fmradio.FmRadioService.2 */
    class C00152 implements OnErrorListener {
        C00152() {
        }

        public boolean onError(MediaPlayer mp, int what, int extra) {
            if (100 == what) {
                Log.d(FmRadioService.TAG, "onError: MEDIA_SERVER_DIED");
                if (FmRadioService.this.mFmPlayer != null) {
                    FmRadioService.this.mFmPlayer.release();
                    FmRadioService.this.mFmPlayer = null;
                }
                FmRadioService.this.mFmPlayer = new MediaPlayer();
                if (!FmRadioUtils.isFmSuspendSupport()) {
                    FmRadioService.this.mFmPlayer.setWakeMode(FmRadioService.this, FmRadioService.NOTIFICATION_ID);
                }
                FmRadioService.this.mFmPlayer.setOnErrorListener(FmRadioService.this.mPlayerErrorListener);
                try {
                    FmRadioService.this.mFmPlayer.setDataSource("THIRDPARTY://MEDIAPLAYER_PLAYERTYPE_FM");
                    FmRadioService.this.mFmPlayer.setAudioStreamType(3);
                    if (FmRadioService.this.mIsPowerUp) {
                        FmRadioService.this.setSpeakerPhoneOn(FmRadioService.this.mIsSpeakerUsed);
                        FmRadioService.this.mFmPlayer.prepare();
                        if (FmRadioUtils.isFmSuspendSupport()) {
                            Log.d(FmRadioService.TAG, "support FM suspend");
                            FmRadioService.this.mFmPlayer.start();
                        } else {
                            FmRadioService.this.mFmPlayer.start();
                        }
                    }
                } catch (IOException ex) {
                    Log.e(FmRadioService.TAG, "setDataSource: " + ex);
                    return FmRadioService.SHORT_ANNTENNA_SUPPORT;
                } catch (IllegalArgumentException ex2) {
                    Log.e(FmRadioService.TAG, "setDataSource: " + ex2);
                    return FmRadioService.SHORT_ANNTENNA_SUPPORT;
                } catch (IllegalStateException ex3) {
                    Log.e(FmRadioService.TAG, "setDataSource: " + ex3);
                    return FmRadioService.SHORT_ANNTENNA_SUPPORT;
                }
            }
            return true;
        }
    }

    /* renamed from: com.mediatek.fmradio.FmRadioService.3 */
    class C00163 implements OnAudioFocusChangeListener {
        C00163() {
        }

        public void onAudioFocusChange(int focusChange) {
            Log.d(FmRadioService.TAG, "onAudioFocusChange: " + focusChange);
            switch (focusChange) {
                case -2:
                    synchronized (this) {
                        FmRadioService.this.mAudioManager.setParameters("AudioFmPreStop=1");
                        FmRadioService.this.setMute(true);
                        Log.d(FmRadioService.TAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                        FmRadioService.this.stopFmFocusLoss(-2);
                        break;
                    }
                case FmRadioService.NOTIFICATION_ID /*1*/:
                    synchronized (this) {
                        Log.d(FmRadioService.TAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                        FmRadioService.this.updateAudioFocusAync(FmRadioService.NOTIFICATION_ID);
                        break;
                    }
                default:
                    Log.d(FmRadioService.TAG, "AudioFocus: Audio focus change, but not need handle");
            }
        }
    }

    class FmRadioServiceHandler extends Handler {
        public FmRadioServiceHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            Bundle bundle;
            switch (msg.what) {
                case FmRadioListener.MSGID_POWERUP_FINISHED /*9*/:
                    FmRadioService.this.handlePowerUp(msg.getData());
                case FmRadioListener.MSGID_POWERDOWN_FINISHED /*10*/:
                    FmRadioService.this.handlePowerDown();
                case FmRadioListener.MSGID_FM_EXIT /*11*/:
                    if (FmRadioService.this.mIsSpeakerUsed) {
                        FmRadioService.this.setSpeakerPhoneOn(FmRadioService.SHORT_ANNTENNA_SUPPORT);
                    }
                    FmRadioService.this.powerDown();
                    FmRadioService.this.closeDevice();
                    if (FmRadioService.this.mFmPlayer != null) {
                        FmRadioService.this.mFmPlayer.release();
                        FmRadioService.this.mFmPlayer = null;
                    }
                    bundle = new Bundle(FmRadioService.NOTIFICATION_ID);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, 11);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                    if (FmRadioService.sExitListener != null) {
                        FmRadioService.sExitListener.onExit();
                    }
                case FmRadioListener.MSGID_SCAN_FINISHED /*13*/:
                    int[] result;
                    int[] channels = null;
                    int scanTuneStation = FmRadioService.CURRENT_RX_ON;
                    boolean isScan = true;
                    FmRadioService.this.mIsScanning = true;
                    if (FmRadioService.this.powerUpFm(FmRadioUtils.DEFAULT_STATION_FLOAT)) {
                        channels = FmRadioService.this.startScan();
                    }
                    if (channels == null || channels[FmRadioService.CURRENT_RX_ON] != -100) {
                        result = FmRadioService.this.insertSearchedStation(channels);
                        scanTuneStation = result[FmRadioService.CURRENT_RX_ON];
                        if (!FmRadioService.this.tuneStation(FmRadioUtils.computeFrequency(scanTuneStation))) {
                            scanTuneStation = FmRadioService.this.mCurrentStation;
                        }
                    } else {
                        Log.d(FmRadioService.TAG, "user canceled scan:channels[0]=" + channels[FmRadioService.CURRENT_RX_ON]);
                        isScan = FmRadioService.SHORT_ANNTENNA_SUPPORT;
                        int i = FmRadioService.CURRENT_TX_SCAN;
                        result = new int[]{-1, FmRadioService.CURRENT_RX_ON};
                    }
                    if (FmRadioService.this.mIsAudioFocusHeld) {
                        Log.d(FmRadioService.TAG, "there is not power down command.set mute false");
                        FmRadioService.this.setMute(FmRadioService.SHORT_ANNTENNA_SUPPORT);
                    }
                    bundle = new Bundle(4);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, 13);
                    bundle.putInt(FmRadioListener.KEY_TUNE_TO_STATION, scanTuneStation);
                    bundle.putInt(FmRadioListener.KEY_STATION_NUM, result[FmRadioService.NOTIFICATION_ID]);
                    bundle.putBoolean(FmRadioListener.KEY_IS_SCAN, isScan);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                    FmRadioService.this.mIsScanning = FmRadioService.SHORT_ANNTENNA_SUPPORT;
                case FmRadioListener.MSGID_TUNE_FINISHED /*15*/:
                    float tuneStation = msg.getData().getFloat(FmRadioService.FM_FREQUENCY);
                    boolean isTune = FmRadioService.this.tuneStation(tuneStation);
                    if (!isTune) {
                        tuneStation = FmRadioUtils.computeFrequency(FmRadioService.this.mCurrentStation);
                    }
                    bundle = new Bundle(4);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, 15);
                    bundle.putBoolean(FmRadioListener.KEY_IS_TUNE, isTune);
                    bundle.putFloat(FmRadioListener.KEY_TUNE_TO_STATION, tuneStation);
                    bundle.putBoolean(FmRadioListener.KEY_IS_POWER_UP, FmRadioService.this.mIsPowerUp);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                case FmRadioListener.MSGID_SEEK_FINISHED /*16*/:
                    bundle = msg.getData();
                    FmRadioService.this.mIsSeeking = true;
                    float seekStation = FmRadioService.this.seekStation(bundle.getFloat(FmRadioService.FM_FREQUENCY), bundle.getBoolean(FmRadioService.OPTION));
                    boolean isSeekTune = FmRadioService.SHORT_ANNTENNA_SUPPORT;
                    if (FmRadioUtils.isValidStation(FmRadioUtils.computeStation(seekStation))) {
                        isSeekTune = FmRadioService.this.tuneStation(seekStation);
                    }
                    if (!isSeekTune) {
                        seekStation = FmRadioUtils.computeFrequency(FmRadioService.this.mCurrentStation);
                    }
                    bundle = new Bundle(FmRadioService.CURRENT_TX_SCAN);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, 15);
                    bundle.putBoolean(FmRadioListener.KEY_IS_TUNE, isSeekTune);
                    bundle.putFloat(FmRadioListener.KEY_TUNE_TO_STATION, seekStation);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                    FmRadioService.this.mIsSeeking = FmRadioService.SHORT_ANNTENNA_SUPPORT;
                case FmRadioListener.MSGID_ACTIVE_AF_FINISHED /*18*/:
                    FmRadioService.this.activeAF();
                case FmRadioListener.MSGID_STARTPLAYBACK_FINISHED /*24*/:
                    if (!FmRadioService.this.startPlayback()) {
                        bundle = new Bundle(FmRadioService.CURRENT_TX_SCAN);
                        bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_RECORDERROR);
                        bundle.putInt(FmRadioListener.KEY_RECORDING_ERROR_TYPE, 33);
                        FmRadioService.this.notifyActivityStateChanged(bundle);
                    }
                case FmRadioListener.MSGID_AUDIOFOCUS_CHANGED /*30*/:
                    FmRadioService.this.updateAudioFocus(msg.getData().getInt(FmRadioListener.KEY_AUDIOFOCUS_CHANGED));
                default:
            }
        }
    }

    private class FmServiceBroadcastReceiver extends BroadcastReceiver {
        private FmServiceBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            Log.d(FmRadioService.TAG, ">>> FmRadioService.onReceive");
            String action = intent.getAction();
            String command = intent.getStringExtra("command");
            Log.d(FmRadioService.TAG, "Action/Command: " + action + " / " + command);
            if (FmRadioService.ACTION_TOFMSERVICE_POWERDOWN.equals(action) || FmRadioService.ACTION_FROMATVSERVICE_POWERUP.equals(action) || (FmRadioService.SOUND_POWER_DOWN_MSG.equals(action) && FmRadioService.CMDPAUSE.equals(command))) {
                FmRadioService.this.mFmServiceHandler.removeCallbacksAndMessages(null);
                Log.d(FmRadioService.TAG, "onReceive.SOUND_POWER_DOWN_MSG. exit FM");
                FmRadioService.this.exitFm();
                FmRadioService.this.stopSelf();
            } else if ("android.intent.action.ACTION_SHUTDOWN".equals(action)) {
                FmRadioService.this.mFmServiceHandler.removeCallbacksAndMessages(null);
                FmRadioService.this.exitFm();
            } else if ("android.intent.action.SCREEN_ON".equals(action)) {
                FmRadioService.this.setRdsAsync(true);
            } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                FmRadioService.this.setRdsAsync(FmRadioService.SHORT_ANNTENNA_SUPPORT);
            } else if ("android.intent.action.HEADSET_PLUG".equals(action)) {
                FmRadioService.this.mValueHeadSetPlug = intent.getIntExtra("state", -1) == FmRadioService.NOTIFICATION_ID ? FmRadioService.CURRENT_RX_ON : FmRadioService.NOTIFICATION_ID;
                FmRadioService.this.switchAntennaAsync(FmRadioService.this.mValueHeadSetPlug);
                Bundle bundle;
                if (FmRadioService.SHORT_ANNTENNA_SUPPORT) {
                    Log.d(FmRadioService.TAG, "onReceive.switch anntenna:isWitch:" + (FmRadioService.this.switchAntenna(FmRadioService.this.mValueHeadSetPlug) == 0 ? true : FmRadioService.SHORT_ANNTENNA_SUPPORT));
                    boolean plugInEarphone = FmRadioService.this.mValueHeadSetPlug == 0 ? true : FmRadioService.SHORT_ANNTENNA_SUPPORT;
                    if (plugInEarphone) {
                        FmRadioService.this.mForcedUseForMedia = FmRadioService.CURRENT_RX_ON;
                        FmRadioService.this.mIsSpeakerUsed = FmRadioService.SHORT_ANNTENNA_SUPPORT;
                    }
                    bundle = new Bundle(FmRadioService.CURRENT_TX_SCAN);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_SPEAKER_MODE_CHANGED);
                    bundle.putBoolean(FmRadioListener.KEY_IS_SPEAKER_MODE, !plugInEarphone ? true : FmRadioService.SHORT_ANNTENNA_SUPPORT);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                    FmRadioService.this.powerUpAutoIfNeed();
                } else if (!FmRadioService.this.mIsServiceInited) {
                    Log.d(FmRadioService.TAG, "onReceive.switch anntenna:service is not init");
                    FmRadioService.this.powerUpAutoIfNeed();
                    return;
                } else if (FmRadioService.this.mValueHeadSetPlug == 0 && FmRadioService.this.isActivityForeground()) {
                    Log.d(FmRadioService.TAG, "onReceive.switch anntenna:need auto power up");
                    FmRadioService.this.powerUpAsync(FmRadioUtils.computeFrequency(FmRadioService.this.mCurrentStation));
                } else if (FmRadioService.NOTIFICATION_ID == FmRadioService.this.mValueHeadSetPlug) {
                    Log.d(FmRadioService.TAG, "plug out earphone, need to stop fm");
                    FmRadioService.this.setMute(true);
                    FmRadioService.this.mFmServiceHandler.removeMessages(13);
                    FmRadioService.this.mFmServiceHandler.removeMessages(16);
                    FmRadioService.this.mFmServiceHandler.removeMessages(15);
                    FmRadioService.this.mFmServiceHandler.removeMessages(10);
                    FmRadioService.this.mFmServiceHandler.removeMessages(9);
                    FmRadioService.this.stopFmFocusLoss(-1);
                    FmRadioService.this.setSpeakerPhoneOn(FmRadioService.SHORT_ANNTENNA_SUPPORT);
                    bundle = new Bundle(FmRadioService.CURRENT_TX_SCAN);
                    bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_SPEAKER_MODE_CHANGED);
                    bundle.putBoolean(FmRadioListener.KEY_IS_SPEAKER_MODE, FmRadioService.SHORT_ANNTENNA_SUPPORT);
                    FmRadioService.this.notifyActivityStateChanged(bundle);
                }
            } else if ("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                int connectState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", FmRadioService.CURRENT_RX_ON);
                Log.d(FmRadioService.TAG, "ACTION_CONNECTION_STATE_CHANGED: connectState=" + connectState + ", ispowerup=" + FmRadioService.this.mIsPowerUp);
                FmRadioService.this.handleBtConnectState(connectState);
            } else {
                Log.w(FmRadioService.TAG, "Error: undefined action.");
            }
            Log.d(FmRadioService.TAG, "<<< FmRadioService.onReceive");

        }
    }

    private static class Record {
        FmRadioListener mCallback;
        int mHashCode;

        private Record() {
        }
    }

    private class SdcardListener extends BroadcastReceiver {
        private SdcardListener() {
        }

        public void onReceive(Context context, Intent intent) {
            FmRadioService.this.updateSdcardStateMap(intent);
            String action = intent.getAction();
            if (("android.intent.action.MEDIA_EJECT".equals(action) || "android.intent.action.MEDIA_UNMOUNTED".equals(action)) && FmRadioService.this.isRecordingCardUnmount(intent)) {
                Log.v(FmRadioService.TAG, "MEDIA_EJECT");
                Bundle bundle = new Bundle(FmRadioService.CURRENT_TX_SCAN);
                bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_RECORDSTATE_CHANGED);
                bundle.putInt(FmRadioListener.KEY_RECORDING_STATE, 5);
                FmRadioService.this.notifyActivityStateChanged(bundle);
            }
        }
    }

    public class ServiceBinder extends Binder {
        public FmRadioService getService() {
            return FmRadioService.this;
        }
    }

    public FmRadioService() {
        this.defaultChannels = new int[]{911, 927, 935, 943, 950, 983, 1026, 1040, 1048, 1056, 1064, 1078};
        this.mSdcardListener = null;
        this.mRecordState = -1;
        this.mRecorderErrorType = -1;
        this.mSdcardStateMap = new HashMap();
        this.mModifiedRecordingName = null;
        this.mRecords = new ArrayList();
        this.mIsInRecordingMode = SHORT_ANNTENNA_SUPPORT;
        this.mPSString = "";
        this.mLRTextString = "";
        this.mIsPSRTEnabled = SHORT_ANNTENNA_SUPPORT;
        this.mIsAFEnabled = SHORT_ANNTENNA_SUPPORT;
        this.mRdsThread = null;
        this.mIsRdsThreadExit = SHORT_ANNTENNA_SUPPORT;
        this.mIsNativeScanning = SHORT_ANNTENNA_SUPPORT;
        this.mIsScanning = SHORT_ANNTENNA_SUPPORT;
        this.mIsNativeSeeking = SHORT_ANNTENNA_SUPPORT;
        this.mIsSeeking = SHORT_ANNTENNA_SUPPORT;
        this.mIsStopScanCalled = SHORT_ANNTENNA_SUPPORT;
        this.mIsSpeakerUsed = SHORT_ANNTENNA_SUPPORT;
        this.mIsDeviceOpen = SHORT_ANNTENNA_SUPPORT;
        this.mIsPowerUp = SHORT_ANNTENNA_SUPPORT;
        this.mIsPowerUping = SHORT_ANNTENNA_SUPPORT;
        this.mIsServiceInited = SHORT_ANNTENNA_SUPPORT;
        this.mIsMakePowerDown = SHORT_ANNTENNA_SUPPORT;
        this.mContext = null;
        this.mAudioManager = null;
        this.mActivityManager = null;
        this.mFmPlayer = null;
        this.mWakeLock = null;
        this.mIsAudioFocusHeld = SHORT_ANNTENNA_SUPPORT;
        this.mPausedByTransientLossOfFocus = SHORT_ANNTENNA_SUPPORT;
        this.mCurrentStation = FmRadioUtils.DEFAULT_STATION;
        this.mValueHeadSetPlug = NOTIFICATION_ID;
        this.mBinder = new ServiceBinder();
        this.mBroadcastReceiver = null;
        this.mStopRecordingLock = new Object();
        this.mPlayerErrorListener = new C00152();
        this.mAudioFocusChangeListener = new C00163();
    }

    static {
        SHORT_ANNTENNA_SUPPORT = FmRadioUtils.isFmShortAntennaSupport();
        sExitListener = null;
        sActivityIsOnStop = SHORT_ANNTENNA_SUPPORT;
    }

    public IBinder onBind(Intent intent) {
        Log.d(TAG, "FmRadioService.onBind: " + intent);
        return this.mBinder;
    }

    private void powerUpAutoIfNeed() {
        if (this.mValueHeadSetPlug == 0 && !this.mIsPowerUping && !this.mIsPowerUp && sActivityIsOnStop) {
            Log.w(TAG, "Power up for start app then quick click power/home");
            powerUpAsync(FmRadioUtils.computeFrequency(FmRadioStation.getCurrentStation(this.mContext)));
        }
    }

    private void handleBtConnectState(int connectState) {
        if (this.mIsPowerUp) {
            switch (connectState) {
                case CURRENT_RX_ON /*0*/:
                    Log.d(TAG, "handleBtConnectState bt disconnected");
                    changeToEarphoneMode();
                case CURRENT_TX_SCAN /*2*/:
                    Log.d(TAG, "handleBtConnectState bt connected");
                    changeToEarphoneMode();
                default:
                    Log.d(TAG, "invalid fm over bt connect state");
            }
        }
    }

    private void changeToEarphoneMode() {
        setSpeakerPhoneOn(SHORT_ANNTENNA_SUPPORT);
        Bundle bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_SPEAKER_MODE_CHANGED);
        bundle.putBoolean(FmRadioListener.KEY_IS_SPEAKER_MODE, SHORT_ANNTENNA_SUPPORT);
        notifyActivityStateChanged(bundle);
    }

    public boolean isBtConnected() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.d(TAG, "isBtConnected headset:" + btAdapter.getProfileConnectionState(NOTIFICATION_ID) + ", a2dp:" + btAdapter.getProfileConnectionState(CURRENT_TX_SCAN));
        int a2dpState = btAdapter.getProfileConnectionState(NOTIFICATION_ID);
        if (CURRENT_TX_SCAN == a2dpState || NOTIFICATION_ID == a2dpState) {
            return true;
        }
        return SHORT_ANNTENNA_SUPPORT;
    }

    public boolean isAntennaAvailable() {
        return this.mAudioManager.isWiredHeadsetOn();
    }

    public void setSpeakerPhoneOn(boolean isSpeaker) {
        Log.d(TAG, ">>> FmRadioService.useSpeaker: " + isSpeaker);
        this.mForcedUseForMedia = isSpeaker ? NOTIFICATION_ID : CURRENT_RX_ON;
        AudioSystem.setForceUse(NOTIFICATION_ID, this.mForcedUseForMedia);
        this.mIsSpeakerUsed = isSpeaker;
        Log.d(TAG, "<<< FmRadioService.useSpeaker");
    }

    private boolean isSpeakerPhoneOn() {
        return this.mForcedUseForMedia == NOTIFICATION_ID ? true : SHORT_ANNTENNA_SUPPORT;
    }

    private boolean openDevice() {
        Log.d(TAG, ">>> FmRadioService.openDevice");
        if (!this.mIsDeviceOpen) {
            this.mIsDeviceOpen = FmRadioNative.openDev();
        }
        Log.d(TAG, "<<< FmRadioService.openDevice: " + this.mIsDeviceOpen);
        return this.mIsDeviceOpen;
    }

    private boolean closeDevice() {
        Log.d(TAG, ">>> FmRadioService.closeDevice");
        boolean isDeviceClose = SHORT_ANNTENNA_SUPPORT;
        if (this.mIsDeviceOpen) {
            isDeviceClose = FmRadioNative.closeDev();
            this.mIsDeviceOpen = !isDeviceClose ? true : SHORT_ANNTENNA_SUPPORT;
        }
        Log.d(TAG, "<<< FmRadioService.closeDevice: " + isDeviceClose);
        this.mFmServiceHandler.getLooper().quit();
        return isDeviceClose;
    }

    public boolean isDeviceOpen() {
        Log.d(TAG, "FmRadioService.isDeviceOpen: " + this.mIsDeviceOpen);
        return this.mIsDeviceOpen;
    }

    public void powerUpAsync(float frequency) {
        this.mIsPowerUping = true;
        this.mFmServiceHandler.removeMessages(9);
        this.mFmServiceHandler.removeMessages(10);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putFloat(FM_FREQUENCY, frequency);
        Message msg = this.mFmServiceHandler.obtainMessage(9);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private boolean powerUpFm(float frequency) {
        Log.d(TAG, ">>> FmRadioService.powerUp: " + frequency);
        if (this.mIsPowerUp) {
            Log.d(TAG, "<<< FmRadioService.powerUp: already power up:" + this.mIsPowerUp);
            return true;
        } else if (requestAudioFocus()) {
            if (!this.mIsDeviceOpen) {
                openDevice();
            }
            waitIfTxSearching();
            Log.d(TAG, "set CURRENT_RX_ON true, CURRENT_TX_ON false");
            FmRadioNative.setFmStatus(CURRENT_RX_ON, true);
            FmRadioNative.setFmStatus(NOTIFICATION_ID, SHORT_ANNTENNA_SUPPORT);
            sendBroadcastToStopOtherAPP();
            Log.d(TAG, "service native power up start");
            if (FmRadioNative.powerUp(frequency)) {
                Log.d(TAG, "service native power up end");
                this.mIsPowerUp = true;
                setMute(true);
                this.mIsMakePowerDown = SHORT_ANNTENNA_SUPPORT;
                Log.d(TAG, "<<< FmRadioService.powerUp: " + this.mIsPowerUp);
                return this.mIsPowerUp;
            }
            Log.e(TAG, "Error: powerup failed.");
            return SHORT_ANNTENNA_SUPPORT;
        } else {
            this.mIsMakePowerDown = true;
            Log.d(TAG, "FM can't get audio focus when power up");
            sendBroadcastToStopOtherAPP();
            return SHORT_ANNTENNA_SUPPORT;
        }
    }

    private void waitIfTxSearching() {
        Log.d(TAG, ">>> waitIfTxSearching " + FmRadioNative.getFmStatus(CURRENT_TX_SCAN));
        long start = System.currentTimeMillis();
        while (FmRadioNative.getFmStatus(CURRENT_TX_SCAN)) {
            if (System.currentTimeMillis() - start > 5000) {
                Log.e(TAG, "waitIfTxSearching timeout");
                break;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
        }
        Log.d(TAG, "<<< waitIfTxSearching");
    }

    private boolean startPlayFm(float frequency) {
        Log.d(TAG, ">>> FmRadioService.initDevice: " + frequency);
        this.mCurrentStation = FmRadioUtils.computeStation(frequency);
        FmRadioStation.setCurrentStation(this.mContext, this.mCurrentStation);
        if (isRdsSupported()) {
            Log.d(TAG, "RDS is supported. Start the RDS thread.");
            startRdsThread();
        }
        if (!(FmRadioUtils.isFmSuspendSupport() || this.mWakeLock.isHeld())) {
            this.mWakeLock.acquire();
            Log.d(TAG, "acquire wake lock");
        }
        if (this.mIsSpeakerUsed != isSpeakerPhoneOn()) {
            setSpeakerPhoneOn(this.mIsSpeakerUsed);
        }
        if (this.mRecordState != 7) {
            enableFmAudio(true);
        }
        setRds(true);
        setMute(SHORT_ANNTENNA_SUPPORT);
        Log.d(TAG, "<<< FmRadioService.initDevice: " + this.mIsPowerUp);
        return this.mIsPowerUp;
    }

    private void sendBroadcastToStopOtherAPP() {
        sendBroadcast(new Intent(ACTION_TOMUSICSERVICE_POWERDOWN));
        sendBroadcast(new Intent(ACTION_TOATVSERVICE_POWERDOWN));
        sendBroadcast(new Intent(ACTION_TOFMTXSERVICE_POWERDOWN));
    }

    public void powerDownAsync() {
        this.mFmServiceHandler.removeMessages(13);
        this.mFmServiceHandler.removeMessages(16);
        this.mFmServiceHandler.removeMessages(15);
        this.mFmServiceHandler.removeMessages(10);
        this.mFmServiceHandler.removeMessages(9);
        this.mFmServiceHandler.sendEmptyMessage(10);
    }

    private boolean powerDown() {
        Log.d(TAG, ">>> FmRadioService.powerDown");
        if (this.mIsPowerUp) {
            boolean powerDownSuccess;
            setMute(true);
            setRds(SHORT_ANNTENNA_SUPPORT);
            enableFmAudio(SHORT_ANNTENNA_SUPPORT);
            if (FmRadioNative.getFmStatus(CURRENT_RX_ON)) {
                powerDownSuccess = FmRadioNative.powerDown(CURRENT_RX_ON);
            } else {
                powerDownSuccess = true;
            }
            if (powerDownSuccess) {
                this.mIsMakePowerDown = true;
                if (isRdsSupported()) {
                    Log.d(TAG, "RDS is supported. Stop the RDS thread.");
                    stopRdsThread();
                }
                this.mIsPowerUp = SHORT_ANNTENNA_SUPPORT;
                if (this.mWakeLock.isHeld()) {
                    this.mWakeLock.release();
                    Log.d(TAG, "release wake lock");
                }
                removeNotification();
                Log.d(TAG, "<<< FmRadioService.powerDown: true");
                return true;
            }
            Log.e(TAG, "Error: powerdown failed.");
            this.mIsMakePowerDown = true;
            if (isRdsSupported()) {
                Log.d(TAG, "RDS is supported. Stop the RDS thread.");
                stopRdsThread();
            }
            this.mIsPowerUp = SHORT_ANNTENNA_SUPPORT;
            if (this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
                Log.d(TAG, "release wake lock");
            }
            removeNotification();
            Log.d(TAG, "powerdown failed.release some resource.");
            return SHORT_ANNTENNA_SUPPORT;
        }
        Log.w(TAG, "Error: device is already power down.");
        return true;
    }

    public boolean isPowerUp() {
        Log.d(TAG, "FmRadioService.isPowerUp: " + this.mIsPowerUp);
        return this.mIsPowerUp;
    }

    public boolean isPowerUping() {
        Log.d(TAG, "FmRadioService.isPowerUping: " + this.mIsPowerUping);
        return this.mIsPowerUping;
    }

    public boolean isMakePowerDown() {
        Log.d(TAG, "FmRadioService.mIsMakePowerDown: " + this.mIsMakePowerDown);
        return this.mIsMakePowerDown;
    }

    public void tuneStationAsync(float frequency) {
        this.mFmServiceHandler.removeMessages(15);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putFloat(FM_FREQUENCY, frequency);
        Message msg = this.mFmServiceHandler.obtainMessage(15);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private boolean tuneStation(float frequency) {
        Log.d(TAG, ">>> FmRadioService.tune: " + frequency);
        if (this.mIsPowerUp) {
            setRds(SHORT_ANNTENNA_SUPPORT);
            Log.d(TAG, "FmRadioService.native tune start");
            boolean bRet = FmRadioNative.tune(frequency);
            Log.d(TAG, "FmRadioService.native tune end");
            if (bRet) {
                setRds(true);
                this.mCurrentStation = FmRadioUtils.computeStation(frequency);
                FmRadioStation.setCurrentStation(this.mContext, this.mCurrentStation);
            }
            setMute(SHORT_ANNTENNA_SUPPORT);
            Log.d(TAG, "<<< FmRadioService.tune: " + bRet);
            return bRet;
        } else if (isAntennaAvailable() || SHORT_ANNTENNA_SUPPORT) {
            Log.w(TAG, "FM is not powered up");
            this.mIsPowerUping = true;
            boolean tune = SHORT_ANNTENNA_SUPPORT;
            if (powerUpFm(frequency)) {
                tune = startPlayFm(frequency);
            }
            this.mIsPowerUping = SHORT_ANNTENNA_SUPPORT;
            Log.d(TAG, "<<< FmRadioService.tune: mIsPowerup:" + tune);
            return tune;
        } else {
            Log.d(TAG, "earphone is not insert and short antenna not support");
            return SHORT_ANNTENNA_SUPPORT;
        }
    }

    public void seekStationAsync(float frequency, boolean isUp) {
        this.mFmServiceHandler.removeMessages(16);
        Bundle bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putFloat(FM_FREQUENCY, frequency);
        bundle.putBoolean(OPTION, isUp);
        Message msg = this.mFmServiceHandler.obtainMessage(16);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private float seekStation(float frequency, boolean isUp) {
        Log.d(TAG, ">>> FmRadioService.seek: " + frequency + " " + isUp);
        if (this.mIsPowerUp) {
            setRds(SHORT_ANNTENNA_SUPPORT);
            this.mIsNativeSeeking = true;
            float fRet = FmRadioNative.seek(frequency, isUp);
            this.mIsNativeSeeking = SHORT_ANNTENNA_SUPPORT;
            this.mIsStopScanCalled = SHORT_ANNTENNA_SUPPORT;
            Log.d(TAG, "<<< FmRadioService.seek: " + fRet);
            return fRet;
        }
        Log.w(TAG, "FM is not powered up");
        return -1.0f;
    }

    public void startScanAsync() {
        this.mFmServiceHandler.removeMessages(13);
        this.mFmServiceHandler.sendEmptyMessage(13);
    }

    private int[] startScan() {
        Log.d(TAG, ">>> FmRadioService.startScan");
        int[] iChannels = null;
        setRds(SHORT_ANNTENNA_SUPPORT);
        setMute(true);
        short[] shortChannels = null;
        if (!this.mIsStopScanCalled) {
            this.mIsNativeScanning = true;
            Log.d(TAG, "startScan native method:start");
            shortChannels = FmRadioNative.autoScan();
            Log.d(TAG, "startScan native method:end " + Arrays.toString(shortChannels));
            this.mIsNativeScanning = SHORT_ANNTENNA_SUPPORT;
        }
        setRds(true);
        if (this.mIsStopScanCalled) {
            shortChannels = new short[NOTIFICATION_ID];
            shortChannels[CURRENT_RX_ON] = (short) -100;
            this.mIsStopScanCalled = SHORT_ANNTENNA_SUPPORT;
        }
        if (shortChannels != null) {
            int size = shortChannels.length;
            iChannels = new int[size];
            for (int i = CURRENT_RX_ON; i < size; i += NOTIFICATION_ID) {
                iChannels[i] = shortChannels[i];
            }
        }
        Log.d(TAG, "<<< FmRadioService.startScan: " + Arrays.toString(iChannels));
        return iChannels;
    }

    public boolean isScanning() {
        return this.mIsScanning;
    }

    public boolean stopScan() {
        Log.d(TAG, ">>> FmRadioService.stopScan");
        if (this.mIsPowerUp) {
            boolean bRet = SHORT_ANNTENNA_SUPPORT;
            this.mFmServiceHandler.removeMessages(13);
            this.mFmServiceHandler.removeMessages(16);
            if (this.mIsNativeScanning || this.mIsNativeSeeking) {
                this.mIsStopScanCalled = true;
                Log.d(TAG, "native stop scan:start");
                bRet = FmRadioNative.stopScan();
                Log.d(TAG, "native stop scan:end --" + bRet);
            }
            Log.d(TAG, "<<< FmRadioService.stopScan: " + bRet);
            return bRet;
        }
        Log.w(TAG, "FM is not powered up");
        return SHORT_ANNTENNA_SUPPORT;
    }

    public boolean isSeeking() {
        return this.mIsNativeSeeking;
    }

    public void setRdsAsync(boolean on) {
        this.mFmServiceHandler.removeMessages(5);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putBoolean(OPTION, on);
        Message msg = this.mFmServiceHandler.obtainMessage(5);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private int setRds(boolean on) {
        if (!this.mIsPowerUp) {
            return -1;
        }
        Log.d(TAG, ">>> FmRadioService.setRDS: " + on);
        int ret = -1;
        if (isRdsSupported()) {
            ret = FmRadioNative.setRds(on);
        }
        setPS("");
        setLRText("");
        Log.d(TAG, "<<< FmRadioService.setRDS: " + ret);
        return ret;
    }

    public String getPS() {
        Log.d(TAG, "FmRadioService.getPS: " + this.mPSString);
        return this.mPSString;
    }

    public String getLRText() {
        Log.d(TAG, "FmRadioService.getLRText: " + this.mLRTextString);
        return this.mLRTextString;
    }

    public void activeAFAsync() {
        this.mFmServiceHandler.removeMessages(18);
        this.mFmServiceHandler.sendEmptyMessage(18);
    }

    private int activeAF() {
        if (this.mIsPowerUp) {
            int frequency = FmRadioNative.activeAf();
            Log.d(TAG, "FmRadioService.activeAF: " + frequency);
            return frequency;
        }
        Log.w(TAG, "FM is not powered up");
        return -1;
    }

    public void setMuteAsync(boolean mute) {
        this.mFmServiceHandler.removeMessages(7);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putBoolean(OPTION, mute);
        Message msg = this.mFmServiceHandler.obtainMessage(7);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private int setMute(boolean mute) {
        if (this.mIsPowerUp) {
            Log.d(TAG, ">>> FmRadioService.setMute: " + mute);
            int iRet = FmRadioNative.setMute(mute);
            Log.d(TAG, "<<< FmRadioService.setMute: " + iRet);
            return iRet;
        }
        Log.w(TAG, "FM is not powered up");
        return -1;
    }

    public boolean isRdsSupported() {
        boolean isRdsSupported = true;
        if (FmRadioNative.isRdsSupport() != NOTIFICATION_ID) {
            isRdsSupported = SHORT_ANNTENNA_SUPPORT;
        }
        Log.d(TAG, "FmRadioService.isRdsSupported: " + isRdsSupported);
        return isRdsSupported;
    }

    public boolean isSpeakerUsed() {
        Log.d(TAG, "FmRadioService.isSpeakerUsed: " + this.mIsSpeakerUsed);
        return this.mIsSpeakerUsed;
    }

    public void initService(int iCurrentStation) {
        Log.d(TAG, "FmRadioService.initService: " + iCurrentStation);
        this.mIsServiceInited = true;
        this.mCurrentStation = iCurrentStation;
    }

    private int[] insertDefaultStation(int[] channels) {
        int firstValidChannel = this.mCurrentStation;
        int channelNum = CURRENT_RX_ON;
        if (channels != null) {
            Arrays.sort(channels);
            int size = channels.length;
            ArrayList<ContentProviderOperation> ops = new ArrayList();
            String defaultStationName = "";
            for (int i = CURRENT_RX_ON; i < size; i += NOTIFICATION_ID) {
                if (FmRadioUtils.isValidStation(channels[i])) {
                    if (channelNum == 0) {
                        firstValidChannel = channels[i];
                    }
                    if (!FmRadioStation.isDefaultStation(this.mContext, channels[i])) {
                        ops.add(ContentProviderOperation.newInsert(FmRadioStation.Station.CONTENT_URI).withValue(FmRadioStation.Station.COLUMN_STATION_NAME, defaultStationName).withValue(FmRadioStation.Station.COLUMN_STATION_FREQ, Integer.valueOf(channels[i])).withValue(FmRadioStation.Station.COLUMN_STATION_TYPE, Integer.valueOf(3)).build());
                    }
                    channelNum += NOTIFICATION_ID;
                }
            }
            try {
                this.mContext.getContentResolver().applyBatch(FmRadioStation.AUTHORITY, ops);
            } catch (RemoteException e) {
                Log.d(TAG, "Exception when applyBatch searched stations " + e);
            } catch (OperationApplicationException e2) {
                Log.d(TAG, "Exception when applyBatch searched stations " + e2);
            }
        }
        int[] iArr = new int[CURRENT_TX_SCAN];
        iArr[CURRENT_RX_ON] = firstValidChannel;
        iArr[NOTIFICATION_ID] = channelNum;
        return iArr;
    }

    public boolean isServiceInited() {
        Log.d(TAG, "FmRadioService.isServiceInit: " + this.mIsServiceInited);
        return this.mIsServiceInited;
    }

    public int getFrequency() {
        Log.d(TAG, "FmRadioService.getFrequency: " + this.mCurrentStation);
        return this.mCurrentStation;
    }

    public void setFrequency(int station) {
        this.mCurrentStation = station;
    }

    private void resumeFmAudio() {
        Log.d(TAG, "FmRadioService.resumeFmAudio");
        if (this.mIsAudioFocusHeld && this.mIsPowerUp) {
            enableFmAudio(true);
        }
    }

    public void switchAntennaAsync(int antenna) {
        this.mFmServiceHandler.removeMessages(4);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putInt(FmRadioListener.SWITCH_ANNTENNA_VALUE, antenna);
        Message msg = this.mFmServiceHandler.obtainMessage(4);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private int switchAntenna(int antenna) {
        Log.d(TAG, ">>> FmRadioService.switchAntenna:" + antenna);
        int ret = FmRadioNative.switchAntenna(antenna);
        Log.d(TAG, "<<< FmRadioService.switchAntenna: " + ret);
        return ret;
    }

    private boolean startPlayback() {
        Log.d(TAG, ">>> startPlayback");
        if (!requestAudioFocus()) {
            Log.d(TAG, "can't get audio focus when play recording file");
            return SHORT_ANNTENNA_SUPPORT;
        } else {
            this.mAudioManager.setParameters("AudioFmPreStop=1");
            setMute(true);
            enableFmAudio(SHORT_ANNTENNA_SUPPORT);
            return true;
        }
    }

    public void stopPlaybackAsync() {
        this.mFmServiceHandler.removeMessages(25);
        this.mFmServiceHandler.sendEmptyMessage(25);
    }

    public void saveRecordingAsync(String newName) {
        this.mFmServiceHandler.removeMessages(26);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putString(RECODING_FILE_NAME, newName);
        Message msg = this.mFmServiceHandler.obtainMessage(26);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, ">>> FmRadioService.onCreate");
        Log.d(TAG, "short antenna support:" + SHORT_ANNTENNA_SUPPORT);
        this.mContext = getApplicationContext();
        this.mAudioManager = (AudioManager) getSystemService("audio");
        this.mActivityManager = (ActivityManager) getSystemService("activity");
        this.mWakeLock = ((PowerManager) getSystemService("power")).newWakeLock(NOTIFICATION_ID, TAG);
        this.mWakeLock.setReferenceCounted(SHORT_ANNTENNA_SUPPORT);
        if (initFmPlayer()) {
            registerFmBroadcastReceiver();
            registerSdcardReceiver();
            HandlerThread handlerThread = new HandlerThread("FmRadioServiceThread");
            handlerThread.start();
            this.mFmServiceHandler = new FmRadioServiceHandler(handlerThread.getLooper());
            openDevice();
            setSpeakerPhoneOn(this.mIsSpeakerUsed);
            Log.d(TAG, "<<< FmRadioService.onCreate");
            return;
        }
        Log.e(TAG, "init FMPlayer failed");
    }

    private boolean initFmPlayer() {
        this.mFmPlayer = new MediaPlayer();
        if (!FmRadioUtils.isFmSuspendSupport()) {
            this.mFmPlayer.setWakeMode(this, NOTIFICATION_ID);
        }
        this.mFmPlayer.setOnErrorListener(this.mPlayerErrorListener);
        try {
            this.mFmPlayer.setDataSource("THIRDPARTY://MEDIAPLAYER_PLAYERTYPE_FM");
            this.mFmPlayer.setAudioStreamType(3);
            return true;
        } catch (IOException ex) {
            Log.e(TAG, "setDataSource: " + ex);
            return SHORT_ANNTENNA_SUPPORT;
        } catch (IllegalArgumentException ex2) {
            Log.e(TAG, "setDataSource: " + ex2);
            return SHORT_ANNTENNA_SUPPORT;
        } catch (SecurityException ex3) {
            Log.e(TAG, "setDataSource: " + ex3);
            return SHORT_ANNTENNA_SUPPORT;
        } catch (IllegalStateException ex4) {
            Log.e(TAG, "setDataSource: " + ex4);
            return SHORT_ANNTENNA_SUPPORT;
        }
    }

    private void registerFmBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SOUND_POWER_DOWN_MSG);
        filter.addAction("android.intent.action.ACTION_SHUTDOWN");
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction("android.intent.action.HEADSET_PLUG");
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction(ACTION_TOFMSERVICE_POWERDOWN);
        filter.addAction(ACTION_FROMATVSERVICE_POWERUP);
        this.mBroadcastReceiver = new FmServiceBroadcastReceiver();
        Log.i(TAG, "Register broadcast receiver.");
        registerReceiver(this.mBroadcastReceiver, filter);
    }

    private void unregisterFmBroadcastReceiver() {
        if (this.mBroadcastReceiver != null) {
            Log.i(TAG, "Unregister broadcast receiver.");
            unregisterReceiver(this.mBroadcastReceiver);
            this.mBroadcastReceiver = null;
        }
    }

    public void onDestroy() {
        Log.d(TAG, ">>> FmRadioService.onDestroy");
        this.mAudioManager.setParameters("AudioFmPreStop=1");
        setMute(true);
        if (isRdsSupported()) {
            Log.d(TAG, "RDS is supported. Stop the RDS thread.");
            stopRdsThread();
        }
        sendBroadcast(new Intent("YouWillNeverKillMe"));
        unregisterFmBroadcastReceiver();
        unregisterSdcardListener();
        abandonAudioFocus();
        exitFm();
        super.onDestroy();
    }

    private void exitFm() {
        Log.d(TAG, "service.exitFm start");
        this.mIsAudioFocusHeld = SHORT_ANNTENNA_SUPPORT;

        if (this.mIsNativeScanning || this.mIsNativeSeeking) {
            stopScan();
        }
        this.mFmServiceHandler.removeCallbacksAndMessages(null);
        this.mFmServiceHandler.removeMessages(11);
        this.mFmServiceHandler.sendEmptyMessage(11);
        Log.d(TAG, "service.exitFm end");
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, ">>> FmRadioService.onStartCommand intent: " + intent + " startId: " + startId);
        Log.d(TAG, "<<< FmRadioService.onStartCommand: " + super.onStartCommand(intent, flags, startId));
        return START_STICKY;
    }

    private void startRdsThread() {
        Log.d(TAG, ">>> FmRadioService.startRdSThread");
        this.mIsRdsThreadExit = SHORT_ANNTENNA_SUPPORT;
        if (this.mRdsThread == null) {
            this.mRdsThread = new C00141();
            Log.d(TAG, "Start RDS Thread.");
            this.mRdsThread.start();
            Log.d(TAG, "<<< FmRadioService.startRdSThread");
        }
    }

    private void stopRdsThread() {
        Log.d(TAG, ">>> FmRadioService.stopRdSThread");
        if (this.mRdsThread != null) {
            this.mIsRdsThreadExit = true;
            this.mRdsThread = null;
        }
        Log.d(TAG, "<<< FmRadioService.stopRdSThread");
    }

    private void setPS(String ps) {
        Log.d(TAG, "FmRadioService.setPS: " + ps + " ,current: " + this.mPSString);
        if (this.mPSString.compareTo(ps) != 0) {
            this.mPSString = ps;
            Bundle bundle = new Bundle(3);
            bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_PS_CHANGED);
            bundle.putString(FmRadioListener.KEY_PS_INFO, this.mPSString);
            bundle.putString(FmRadioListener.KEY_RT_INFO, this.mLRTextString);
            notifyActivityStateChanged(bundle);
        }
    }

    private void setLRText(String lrtText) {
        Log.d(TAG, "FmRadioService.setLRText: " + lrtText + " ,current: " + this.mLRTextString);
        if (this.mLRTextString.compareTo(lrtText) != 0) {
            this.mLRTextString = lrtText;
            Bundle bundle = new Bundle(3);
            bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_RT_CHANGED);
            bundle.putString(FmRadioListener.KEY_PS_INFO, this.mPSString);
            bundle.putString(FmRadioListener.KEY_RT_INFO, this.mLRTextString);
            notifyActivityStateChanged(bundle);
        }
    }

    private void enableFmAudio(boolean enable) {
        Log.d(TAG, ">>> FmRadioService.enableFmAudio: " + enable);
        if (this.mFmPlayer == null || !this.mIsPowerUp) {
            Log.w(TAG, "mFMPlayer is null in Service.enableFmAudio");
        } else if (enable) {
            if (this.mFmPlayer.isPlaying()) {
                Log.d(TAG, "warning: FM audio is already enabled.");
                return;
            }
            try {
                this.mFmPlayer.prepare();
                if (FmRadioUtils.isFmSuspendSupport()) {
                    Log.d(TAG, "support FM suspend");
                    this.mFmPlayer.start();
                } else {
                    this.mFmPlayer.start();
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception: Cannot call MediaPlayer prepare.", e);
            } catch (IllegalStateException e2) {
                Log.e(TAG, "Exception: Cannot call MediaPlayer prepare.", e2);
            }
            Log.d(TAG, "Start FM audio.");
            Log.d(TAG, "<<< FmRadioService.enableFmAudio");
        } else {
            try {
                if (this.mFmPlayer.isPlaying()) {
                    Log.d(TAG, "call MediaPlayer.stop()");
                    this.mFmPlayer.stop();
                    Log.d(TAG, "stop FM audio.");
                    return;
                }
                Log.d(TAG, "warning: FM audio is already disabled.");
            } catch (IllegalStateException e22) {
                Log.e(TAG, "Exception: Cannot call MediaPlayer isPlaying.", e22);
            }
        }
    }

    private void removeNotification() {
        Log.d(TAG, "FmRadioService.removeNotification");
        stopForeground(true);
    }

    private void registerSdcardReceiver() {
        Log.v(TAG, "registerSdcardReceiver >>> ");
        if (this.mSdcardListener == null) {
            this.mSdcardListener = new SdcardListener();
        }
        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("file");
        filter.addAction("android.intent.action.MEDIA_MOUNTED");
        filter.addAction("android.intent.action.MEDIA_UNMOUNTED");
        filter.addAction("android.intent.action.MEDIA_EJECT");
        registerReceiver(this.mSdcardListener, filter);
        Log.v(TAG, "registerSdcardReceiver <<< ");
    }

    private void unregisterSdcardListener() {
        if (this.mSdcardListener != null) {
            unregisterReceiver(this.mSdcardListener);
        }
    }

    private void updateSdcardStateMap(Intent intent) {
        String action = intent.getAction();
        Uri mountPointUri = intent.getData();
        if (mountPointUri != null) {
            String sdcardPath = mountPointUri.getPath();
            if (sdcardPath == null) {
                return;
            }
            if ("android.intent.action.MEDIA_EJECT".equals(action)) {
                Log.d(TAG, "updateSdcardStateMap: ENJECT " + sdcardPath);
                this.mSdcardStateMap.put(sdcardPath, Boolean.valueOf(SHORT_ANNTENNA_SUPPORT));
            } else if ("android.intent.action.MEDIA_UNMOUNTED".equals(action)) {
                Log.d(TAG, "updateSdcardStateMap: UNMOUNTED " + sdcardPath);
                this.mSdcardStateMap.put(sdcardPath, Boolean.valueOf(SHORT_ANNTENNA_SUPPORT));
            } else if ("android.intent.action.MEDIA_MOUNTED".equals(action)) {
                Log.d(TAG, "updateSdcardStateMap: MOUNTED " + sdcardPath);
                this.mSdcardStateMap.put(sdcardPath, Boolean.valueOf(true));
            }
        }
    }

    public void onRecorderStateChanged(int state) {
        Log.d(TAG, "onRecorderStateChanged: " + state);
        this.mRecordState = state;
        Bundle bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_RECORDSTATE_CHANGED);
        bundle.putInt(FmRadioListener.KEY_RECORDING_STATE, state);
        notifyActivityStateChanged(bundle);
    }

    public void onRecorderError(int error) {
        int i;
        Log.d(TAG, "onRecorderError: " + error);
        if (100 == error) {
            i = 4;
        } else {
            i = error;
        }
        this.mRecorderErrorType = i;
        Bundle bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putInt(FmRadioListener.CALLBACK_FLAG, FmRadioListener.LISTEN_RECORDERROR);
        bundle.putInt(FmRadioListener.KEY_RECORDING_ERROR_TYPE, this.mRecorderErrorType);
        notifyActivityStateChanged(bundle);
        if (4 == error) {
            resumeFmAudio();
        }
    }

    public void onPlayRecordFileComplete() {
        Log.d(TAG, "service.onPlayRecordFileComplete");
        checkAfterPlayback();
    }

    private void checkAfterPlayback() {
        if (isHeadSetIn() || SHORT_ANNTENNA_SUPPORT) {
            Log.d(TAG, "checkAfterPlayback:eaphone is in,need resume fm");
            if (this.mIsPowerUp) {
                resumeFmAudio();
                setMute(SHORT_ANNTENNA_SUPPORT);
                return;
            }
            powerUpAsync(FmRadioUtils.computeFrequency(this.mCurrentStation));
            return;
        }
        Log.d(TAG, "checkAfterPlayback:earphone is out, need show plug in earphone tips");
        switchAntennaAsync(this.mValueHeadSetPlug);
    }

    private boolean isHeadSetIn() {
        return this.mValueHeadSetPlug == 0 ? true : SHORT_ANNTENNA_SUPPORT;
    }

    private void stopFmFocusLoss(int focusState) {
        this.mIsAudioFocusHeld = SHORT_ANNTENNA_SUPPORT;
        if (this.mIsNativeScanning || this.mIsNativeSeeking) {
            stopScan();
            Log.d(TAG, "need to stop FM, so stop scan channel.");
        }
        updateAudioFocusAync(focusState);
        Log.d(TAG, "need to stop FM, so powerdown FM.");
    }

    public boolean requestAudioFocus() {
        boolean z = true;
        if (this.mIsAudioFocusHeld) {
            return true;
        }
        if (NOTIFICATION_ID != this.mAudioManager.requestAudioFocus(this.mAudioFocusChangeListener, 3, NOTIFICATION_ID)) {
            z = SHORT_ANNTENNA_SUPPORT;
        }
        this.mIsAudioFocusHeld = z;
        return this.mIsAudioFocusHeld;
    }

    public void abandonAudioFocus() {
        this.mAudioManager.abandonAudioFocus(this.mAudioFocusChangeListener);
        this.mIsAudioFocusHeld = SHORT_ANNTENNA_SUPPORT;
    }

    private synchronized void updateAudioFocusAync(int focusState) {
        Log.d(TAG, "updateAudioFocusAync: focusState = " + focusState);
        Bundle bundle = new Bundle(NOTIFICATION_ID);
        bundle.putInt(FmRadioListener.KEY_AUDIOFOCUS_CHANGED, focusState);
        Message msg = this.mFmServiceHandler.obtainMessage(30);
        msg.setData(bundle);
        this.mFmServiceHandler.sendMessage(msg);
    }

    private void updateAudioFocus(int focusState) {
        Log.d(TAG, "FmRadioService.updateAudioFocus");
        int fmState;
        switch (focusState) {
            case -2:
                if (this.mIsPowerUp) {
                    this.mPausedByTransientLossOfFocus = true;
                }
                Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: mPausedByTransientLossOfFocus:" + this.mPausedByTransientLossOfFocus);
                handlePowerDown();
            case NOTIFICATION_ID /*1*/:
                Log.d(TAG, "AUDIOFOCUS_GAIN: mPausedByTransientLossOfFocus:" + this.mPausedByTransientLossOfFocus);
                if (!this.mIsPowerUp && this.mPausedByTransientLossOfFocus) {
                    this.mIsPowerUping = true;
                    this.mFmServiceHandler.removeMessages(9);
                    this.mFmServiceHandler.removeMessages(10);
                    Bundle bundle = new Bundle(NOTIFICATION_ID);
                    bundle.putFloat(FM_FREQUENCY, FmRadioUtils.computeFrequency(this.mCurrentStation));
                    handlePowerUp(bundle);
                }
            default:
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void registerFmRadioListener(com.nordicsemi.nrfUARTv2.radio.FmRadioListener r8) {
        /*
        r7 = this;
        r6 = r7.mRecords;
        monitor-enter(r6);
        r3 = 0;
        r0 = r8.hashCode();	 Catch:{ all -> 0x0035 }
        r5 = r7.mRecords;	 Catch:{ all -> 0x0035 }
        r2 = r5.size();	 Catch:{ all -> 0x0035 }
        r1 = 0;
        r4 = r3;
    L_0x0010:
        if (r1 >= r2) goto L_0x0024;
    L_0x0012:
        r5 = r7.mRecords;	 Catch:{ all -> 0x0038 }
        r3 = r5.get(r1);	 Catch:{ all -> 0x0038 }
        r3 = (com.mediatek.fmradio.FmRadioService.Record) r3;	 Catch:{ all -> 0x0038 }
        r5 = r3.mHashCode;	 Catch:{ all -> 0x0035 }
        if (r0 != r5) goto L_0x0020;
    L_0x001e:
        monitor-exit(r6);	 Catch:{ all -> 0x0035 }
    L_0x001f:
        return;
    L_0x0020:
        r1 = r1 + 1;
        r4 = r3;
        goto L_0x0010;
    L_0x0024:
        r3 = new com.mediatek.fmradio.FmRadioService$Record;	 Catch:{ all -> 0x0038 }
        r5 = 0;
        r3.<init>();	 Catch:{ all -> 0x0038 }
        r3.mHashCode = r0;	 Catch:{ all -> 0x0035 }
        r3.mCallback = r8;	 Catch:{ all -> 0x0035 }
        r5 = r7.mRecords;	 Catch:{ all -> 0x0035 }
        r5.add(r3);	 Catch:{ all -> 0x0035 }
        monitor-exit(r6);	 Catch:{ all -> 0x0035 }
        goto L_0x001f;
    L_0x0035:
        r5 = move-exception;
    L_0x0036:
        monitor-exit(r6);	 Catch:{ all -> 0x0035 }
        throw r5;
    L_0x0038:
        r5 = move-exception;
        r3 = r4;
        goto L_0x0036;
        */
        throw new UnsupportedOperationException("Method not decompiled: FmRadioService.registerFmRadioListener:void");
    }

    private void notifyActivityStateChanged(Bundle bundle) {
        if (!this.mRecords.isEmpty()) {
            Log.d(TAG, "notifyActivityStatusChanged:clients = " + this.mRecords.size());
            synchronized (this.mRecords) {
                Iterator<Record> iterator = this.mRecords.iterator();
                while (iterator.hasNext()) {
                    FmRadioListener listener = ((Record) iterator.next()).mCallback;
                    if (listener == null) {
                        iterator.remove();
                        return;
                    }
                    listener.onCallBack(bundle);
                }
            }
        }
    }

    public void unregisterFmRadioListener(FmRadioListener callback) {
        remove(callback.hashCode());
    }

    private void remove(int hashCode) {
        synchronized (this.mRecords) {
            Iterator<Record> iterator = this.mRecords.iterator();
            while (iterator.hasNext()) {
                if (((Record) iterator.next()).mHashCode == hashCode) {
                    iterator.remove();
                }
            }
        }
    }

    public boolean isRecordingCardUnmount(Intent intent) {
        String unmountSDCard = intent.getData().toString();
        Log.d(TAG, "unmount sd card file path: " + unmountSDCard);
        return unmountSDCard.equalsIgnoreCase(new StringBuilder().append("file://").append(sRecordingSdcard).toString()) ? true : SHORT_ANNTENNA_SUPPORT;
    }

    private int[] insertSearchedStation(int[] channels) {
        Log.d(TAG, "insertSearchedStation.firstValidChannel:" + Arrays.toString(channels));
        int firstValidChannel = this.mCurrentStation;
        int channelNum = CURRENT_RX_ON;
        if (channels != null) {
            Arrays.sort(channels);
            int size = channels.length;
            ArrayList<ContentProviderOperation> ops = new ArrayList();
            String defaultStationName = "";
            for (int i = CURRENT_RX_ON; i < size; i += NOTIFICATION_ID) {
                if (FmRadioUtils.isValidStation(channels[i])) {
                    if (channelNum == 0) {
                        firstValidChannel = channels[i];
                    }
                    if (!FmRadioStation.isFavoriteStation(this.mContext, channels[i])) {
                        ops.add(ContentProviderOperation.newInsert(FmRadioStation.Station.CONTENT_URI).withValue(FmRadioStation.Station.COLUMN_STATION_NAME, defaultStationName).withValue(FmRadioStation.Station.COLUMN_STATION_FREQ, Integer.valueOf(channels[i])).withValue(FmRadioStation.Station.COLUMN_STATION_TYPE, Integer.valueOf(3)).build());
                    }
                    channelNum += NOTIFICATION_ID;
                }
            }
            try {
                this.mContext.getContentResolver().applyBatch(FmRadioStation.AUTHORITY, ops);
            } catch (RemoteException e) {
                Log.d(TAG, "Exception when applyBatch searched stations " + e);
            } catch (OperationApplicationException e2) {
                Log.d(TAG, "Exception when applyBatch searched stations " + e2);
            }
        }
        Log.d(TAG, "insertSearchedStation.firstValidChannel:" + firstValidChannel + ",channelNum:" + channelNum);
        int[] iArr = new int[CURRENT_TX_SCAN];
        iArr[CURRENT_RX_ON] = firstValidChannel;
        iArr[NOTIFICATION_ID] = channelNum;
        return iArr;
    }

    private void handlePowerDown() {
        boolean isPowerdown = powerDown();
        Bundle bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putInt(FmRadioListener.CALLBACK_FLAG, 10);
        bundle.putBoolean(FmRadioListener.KEY_IS_POWER_DOWN, isPowerdown);
        notifyActivityStateChanged(bundle);
    }

    private void handlePowerUp(Bundle bundle) {
        boolean isPowerup = SHORT_ANNTENNA_SUPPORT;
        Log.d(TAG, "service handler power up start");
        float curFrequency = bundle.getFloat(FM_FREQUENCY);
        if (SHORT_ANNTENNA_SUPPORT || isAntennaAvailable()) {
            if (powerUpFm(curFrequency)) {
                isPowerup = startPlayFm(curFrequency);
                this.mPausedByTransientLossOfFocus = SHORT_ANNTENNA_SUPPORT;
            }
            this.mIsPowerUping = SHORT_ANNTENNA_SUPPORT;
            bundle = new Bundle(CURRENT_TX_SCAN);
            bundle.putInt(FmRadioListener.CALLBACK_FLAG, 9);
            bundle.putBoolean(FmRadioListener.KEY_IS_POWER_UP, isPowerup);
            notifyActivityStateChanged(bundle);
            Log.d(TAG, "service handler power up end");
            return;
        }
        Log.d(TAG, "call back to activity, earphone is not ready");
        this.mIsPowerUping = SHORT_ANNTENNA_SUPPORT;
        bundle = new Bundle(CURRENT_TX_SCAN);
        bundle.putInt(FmRadioListener.CALLBACK_FLAG, 4);
        bundle.putBoolean(FmRadioListener.KEY_IS_SWITCH_ANNTENNA, SHORT_ANNTENNA_SUPPORT);
        notifyActivityStateChanged(bundle);
    }

    public boolean isActivityForeground() {
        boolean isForeground = true;
        for (RunningAppProcessInfo appProcessInfo : this.mActivityManager.getRunningAppProcesses()) {
            if (appProcessInfo.processName.equals(this.mContext.getPackageName())) {
                int importance = appProcessInfo.importance;
                Log.d(TAG, "isActivityForeground importance:" + importance);
                if (importance == 100 || importance == 200) {
                    Log.d(TAG, "isActivityForeground is foreground");
                    isForeground = true;
                } else {
                    Log.d(TAG, "isActivityForeground is background");
                    isForeground = SHORT_ANNTENNA_SUPPORT;
                }
                Log.d(TAG, "isActivityForeground return " + isForeground);
                return isForeground;
            }
        }
        Log.d(TAG, "isActivityForeground return " + isForeground);
        return isForeground;
    }

    public boolean isInLockTaskMode() {
        Log.d(TAG, "isInLockTaskMode:" + this.mActivityManager.isInLockTaskMode());
        return this.mActivityManager.isInLockTaskMode();
    }

    public static String getRecordingSdcard() {
        return sRecordingSdcard;
    }

    public static void registerExitListener(OnExitListener listener) {
        sExitListener = listener;
    }

    public static void unregisterExitListener(OnExitListener listener) {
        sExitListener = null;
    }

    public String getModifiedRecordingName() {
        Log.d(TAG, "getRecordingNameInDialog:" + this.mModifiedRecordingName);
        return this.mModifiedRecordingName;
    }

    public void setModifiedRecordingName(String name) {
        Log.d(TAG, "setRecordingNameInDialog:" + name);
        this.mModifiedRecordingName = name;
    }

    public static void setActivityIsOnStop(boolean stop) {
        sActivityIsOnStop = stop;
    }

    public boolean isModeNormal() {
        int mode = this.mAudioManager.getMode();
        Log.d(TAG, "isInCall mode:" + mode);
        return mode == 0 ? true : SHORT_ANNTENNA_SUPPORT;
    }

    public boolean getStereoMono() {
        Log.d(TAG, "FMRadioService.getStereoMono");
        return FmRadioNative.stereoMono();
    }

    public boolean setStereoMono(boolean isMono) {
        Log.d(TAG, "FMRadioService.setStereoMono: isMono=" + isMono);
        return FmRadioNative.setStereoMono(isMono);
    }

    public boolean setEmth(int index, int value) {
        Log.d(TAG, ">>> FMRadioService.setEmth: index=" + index + ",value=" + value);
        boolean isOk = FmRadioNative.emsetth(index, value);
        Log.d(TAG, "<<< FMRadioService.setEmth: isOk=" + isOk);
        return isOk;
    }

    public short[] emcmd(short[] val) {
        Log.d(TAG, ">>FMRadioService.emcmd: val=" + val);
        short[] shortCmds = FmRadioNative.emcmd(val);
        Log.d(TAG, "<<FMRadioService.emcmd:" + shortCmds);
        return shortCmds;
    }

    public int[] getHardwareVersion() {
        return FmRadioNative.getHardwareVersion();
    }

    public int getCapArray() {
        Log.d(TAG, "FMRadioService.readCapArray");
        if (this.mIsPowerUp) {
            return FmRadioNative.readCapArray();
        }
        Log.w(TAG, "FM is not powered up");
        return -1;
    }

    public int getRssi() {
        Log.d(TAG, "FMRadioService.readRssi");
        if (this.mIsPowerUp) {
            return FmRadioNative.readRssi();
        }
        Log.w(TAG, "FM is not powered up");
        return -1;
    }

    public int getRdsBler() {
        Log.d(TAG, "FMRadioService.readRdsBler");
        if (this.mIsPowerUp) {
            return FmRadioNative.readRdsBler();
        }
        Log.w(TAG, "FM is not powered up");
        return -1;
    }
}
