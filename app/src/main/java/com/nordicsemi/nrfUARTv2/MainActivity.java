
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.nordicsemi.nrfUARTv2;


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.db.circularcounter.CircularCounter;
import com.nordicsemi.nrfUARTv2.radio.FmRadioListener;
import com.nordicsemi.nrfUARTv2.radio.FmRadioService;
import com.nordicsemi.nrfUARTv2.radio.FmRadioStation;
import com.nordicsemi.nrfUARTv2.radio.FmRadioUtils;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private static final boolean SHORT_ANNTENNA_SUPPORT;

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect,btnSend;
    private EditText edtMessage;

    private LinearLayout hud;
    private TextView iconTitle;
    private TextView valueTitle;

    private RelativeLayout iconHud;
    private ImageView iconImage;
    private TextView valueNumber;
    private CircularCounter valueCounter;

    private Modes modes;

    private FmRadioService mRadioService;
    private FmRadioListener mFmRadioListener;
    private ServiceConnection mRadioServiceConnection;
    private AudioManager mAudioManager;
    private boolean mIsRadioServiceBinded;
    private boolean mIsRadioServiceStarted;
    private int mCurrentStation;

    public MainActivity() {
        this.mIsRadioServiceBinded = SHORT_ANNTENNA_SUPPORT;
        this.mIsRadioServiceStarted = SHORT_ANNTENNA_SUPPORT;
        this.mCurrentStation = FmRadioUtils.DEFAULT_STATION;
        this.mFmRadioListener = new RadioListener();
        this.mRadioService = null;
        this.mAudioManager = null;
        this.mRadioServiceConnection = new RadioServiceConnection();
    }
    static {
        SHORT_ANNTENNA_SUPPORT = FmRadioUtils.isFmShortAntennaSupport();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);
        btnSend=(Button) findViewById(R.id.sendButton);

        hud = (LinearLayout) findViewById(R.id.hud);
        iconTitle = (TextView) hud.findViewById(R.id.icon);
        valueTitle = (TextView) hud.findViewById(R.id.value);

        iconHud = (RelativeLayout) findViewById(R.id.iconHud);
        iconImage = (ImageView) iconHud.findViewById(R.id.iconImage);
        valueNumber = (TextView) iconHud.findViewById(R.id.valueNumber);
        valueCounter = (CircularCounter) iconHud.findViewById(R.id.valueCounter);

        modes = new Modes();

        iconTitle.setText("ICON: " + modes.getIconType());
        valueTitle.setText("VALUE: " + modes.getValue());
        valueNumber.setText(modes.getValue()+"");

        hud.setVisibility(View.INVISIBLE);
        iconHud.setVisibility(View.INVISIBLE);
        edtMessage = (EditText) findViewById(R.id.sendText);
        service_init();



        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                	if (btnConnectDisconnect.getText().equals("Connect")){

                		//Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

            			Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
            			startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
        			} else {
        				//Disconnect button pressed
        				if (mDevice!=null)
        				{
        					mService.disconnect();

        				}
        			}
                }
            }
        });
        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	EditText editText = (EditText) findViewById(R.id.sendText);
            	String message = editText.getText().toString();
            	byte[] value;
				try {
					//send data to service
					value = message.getBytes("UTF-8");
					mService.writeRXCharacteristic(value);
					//Update the log with time stamp
					String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
					listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
               	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
               	 	edtMessage.setText("");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

            }
        });

        // Set initial UI state

        setupFm();

    }

    private void setupFm() {
        FmRadioStation.initFmDatabase(getApplicationContext());
        this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
        		mService = ((UartService.LocalBinder) rawBinder).getService();
        		Log.d(TAG, "onServiceConnected mService= " + mService);
        		if (!mService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

        }

        public void onServiceDisconnected(ComponentName classname) {
       ////     mService.disconnect(mDevice);
        		mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
           //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                         	String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                             Log.d(TAG, "UART_CONNECT_MSG");
                             btnConnectDisconnect.setText("Disconnect");
                         btnConnectDisconnect.setVisibility(View.GONE);
                         findViewById(R.id.hud).setVisibility(View.INVISIBLE);
                         findViewById(R.id.iconHud).setVisibility(View.VISIBLE);
                         setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                             edtMessage.setEnabled(true);
                             btnSend.setEnabled(true);
                             ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                             listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                             mState = UART_PROFILE_CONNECTED;
                     }
            	 });
            }

          //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                    	 	 String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                             Log.d(TAG, "UART_DISCONNECT_MSG");
                             btnConnectDisconnect.setText("Connect");
                             edtMessage.setEnabled(false);
                             btnSend.setEnabled(false);
                             ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                             listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                             mState = UART_PROFILE_DISCONNECTED;
                             mService.close();
                            //setUiState();

                     }
                 });
            }


          //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
             	 mService.enableTXNotification();
            }
          //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                 final byte[] rxValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                 runOnUiThread(new Runnable() {
                     public void run() {
                         try {
                         	String text = new String(rxValue, "UTF-8");
                             // send message to method to pull up different functions
                             MotionAction motionAction = readTransmissionAndReturnAction(
                                     text.trim().toLowerCase());
                             switch (motionAction) {
                                 case SQUEEZE:
                                     modes.next();
                                     valueTitle.setText("VALUE: " + modes.getValue());
                                     iconTitle.setText("ICON: " + modes.getIconType());
                                     valueNumber.setText(modes.getValue()+"");
                                     valueCounter.setRange(modes.getMaximum());
                                     valueCounter.setValues(modes.getValue(), modes.getValue(), modes.getValue());
                                     valueCounter.setMetricText(modes.getMetricText());
                                     iconImage.setImageResource(modes.getImage());
                                     ((GewiLayout) iconHud).setColors(modes);

                                     break;
                                 case UP:
                                     modes.incrementCurrentValue(1);
                                     valueTitle.setText("VALUE: " + modes.getValue());
                                     valueNumber.setText(modes.getValue()+"");
                                     valueCounter.setMetricText(modes.getMetricText());
                                     valueCounter.setRange(modes.getMaximum());
                                     valueCounter.setValues(modes.getValue(), modes.getValue(), modes.getValue());

                                     break;
                                 case DOWN:
                                     modes.decrementCurrentValue(1);
                                     valueTitle.setText("VALUE: " + modes.getValue());
                                     valueCounter.setMetricText(modes.getMetricText());
                                     valueNumber.setText(modes.getValue()+"");
                                     valueCounter.setRange(modes.getMaximum());
                                     valueCounter.setValues(modes.getValue(), modes.getValue(), modes.getValue());

                                     break;
                                 default:
                                     break;
                                 }
                         	String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        	 	listAdapter.add("["+currentDateTimeString+"] RX: "+text);
                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

                         } catch (Exception e) {
                             Log.e(TAG, e.toString());
                         }
                     }
                 });
             }
           //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
            	showMessage("Device doesn't support UART. Disconnecting");
            	mService.disconnect();
            }


        }
    };

    /**
     * Accepts a transmitted string thru serial comms from Arduino.
     * Returns an enum that can be stored and you can do whatever with it.
     *
     * @param transmitted the string from serial comms thru arduino.
     * @return {@link MotionAction} that tells rest of code what to do.
     */
    private MotionAction readTransmissionAndReturnAction(String transmitted) {
        if (transmitted.contains("squeeze")) {
            return MotionAction.SQUEEZE;
        }
        if (transmitted.contains("up")) {
            return MotionAction.UP;
        }
        if (transmitted.contains("down")) {
            return MotionAction.DOWN;
        }
        // this should never happen
        Log.e(TAG, "None of the gestures took place");
        return MotionAction.WAT;
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
        FmRadioService.setActivityIsOnStop(SHORT_ANNTENNA_SUPPORT);
        ComponentName serviceName = startService(new Intent(this, FmRadioService.class));
        if (startService(new Intent(this, FmRadioService.class)) == null) {
            Log.e(TAG, "Error: Cannot start FM service");
        } else {
            this.mIsRadioServiceStarted = true;
            this.mIsRadioServiceBinded = bindService(new Intent(this, FmRadioService.class),
                    this.mRadioServiceConnection, Context.BIND_AUTO_CREATE);
            if (this.mIsRadioServiceBinded) {
                Log.d(TAG, "FmRadioActivity.onStart end");
                return;
            }
            Log.e(TAG, "Error: Cannot bind FM service");
        }

    }

    @Override
    public void onDestroy() {
    	 super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        if (this.mRadioService != null) {
            this.mRadioService.unregisterFmRadioListener(this.mFmRadioListener);
            this.mFmRadioListener = null;
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        FmRadioService.setActivityIsOnStop(true);
        if (this.mIsRadioServiceBinded) {
            unbindService(this.mRadioServiceConnection);
            this.mIsRadioServiceBinded = SHORT_ANNTENNA_SUPPORT;
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {

        case REQUEST_SELECT_DEVICE:
        	//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                mService.connect(deviceAddress);

            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        default:
            Log.e(TAG, "wrong request code");
            break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (btnConnectDisconnect.getVisibility() != View.VISIBLE) {
            btnConnectDisconnect.setVisibility(View.VISIBLE);
            findViewById(R.id.hud).setVisibility(View.INVISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }

        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
   	                finish();
                }
            })
            .setNegativeButton(R.string.popup_no, null)
            .show();
        }
    }

    public void powerUpFm() {
        Log.v(TAG, "start powerUpFm");
        if (!System.getProperty("ro.project", "trunk").equals("qmb_pk") || isAntennaAvailable()) {
            this.mRadioService.powerUpAsync(FmRadioUtils.computeFrequency(this.mCurrentStation));
            Log.v(TAG, "end powerUpFm");
            return;
        }
        exitService();
    }

    public void exitService() {
        Log.i(TAG, "exitService");
        if (this.mIsRadioServiceBinded) {
            unbindService(this.mServiceConnection);
            this.mIsRadioServiceBinded = SHORT_ANNTENNA_SUPPORT;
        }
        if (this.mIsRadioServiceStarted) {
            if (!stopService(new Intent(this, FmRadioService.class))) {
                Log.e(TAG, "Error: Cannot stop the FM service.");
            }
            this.mIsRadioServiceStarted = SHORT_ANNTENNA_SUPPORT;
        }
    }

    public void tuneToStation(int station) {
        this.mRadioService.tuneStationAsync(FmRadioUtils.computeFrequency(station));
    }

    private void seekStation(int station, boolean direction) {
        this.mRadioService.seekStationAsync(FmRadioUtils.computeFrequency(station), direction);
    }

    public void updateCurrentStation() {
        int freq = this.mRadioService.getFrequency();
        if (FmRadioUtils.isValidStation(freq) && this.mCurrentStation != freq) {
            Log.d(TAG, "frequency in service isn't same as in database");
            this.mCurrentStation = freq;
            FmRadioStation.setCurrentStation(getApplicationContext(), this.mCurrentStation);
        }
    }

    public boolean isAntennaAvailable() {
        return this.mAudioManager.isWiredHeadsetOn();
    }

    /* renamed from: com.mediatek.fmradio.FmRadioActivity.1 */
    class RadioListener implements FmRadioListener {
        RadioListener() {
        }

        public void onCallBack(Bundle bundle) {
            int flag = bundle.getInt(FmRadioListener.CALLBACK_FLAG);
            Log.d(MainActivity.TAG, "call back method flag:" + flag);
            if (flag == 11) {
                //FmRadioActivity.this.mHandler.removeCallbacksAndMessages(null);
            }
            //Message msg = FmRadioActivity.this.mHandler.obtainMessage(flag);
            //msg.setData(bundle);
            //FmRadioActivity.this.mHandler.removeMessages(flag);
            //FmRadioActivity.this.mHandler.sendMessage(msg);
        }
    }

    class RadioServiceConnection implements ServiceConnection {
        RadioServiceConnection() {
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(MainActivity.TAG, "FmRadioActivity.onServiceConnected start");
            MainActivity.this.mRadioService = ((FmRadioService.ServiceBinder) service).getService();
            if (MainActivity.this.mService == null) {
                Log.e(MainActivity.TAG, "ServiceConnection: Error: can't get Service");
                return;
            }
            MainActivity.this.mRadioService.registerFmRadioListener(MainActivity.this.mFmRadioListener);
            if (MainActivity.this.mRadioService.isServiceInited()) {
                Log.d(MainActivity.TAG, "ServiceConnection: FM service is already init");
                if (MainActivity.this.mRadioService.isDeviceOpen()) {
                    if (!MainActivity.this.mRadioService.isPowerUp() && MainActivity.this.mRadioService.isModeNormal()) {
                        Log.d(MainActivity.TAG, "Need to power up auto for this case");
                        MainActivity.this.powerUpFm();
                    } else if (!(MainActivity.this.mRadioService.isPowerUp() || MainActivity.this.mRadioService.isModeNormal() || MainActivity.this.mRadioService.isAntennaAvailable() || FmRadioUtils.isFmShortAntennaSupport())) {
                        Log.w(MainActivity.TAG, "Need to show no antenna dialog for plug out earphone in onPause state");
                    }
                    MainActivity.this.tuneToStation(MainActivity.this.mCurrentStation);

                    MainActivity.this.updateCurrentStation();
                } else {
                    Log.e(MainActivity.TAG, "ServiceConnection: service is exiting while start FM again");
                    MainActivity.this.exitService();
                }
                if (System.getProperty("ro.project", "trunk").equals("qmb_pk") && !MainActivity.this.isAntennaAvailable()) {
                    MainActivity.this.exitService();
                    return;
                }
            }
            Log.d(MainActivity.TAG, "ServiceConnection: FM service is not init");
            MainActivity.this.mRadioService.initService(MainActivity.this.mCurrentStation);
            MainActivity.this.powerUpFm();
            Log.d(MainActivity.TAG, "MainActivity.onServiceConnected end");
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(MainActivity.TAG, "FmRadioActivity.onServiceDisconnected");
        }
    }

    class Modes {
        private final String[] iconTypes;
        private final int[] values;

        private int currentPosition;

        public Modes(String[] iconTypes, int[] values, int currentPosition) {
            this.iconTypes = iconTypes;
            this.values = values;
            this.currentPosition = currentPosition;
        }

        public Modes() {
            this.iconTypes = new String[] {"radio", "volume"};
            this.values = new int[iconTypes.length];
            for (int index : values) {
                values[index] = 0;
            }
            this.currentPosition = 0;


        }

        public String getIconType() {
            return iconTypes[currentPosition];
        }

        /**
         *
         * @return a resource ID to an image in the drawables folder
         */
        public int getImage() {
            if (getIconType().contains("fan")) {
                // return fan icon image resource id
            }
            if (getIconType().contains("temperature")) {
                // return thermometer icon resource id
            }
            if (getIconType().contains("radio")) {
                // return radio icon resource id
                return R.drawable.ic_headset_black_24dp;
            }
            if (getIconType().contains("volume")) {
                // return volume icon resource id
                return R.drawable.ic_volume_up_black_24dp;
            }
            return R.drawable.nrfuart_hdpi_icon;
        }

        /**
         *
         * @return a unit of text to return
         */
        public String getMetricText() {
            if (getIconType().contains("fan")) {
                return "";
            }
            if (getIconType().contains("temperature")) {
                return "Celsius";
            }
            if (getIconType().contains("radio")) {
                return "mHz";
            }
            if (getIconType().contains("volume")) {
                return "%";
            }
            return "";
        }

        public int getValue() {
            if (getIconType().contains("radio")) {
                return values[currentPosition]+89;
            }
            return values[currentPosition];
        }

        public int getMaximum() {
            if (getIconType().contains("fan")) {
                return 4;
            }
            if (getIconType().contains("temperature")) {
                return 30;
            }
            if (getIconType().contains("radio")) {
                return 20;
            }
            if (getIconType().contains("volume")) {
                return 100;
            }
            return -1;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public void incrementCurrentValue(int value) {
            values[currentPosition] += value;
        }

        public void decrementCurrentValue(int value) {
            values[currentPosition] -= value;
        }

        public void setCurrentPosition(int currentPosition) {
            this.currentPosition = currentPosition;
        }

        public void next() {
            this.currentPosition = currentPosition+1;
            if (currentPosition == iconTypes.length) {
                currentPosition = 0;
            }
        }
    }
}
