package com.mediatek.labs.linkitremote;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.os.SystemClock;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

import io.github.controlwear.virtual.joystick.android.*;


public class RemoteView extends AppCompatActivity {
    static private final String TAG = "RemoteView";

    private BluetoothDevice mDevice;
    private BluetoothGattCallback mCallback;
    private BluetoothGatt mGatt;
    private BluetoothGattService mService;
    private BluetoothGattCharacteristic mDeviceNameCharacteristic;
    private BluetoothGattCharacteristic mEventCharacteristic;
    private BluetoothGattCharacteristic mUIUpdateCharacteristic;
    private HashSet<BluetoothGattCharacteristic> mQuery;
    private HashMap<UUID, BluetoothGattCharacteristic> mValues;
    private ProgressBar mActivityIndicator;
    private Handler mHandler;
    private UIEventListener mEventListener;
    private DeviceInfo mDeviceInfo;
    private byte mEventSeq = 0;
    private boolean mActiveDisconnect = false;
    private final LinkedBlockingDeque<byte[]> mWriteRequests = new LinkedBlockingDeque<>();
    private final Semaphore mWriting = new Semaphore(1, true);
    private long mLastSliderUpdateTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_view);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        mDevice = intent.getExtras().getParcelable(DeviceList.CONNECT_DEVICE_MESSAGE);

        // for posting UI methods from GATT callbacks
        mHandler = new Handler(RemoteView.this.getApplicationContext().getMainLooper());

        // Set device name
        final String name = mDevice.getName();
        final ActionBar bar = getSupportActionBar();
        if(bar != null) {
            if(name != null) {
                bar.setTitle(name);
            } else {
                bar.setTitle(R.string.no_name);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectDevice();
    }

    @Override
    protected void onPause() {
        disconnectDevice();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_refresh:
                disconnectDevice();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connectDevice();
                    }
                }, 10);

                return true;
            case R.id.action_help:
                Uri uri = Uri.parse(getResources().getString((R.string.app_info_url)));
                startActivity( new Intent( Intent.ACTION_VIEW, uri ) );
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void connectDevice() {
        if (null == mDevice) {
            Log.d(TAG, "no device assigned to activity");
            return;
        }

        if (null != mCallback) {
            // already connected
            Log.d(TAG, "already connected to device");
            return;
        }

        mCallback = new GattCallback();
        mGatt = mDevice.connectGatt(getApplicationContext(),
                false, // direct connect
                mCallback);

        mActivityIndicator = (ProgressBar) findViewById(R.id.progressBar);
        if(null != mActivityIndicator) {
            mActivityIndicator.setVisibility(View.VISIBLE);
        }
        hideError();
    }

    private void disconnectDevice() {
        if(mGatt != null) {
            mActiveDisconnect = true;
            mGatt.disconnect();
        }
    }

    // cleanup resources used for BLE connection & UI views
    private void clearConnection() {

        // clear UI elements
        final RelativeLayout viewGroup = (RelativeLayout) findViewById(R.id.remote_layout);
        if(viewGroup != null) {
            viewGroup.removeAllViews();
        }

        // release resources
        mCallback = null;
        mGatt = null;
        mService = null;
        mDeviceNameCharacteristic = null;
        mEventCharacteristic = null;
        mUIUpdateCharacteristic = null;
        mDeviceInfo = null;
        mQuery = null;
        mValues = null;
        mEventListener = null;

        if(mActiveDisconnect){
            mActiveDisconnect = false;
        } else {
            showError(R.string.device_disconnected);
        }

    }

    private void hideError() {
        TextView errorText = (TextView) findViewById(R.id.errorText);
        if (null != errorText) {
            errorText.setVisibility(View.INVISIBLE);
        }
    }

    private void showError(int msg) {
        TextView errorText = (TextView) findViewById(R.id.errorText);
        if (null != errorText) {
            errorText.setText(msg);
            errorText.setVisibility(View.VISIBLE);
        }
    }

    // Note that these callbacks are invoked in a separate context,
    // and MUST NOT call Android UI methods directly. Use mHandler.post() instead
    private class GattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "connected");
                    mGatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "device disconnected");

                    // Post to main thread to clean up
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            RemoteView.this.clearConnection();
                        }
                    });
                    break;
                default:
                    Log.d(TAG, "Unhandled stat=" + String.valueOf(newState));
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Service discovery failed");
                return;
            }

            // Try to get device name
            BluetoothGattService gapService = gatt.getService(UUID.fromString("00001800-0000-1000-8000-00805F9B34FB"));
            if (null != gapService) {
                mDeviceNameCharacteristic = gapService.getCharacteristic(UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB"));
                gatt.readCharacteristic(mDeviceNameCharacteristic);
            }

            // read our own "Remote Control" service info
            mService = gatt.getService(Constants.rcService.getUuid());

            if(mService != null) {
                mValues = new HashMap<>();
                mQuery = new HashSet<>(mService.getCharacteristics());
                mEventCharacteristic = mService.getCharacteristic(Constants.rcEventArray);
                mUIUpdateCharacteristic = mService.getCharacteristic(Constants.rcUIUpdate);
                if(mUIUpdateCharacteristic != null) {
                    gatt.setCharacteristicNotification(mUIUpdateCharacteristic, true);
                }

                for (BluetoothGattCharacteristic c : mQuery) {
                    final boolean result = gatt.readCharacteristic(c);
                    if (!result) {
                        // we failed to continuous read - read later
                        break;
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (mDeviceNameCharacteristic != null && characteristic == mDeviceNameCharacteristic) {
                // Update device name if needed
                final String fullDeviceName = mDeviceNameCharacteristic.getStringValue(0);
                if (!fullDeviceName.isEmpty()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            RemoteView.this.setTitle(fullDeviceName);
                            ActionBar bar = getSupportActionBar();
                            if(null != bar) {
                                 bar.setTitle(fullDeviceName);
                            }
                        }
                    });
                }
            } else {
                // these are Remote Controll service characteristics
                mQuery.remove(characteristic);
                mValues.put(characteristic.getUuid(), characteristic);
            }

            if (mQuery.isEmpty()) {
                Log.d(TAG, "all characteristic read");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        createDeviceLayout();
                    }
                });
            } else {
                // keep reading
                for (BluetoothGattCharacteristic c : mQuery) {
                    final boolean result = gatt.readCharacteristic(c);
                    if (!result) {
                        // we failed to continuous read - read later
                        break;
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if(characteristic == mEventCharacteristic) {
                Log.d(TAG, "onWritten, status = " + String.valueOf(status));
                mWriting.release();
                performWriteOperation();
            }
        }

        @Override
        // Characteristic notification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if(mDeviceInfo == null) {
                return;
            }

            if(characteristic == mUIUpdateCharacteristic) {
                Log.d(TAG, "notification received!");
                final ByteBuffer configDataBuffer = ByteBuffer.wrap(characteristic.getValue());
                configDataBuffer.order(ByteOrder.LITTLE_ENDIAN);
                final byte controlIndex = configDataBuffer.get();
                final byte dataSize = configDataBuffer.get();
                final int headerOffset = configDataBuffer.position();
                try {
                    final String text = new String(characteristic.getValue(), headerOffset, dataSize - 1 , "UTF-8");
                    Log.d(TAG, text);

                    // since we're in BLE context, we need to send task to UI loop.
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // update to corresponding UI controls, if it is a Label control
                            if (mDeviceInfo != null) {
                                for(ControlInfo c : mDeviceInfo.controls){
                                    if (c.index == controlIndex && c.type == ControlInfo.ControlType.label && c.view != null) {
                                        TextView tv = (TextView)c.view;
                                        tv.setText(text);
                                        tv.invalidate();
                                    }
                                }
                            }
                        }
                    });
                } catch (UnsupportedEncodingException e){
                    Log.d(TAG, "encoding error");
                } catch (IndexOutOfBoundsException e) {
                    Log.d(TAG, "corrupted update info");
                }


            }
        }

        private Drawable loadDrawable(int id){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return getResources().getDrawable(id, null);
            } else {
                return getResources().getDrawable(id);
            }
        }

        private void createDeviceLayout() {
            final DeviceInfo d = readDeviceInfo();
            if (null == d) {
                Log.d(TAG, "cannot load device info!");
                return;
            }

            if(d.isLandscape) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

            final RelativeLayout v = (RelativeLayout) findViewById(R.id.remote_layout);
            if (null == v) {
                Log.d(TAG, "root view not found!");
                return;
            }

            // Setup UI resources
            mEventListener = new UIEventListener();

            final int padding = Constants.VIEW_PADDING;
            final int cw = v.getWidth() / d.col;
            final int ch = v.getHeight() / d.row;

            for (ControlInfo c : d.controls) {
                final int controlWidthInCell = c.cell.width();
                final int controlHeightInCell = c.cell.height();
                c.cell.left *= cw;
                c.cell.top *= ch;
                c.cell.right = c.cell.left + controlWidthInCell * cw;
                c.cell.bottom = c.cell.top + controlHeightInCell * ch;
                c.cell.inset(padding, padding);

                final int fontColor = Brandcolor.font.primary;

                View component = null;
                switch (c.type) {
                    case label: {
                        // labels are disabled buttons
                        TextView b = new TextView(RemoteView.this);
                        b.setBackground(loadDrawable(R.drawable.rectangle_label));
                        b.getBackground().setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);
                        b.setText(c.text);
                        b.setTextColor(fontColor);
                        b.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
                        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                        component = b;
                        break;
                    }
                    case circleButton: {
                        Button b = new Button(RemoteView.this);
                        b.setBackground(loadDrawable(R.drawable.round_button));
                        b.getBackground().setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);

                        b.setText(c.text);
                        b.setTextColor(fontColor);
                        b.setTypeface(b.getTypeface(), Typeface.BOLD);

                        // we want to make a "square" circle, not an oval - adjust cell rect.
                        final int cX = c.cell.centerX();
                        final int cY = c.cell.centerY();
                        final int radius = Math.min(c.cell.width(), c.cell.height()) / 2;
                        c.cell.left = cX - radius;
                        c.cell.right = cX + radius;
                        c.cell.top = cY - radius;
                        c.cell.bottom = cY + radius;

                        b.setTag(c);
                        b.setOnTouchListener(mEventListener);
                        component = b;
                        break;
                    }
                    case pushButton: {
                        Button b = new Button(RemoteView.this);
                        b.setBackground(loadDrawable(R.drawable.rectangle_button));
                        b.getBackground().setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);
                        b.setText(c.text);
                        b.setTextColor(fontColor);
                        b.setTypeface(b.getTypeface(), Typeface.BOLD);

                        b.setOnTouchListener(mEventListener);
                        b.setTag(c);
                        component = b;
                        break;
                    }
                    case switchButton: {
                        // load the switch button view component
                        View switchPanel = LayoutInflater.from(getApplicationContext())
                                .inflate(R.layout.switch_panel, null);
                        switchPanel.setBackground(loadDrawable(R.drawable.rectangle_button));
                        switchPanel.getBackground().setColorFilter(Brandcolor.grey.secondary, PorterDuff.Mode.MULTIPLY);

                        ToggleButton btn = (ToggleButton)switchPanel.findViewById(R.id.switch_button);
                        btn.getBackground().setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);

                        TextView title = (TextView)switchPanel.findViewById(R.id.switch_title);
                        title.setText(c.text);
                        title.setTextColor(fontColor);
                        title.setTypeface(title.getTypeface(), Typeface.BOLD);

                        btn.setTag(c);
                        btn.setOnClickListener(mEventListener);
                        component = switchPanel;
                        break;
                    }
                    case slider: {
                        // load the slider view component
                        View sliderPanel = LayoutInflater.from(getApplicationContext())
                                .inflate(R.layout.slider_panel, null);
                        sliderPanel.setBackground(loadDrawable(R.drawable.rectangle_button));
                        sliderPanel.getBackground().setColorFilter(Brandcolor.grey.secondary, PorterDuff.Mode.MULTIPLY);

                        SeekBar bar = (SeekBar)sliderPanel.findViewById(R.id.slider_bar);
                        TextView title = (TextView)sliderPanel.findViewById(R.id.slider_title);
                        TextView value = (TextView)sliderPanel.findViewById(R.id.slider_value);

                        bar.getThumb().setColorFilter(c.color, PorterDuff.Mode.SRC_ATOP);
                        bar.getProgressDrawable().setColorFilter(c.color, PorterDuff.Mode.SRC_ATOP);
                        title.setText(c.text);
                        title.setTypeface(title.getTypeface(), Typeface.BOLD);
                        value.setTypeface(value.getTypeface(), Typeface.BOLD);

                        // Note that SeekBar.setMin() is only availble after Android O
                        // so we have to calculate our own value range and map it.
                        // bar.setMin((int)c.config.data1);
                        final int valueRange = c.config.data2 - c.config.data1;
                        final int mappedInitialValue = c.config.data3 - c.config.data1;
                        bar.setMax(valueRange);
                        value.setText(String.valueOf(mappedInitialValue));
                        bar.setProgress(mappedInitialValue);

                        // event handlers
                        bar.setOnSeekBarChangeListener(mEventListener);
                        bar.setTag(c);
                        component = sliderPanel;
                        break;
                    }
                    case joystick: {
                        // MARK: Joystick
                        JoystickView j = new JoystickView(getApplicationContext());

                        j.setTag(c);
                        j.setButtonColor(c.color);
                        j.setBackgroundColor(Brandcolor.grey.secondary);
                        final float thumbSize = 0.3f;
                        final float bgSize = 1.0f - thumbSize;
                        j.setButtonSizeRatio(thumbSize);
                        j.setBackgroundSizeRatio(bgSize);

                        // unfortunately setOnMoveListener does not pass
                        // the View object through the parameter.
                        // Therefore we need to reference controlInfo (c)
                        // from the closure. "final" is required by Java's closure.
                        final ControlInfo cInfo = c;
                        j.setOnMoveListener( new JoystickView.OnMoveListener() {
                                long mLastSentTime = SystemClock.elapsedRealtime();
                                @Override
                                public void onMove(int angle, int strength) {
                                    double rad = Math.toRadians(angle);
                                    double x = Math.cos(rad) * strength * 1;
                                    double y = Math.sin(rad) * strength * 1;
                                    x = Math.min(x, 100);
                                    y = Math.min(y, 100);
                                    x = Math.max(x, -100);
                                    y = Math.max(y, -100);

                                    final int bx = (int)x;
                                    final int by = (int)y;

                                    Log.d(TAG, String.format("(%d, %d) -> (%d, %d)", angle, strength, bx, by));
                                    int data = (bx << 8) | (by & 0xFF);

                                    // data = 0 (joystick centered) -> always send
                                    // others -> send every 100ms
                                    boolean shouldSend = (data == 0) ||
                                            ((SystemClock.elapsedRealtime() - mLastSentTime) > 100);

                                    if(shouldSend) {
                                        sendRemoteEvent(cInfo, ControlEvent.valueChange, data);
                                        mLastSentTime = SystemClock.elapsedRealtime();
                                    } else {
                                        Log.d(TAG, "joystick event dropped");
                                    }
                                }

                            });

                        // assign to component
                        component = j;
                        break;
                    }
                    default:
                        break;
                }

                // layout the control
                if (component != null) {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(c.cell.width(), c.cell.height());
                    params.leftMargin = c.cell.left;
                    params.topMargin = c.cell.top;
                    v.addView(component, params);
                    c.view = component;
                }
            }

            mActivityIndicator.setVisibility(View.INVISIBLE);
            mDeviceInfo = d;
        }

        private DeviceInfo readDeviceInfo() {
            if (mValues.isEmpty()) {
                return null;
            }


            // check for versions
            BluetoothGattCharacteristic versionChar = mValues.get(Constants.rcProtocolVersion);
            if(null == versionChar || Constants.PROTOCOL_VERSION !=
                            versionChar.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0)) {
                showError(R.string.protocol_mismatch);
            }


            DeviceInfo d = new DeviceInfo();

            d.row = mValues.get(Constants.rcRow).
                    getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
            d.col = mValues.get(Constants.rcCol).
                    getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
            d.isLandscape = mValues.get(Constants.rcOrientation).
                    getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0) > 0;

            final int controlCount = mValues.get(Constants.rcControlCount).
                    getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
            final byte[] typeArray = mValues.get(Constants.rcControlTypes).getValue();
            final byte[] colorArray = mValues.get(Constants.rcColors).getValue();
            final byte[] rectArray = mValues.get(Constants.rcFrames).getValue();
            final byte[] configArray = mValues.get(Constants.rcConfigDataArray).getValue();
            final String nameString = mValues.get(Constants.rcNames).getStringValue(0);
            final String[] names = nameString.split("\n");

            d.controls = new ControlInfo[controlCount];

            // Note that config data are uint16_t stored in little endian byte order.
            final ByteBuffer configDataBuffer = ByteBuffer.wrap(configArray);
            configDataBuffer.order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < controlCount; ++i) {
                ControlInfo c = new ControlInfo();
                c.index = i;
                c.type = ControlInfo.ControlType.getEnum(typeArray[i]);
                c.color = Brandcolor.fromBLE(colorArray[i]).primary;
                c.cell = new Rect();
                c.cell.left = rectArray[i * 4];
                c.cell.top = rectArray[i * 4 + 1];
                c.cell.right = c.cell.left + rectArray[i * 4 + 2];
                c.cell.bottom = c.cell.top + rectArray[i * 4 + 3];
                try {
                    c.text = names[i];
                } catch (Exception e) {
                    c.text = getResources().getString(R.string.no_name);
                }

                c.config = new ControlConfig();
                c.config.data1 = configDataBuffer.getShort();
                c.config.data2 = configDataBuffer.getShort();
                c.config.data3 = configDataBuffer.getShort();
                c.config.data4 = configDataBuffer.getShort();
                d.controls[i] = c;
            }

            return d;
        }

    }

    private void sendRemoteEvent(ControlInfo c, int event, int data) {
        if(null == mGatt || null == mEventCharacteristic) {
            return;
        }

        byte[] eventData = new byte[6];
        final int index = c.index;

        eventData[0] = mEventSeq;                   // sequence number - increment it
        mEventSeq += 1;
        eventData[1] = (byte)index;                 // control index
        eventData[2] = (byte)event;                 // event
        eventData[3] = 0;                           // ignored & reserved
        eventData[4] = (byte)(data & 0xFF);         // event data, high byte
        eventData[5] = (byte)((data >> 8) & 0xFF);  // event data, low byte

        mWriteRequests.add(eventData);
        performWriteOperation();
    }

    private void performWriteOperation() {
        if(mWriteRequests.isEmpty()){
            Log.d(TAG, "Nothing to write");
            return;
        }

        if(mWriting.tryAcquire()){
            // lock acquired, start writing
            try {
                mEventCharacteristic.setValue(mWriteRequests.take());
                final boolean writeInitiated = mGatt.writeCharacteristic(mEventCharacteristic);
                Log.d(TAG, "write event " + String.valueOf(mEventSeq) + ", initiated=" + String.valueOf(writeInitiated));
                // we'll release the mWriting lock in  "onCharacteristicWritten"
            } catch (InterruptedException e) {
                e.printStackTrace();
                mWriting.release();
            }
        } else {
            Log.d(TAG, "Writing - perform later");
        }
    }

    private class UIEventListener implements View.OnTouchListener,
                                             SeekBar.OnSeekBarChangeListener,
                                             ToggleButton.OnClickListener {
        public boolean onTouch(View var1, MotionEvent var2) {
            ControlInfo c = (ControlInfo)var1.getTag();
            Button b = (Button)var1;

            Log.d(TAG, "btn state=" + String.valueOf(var2));

            switch(var2.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendRemoteEvent(c, ControlEvent.valueChange, 1);
                    break;
                case MotionEvent.ACTION_UP:
                    sendRemoteEvent(c, ControlEvent.valueChange, 0);
                    break;
                default:
                    // unrecognized action
                    break;
            }


            return false;
        }

        public void onProgressChanged(SeekBar var1, int var2, boolean var3){
            ControlInfo c = (ControlInfo)var1.getTag();

            // update label, note that seekbar range is data2 - data1
            TextView valueLabel = (TextView)c.view.findViewById(R.id.slider_value);
            final int value = c.config.data1 + var1.getProgress();
            valueLabel.setText(String.valueOf(value));

            // send update according to interval
            final long EVENT_INTERVAL_MS = 100;
            final long currentTime = SystemClock.elapsedRealtime();
            if(currentTime - mLastSliderUpdateTime > EVENT_INTERVAL_MS) {
                mLastSliderUpdateTime = currentTime;
                sendRemoteEvent(c, ControlEvent.valueChange, value);
            }
        }

        public void onStartTrackingTouch(SeekBar var1) {
            // do nothing, but mandatory to implement.
            mLastSliderUpdateTime = SystemClock.elapsedRealtime();
        }

        public void onStopTrackingTouch(SeekBar var1) {
            ControlInfo c = (ControlInfo)var1.getTag();
            // note that seekbar stores range, and data1 is min value
            final int seekEnd = var1.getProgress();
            final int value = c.config.data1 + seekEnd;
            Log.d(TAG, "seek end = " + String.valueOf(seekEnd) + "value = " + String.valueOf(value));
            sendRemoteEvent(c, ControlEvent.valueChange, value);
        }

        public void onClick(View var1) {
            ControlInfo c = (ControlInfo)var1.getTag();
            if(c.type == ControlInfo.ControlType.switchButton) {
                ToggleButton btn = (ToggleButton)var1;
                Log.d(TAG, "toggle = " + String.valueOf(btn.isChecked()));
                sendRemoteEvent(c, ControlEvent.valueChange, btn.isChecked() ? 1 : 0);
            }
        }

    }
}
