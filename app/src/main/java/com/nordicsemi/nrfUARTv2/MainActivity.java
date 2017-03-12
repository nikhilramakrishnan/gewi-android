
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
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
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
    private static final String SQUEEZE = "sq";
    private static final String UP = "up";
    private static final String DOWN = "dn";
    private static final String NIL = "nil";

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
    private InfotainmentController infotainmentController;
    private AudioManager mAudioManager;

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
        infotainmentController = new InfotainmentController(getApplicationContext());

        iconTitle.setText("ICON: " + modes.getIconType());
        valueTitle.setText("VALUE: " + modes.getValue());
        valueNumber.setText(modes.getValue()+"");

        hud.setVisibility(View.INVISIBLE);
        iconHud.setVisibility(View.INVISIBLE);
        edtMessage = (EditText) findViewById(R.id.sendText);
        service_init();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


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
                         interpretAction(MotionAction.SQUEEZE);
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
                             if (infotainmentController.isRadioPlaying()) {
                                 infotainmentController.stopRadio();
                             }
                             infotainmentController.releaseMediaPlayer();
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
                             interpretAction(motionAction);
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
        // gestures[0]: left FSR
        // gestures[1]: right FSR
        // gestures[2]: left FSLP
        // gestures[3]: right FSLP
        String[] gestures =  transmitted.split(",");

        // right and left FSR need to be squeezed to change screen
        if (gestures[0].contains(SQUEEZE)) {
            return MotionAction.SQUEEZE;
        }

        if (gestures[3].contains(UP)) {
            return MotionAction.UP;
        }

        if (gestures[3].contains(DOWN)) {
            return MotionAction.DOWN;
        }

        if (gestures[2].contains(UP)) {
            return MotionAction.VOLUP;
        }

        if (gestures[2].contains(DOWN)) {
            return MotionAction.VOLDN;
        }
        // this should never happen
        Log.e(TAG, "None of the gestures took place");
        return MotionAction.WAT;
    }

    private void interpretAction(final MotionAction motionAction) {
        switch (motionAction) {
            case SQUEEZE:
                modes.next();
                if (modes.getIconType().contains("radio")) {
                    infotainmentController.startRadio();
                } else {
                    infotainmentController.stopRadio();
                }
                if (modes.getIconType().contains("cd")) {
                    infotainmentController.startMediaPlayer();
                } else {
                    if (infotainmentController.isMediaPlayerPrepared()) {
                        infotainmentController.pauseMediaPlayer();
                    }
                }
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
                if (modes.getIconType().contains("radio") && modes.getValue() != modes.radioMax) {
                    infotainmentController.nextRadioStation();
                }
                if (modes.getIconType().contains("cd") && modes.getValue() != modes.cdMax) {
                    infotainmentController.nextSong();
                }
                modes.incrementCurrentValue(1);
                valueTitle.setText("VALUE: " + modes.getValue());
                valueNumber.setText(modes.getValue()+"");
                valueCounter.setMetricText(modes.getMetricText());
                valueCounter.setRange(modes.getMaximum());
                valueCounter.setValues(modes.getValue(), modes.getValue(), modes.getValue());
                break;
            case DOWN:
                if (modes.getIconType().contains("radio") && modes.getValue() != 0) {
                    infotainmentController.previousRadioStation();
                }
                if (modes.getIconType().contains("cd") && modes.getValue() != 0) {
                    infotainmentController.previousSong();
                }
                modes.decrementCurrentValue(1);
                valueTitle.setText("VALUE: " + modes.getValue());
                valueCounter.setMetricText(modes.getMetricText());
                valueNumber.setText(modes.getValue()+"");
                valueCounter.setRange(modes.getMaximum());
                valueCounter.setValues(modes.getValue(), modes.getValue(), modes.getValue());
                break;
            case VOLUP:
                if (mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        == mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                    break;
                }
                mAudioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)+1,
                        AudioManager.FLAG_SHOW_UI);
                break;
            case VOLDN:
                if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                    break;
                }
                mAudioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)-1,
                        AudioManager.FLAG_SHOW_UI);
                break;
            default:
                break;
        }
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
        infotainmentController.connectRadio();
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

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        infotainmentController.disconnectRadio();
        infotainmentController.releaseMediaPlayer();
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
            findViewById(R.id.iconHud).setVisibility(View.INVISIBLE);
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

    class Modes {
        private final int cdMax = 1;
        private final int radioMax = 2;
        private final String[] iconTypes;
        private final int[] values;

        private int currentPosition;

        public Modes(String[] iconTypes, int[] values, int currentPosition) {
            this.iconTypes = iconTypes;
            this.values = values;
            this.currentPosition = currentPosition;
        }

        public Modes() {
            this.iconTypes = new String[] {"radio", "cd"};
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
            if (getIconType().contains("cd")) {
                return R.drawable.ic_play_circle_filled_black_24dp;
            }
            if (getIconType().contains("radio")) {
                // return radio icon resource id
                return R.drawable.ic_headset_black_24dp;
            }
            return R.drawable.nrfuart_hdpi_icon;
        }

        /**
         *
         * @return a unit of text to return
         */
        public String getMetricText() {
            if (getIconType().contains("cd")) {
                return "CD";
            }
            if (getIconType().contains("radio")) {
                return "radio";
            }
            return "";
        }

        public int getValue() {
            return values[currentPosition];
        }

        public int getMaximum() {
            if (getIconType().contains("cd")) {
                return cdMax;
            }
            if (getIconType().contains("radio")) {
                return radioMax;
            }
            return -1;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public void incrementCurrentValue(int value) {
            if (getIconType().contains("cd") && values[currentPosition] != cdMax) {
                values[currentPosition] += value;
            }
            if (getIconType().contains("radio") && values[currentPosition] != radioMax) {
                values[currentPosition] += value;
            }
        }

        public void decrementCurrentValue(int value) {
            if (values[currentPosition] == 0) {
                return;
            }
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
