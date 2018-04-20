package com.mediatek.labs.linkitremote;

import android.Manifest;
import android.annotation.TargetApi;
import android.location.LocationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceList extends AppCompatActivity {

    static private final String TAG = "DeviceList";
    public static final String CONNECT_DEVICE_MESSAGE = "com.mediatek.labs.linkitremote.connect_device";


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mScanner;
    private ScanResultAdapter mListAdapter;
    private ScanCallback mScanCallback;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        setTitle(R.string.activity_main_title);

        // initialize error text label
        TextView view = (TextView) findViewById(R.id.error_textview);
        view.setVisibility(View.INVISIBLE);

        // setup UI data source and scan resources
        mListAdapter = new ScanResultAdapter(this.getApplicationContext(), this.getLayoutInflater());
        final ListView listView = (ListView) findViewById(R.id.device_list);
        listView.setAdapter(mListAdapter);
        mHandler = new Handler();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                ScanResult tappedItem = (ScanResult) adapterView.getItemAtPosition(position);
                launchRemoteView(tappedItem);
            }
        });

        // make sure BLE enabled
        if (savedInstanceState == null) {

            mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

            // Is Bluetooth supported on this device?
            if (mBluetoothAdapter != null) {
                // Is Bluetooth turned on?
                if (mBluetoothAdapter.isEnabled()) {
                    // Everything is supported and enabled, load the fragments.
                    scanDevices();
                } else {
                    // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            } else {

                // Bluetooth is not supported.
                showErrorText(R.string.bt_not_supported);
            }
        }
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
                scanDevices();
                return true;
            case R.id.action_help:
                Uri uri = Uri.parse(getResources().getString((R.string.app_info_url)));
                startActivity( new Intent( Intent.ACTION_VIEW, uri ) );
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // User BT enable response
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT:

                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Bluetooth enabled");
                    scanDevices();
                } else {

                    // User declined to enable Bluetooth, exit the app.
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void scanDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestScanPermission();
        } else {
            scanDevicesWithPermission();
        }
    }

    // User BT permision reponse (Android M or later)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted
                    scanDevicesWithPermission();
                } else {
                    // Permission Denied
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT)
                            .show();

                    finish();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestScanPermission() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();

        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("scan BLE devices");

        if (permissionsList.size() == 0) {
            // we already got permission
            scanDevicesWithPermission();
        } else {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                        Constants.REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                return;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    Constants.REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

    private void scanDevicesWithPermission() {
        if (mScanCallback != null) {
            // scanning
            Toast.makeText(this, R.string.already_scanning, Toast.LENGTH_SHORT).show();
        } else {

            boolean needSetting = false;
            if (null == mBluetoothAdapter) {
                mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                Log.d(TAG, "mBluetoothAdapter reinitialized");
            }

            if (null == mBluetoothAdapter) {
                // Bluetooth is not supported.
                showErrorText(R.string.bt_not_supported);
                needSetting = true;
                Log.d(TAG, "mBluetoothAdapter reinitialize failed");
            }

            try {
                LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                boolean gps_enabled = false;
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if(!gps_enabled) {
                    Log.d(TAG, "no GPS available");
                    needSetting = true;
                }
            } catch(Exception ex) {
                // BLE scanner requires location service
                Log.d(TAG, "excetpion", ex);
                needSetting = true;
                throw ex;
            }

            if(needSetting){
                showErrorDialog(R.string.enable_location_service);
                return;
            }

            // build scanner and start scanning
            // Will stop the scanning after a set time.
            if(mHandler != null) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScanning();
                    }
                }, Constants.SCAN_PERIOD_MS);
            }

            try {
                mScanner = mBluetoothAdapter.getBluetoothLeScanner();

                if (null == mScanner) {
                    // Bluetooth is not supported.
                    showErrorText(R.string.bt_not_supported);
                    return;
                }

                Log.d(TAG, "create scancallback");
                mScanCallback = new SampleScanCallback();
                Log.d(TAG, "create scancallback done");

                mScanner.startScan(buildScanFilters(),
                        buildScanSettings(),
                        mScanCallback);
            } catch (java.lang.Exception e) {
                Log.e(TAG, "exception", e);
                showErrorText(R.string.bt_not_supported);
                return;
            }


            // Update UI
            ProgressBar b = (ProgressBar)findViewById(R.id.scanBar);
            b.setVisibility(View.VISIBLE);
            b.setIndeterminate(true);
            Toast.makeText(getApplicationContext(), R.string.scan_start_toast, Toast.LENGTH_LONG).show();
            TextView view = (TextView) findViewById(R.id.error_textview);
            view.setVisibility(View.INVISIBLE);
        }
    }

    private void stopScanning() {
        mScanner.stopScan(mScanCallback);
        mScanCallback = null;
        ProgressBar b = (ProgressBar)findViewById(R.id.scanBar);
        b.setVisibility(View.INVISIBLE);

        if (mListAdapter.getCount() <= 0){
            showErrorText(R.string.empty_list);
        }
    }

    private void showErrorText(int messageId) {
        TextView view = (TextView) findViewById(R.id.error_textview);
        view.setText(getString(messageId));
        view.setVisibility(View.VISIBLE);
    }

    private void showErrorDialog(int messageId) {
        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
        dlgAlert.setMessage(getString(messageId));
        dlgAlert.setTitle(getString(R.string.need_setting));
        dlgAlert.setPositiveButton(getString(R.string.setting_app),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
                    }
                });
        dlgAlert.setCancelable(true);
        dlgAlert.create().show();

    }


    /**
     * Return a List of {@link ScanFilter} objects to filter by Service UUID.
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanFilter.Builder builder = new ScanFilter.Builder();
        // Comment out the below line to see all BLE devices around you
        builder.setServiceUuid(Constants.rcService);
        scanFilters.add(builder.build());

        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object set to use low power (to preserve battery life).
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        return builder.build();
    }


    /**
     * Custom ScanCallback object - adds to adapter on success, displays error on failure.
     */
    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for (ScanResult result : results) {
                mListAdapter.add(result);
            }
            mListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            mListAdapter.add(result);
            mListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(getApplicationContext(), "Scan failed with error: " + errorCode, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void launchRemoteView(ScanResult tappedItem) {
        Intent intent = new Intent(this, RemoteView.class);
        intent.putExtra(CONNECT_DEVICE_MESSAGE, tappedItem.getDevice());
        startActivity(intent);
    }
}
