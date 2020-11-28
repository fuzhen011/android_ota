package com.example.zhfu.ota;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private MyBluetoothLeService myBluetoothLeService;

    public static final String OPEN_DRAWER_ACTION = "com.example.zhfu.ota.OPEN_DRAWER_ACTION";
    public static final String CLOSE_DRAWER_ACTION = "com.example.zhfu.ota.CLOSE_DRAWER_ACTION";
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_COARSE_LOCATION = 2;
    private static final int REQUEST_WRITE_SD = 3;

    DrawerLayout drawerLayout;

    private MyBroadcastReceiver myBroadcastReceiver;

    private TextView totalSizeText, totalTimeText, otaProgressText, dataRateText, debugInfoText;

    private RadioGroup fileFormatGroup;
    
    private long totalSizeTemp = 0;
    private EditText addressEdit;

    private SharedPreferences sp;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBluetoothLeService = ((MyBluetoothLeService.MyBinder) service).getMyBluetoothLeServiceInstance();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        BluetoothManager mBluetoothManager;
        if((mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)) != null) {
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if(!mBluetoothAdapter.isEnabled() || mBluetoothAdapter == null) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            Toast.makeText(this, R.string.ble_manager_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
        }

        addressEdit = (EditText) findViewById(R.id.bdaddr_edit);

        Button otaStackBtn = (Button) findViewById(R.id.ota_stack_btn);
        Button otaAppBtn = (Button) findViewById(R.id.ota_app_btn);
        Button scanBtn = (Button) findViewById(R.id.scan_btn);
        Button homeBtn = (Button)findViewById(R.id.home_btn);
        homeBtn.setOnClickListener(this);
        scanBtn.setOnClickListener(this);
        otaStackBtn.setOnClickListener(this);
        otaAppBtn.setOnClickListener(this);

        totalSizeText = (TextView) findViewById(R.id.total_size_text);
        totalTimeText = (TextView) findViewById(R.id.total_time_text);
        otaProgressText = (TextView) findViewById(R.id.progress_text);
        dataRateText = (TextView) findViewById(R.id.data_rate_text);
        debugInfoText = (TextView) findViewById(R.id.debug_info_text);

        fileFormatGroup = (RadioGroup)findViewById(R.id.file_format_group);
        fileFormatGroup.check(R.id.gbl_select);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        DrawerLayout.DrawerListener drawerListener = new DrawerLayout.DrawerListener() {
            Intent intent;
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                Log.i(TAG, "onDrawerOpened: ");
                intent = new Intent(OPEN_DRAWER_ACTION);
                sendBroadcast(intent);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                Log.i(TAG, "onDrawerClosed: ");
                intent = new Intent(CLOSE_DRAWER_ACTION);
                sendBroadcast(intent);
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        };
        drawerLayout.addDrawerListener(drawerListener);

        myBroadcastReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyBluetoothLeService.ACTION_SCANNING);
        intentFilter.addAction(MyBluetoothLeService.ACTION_CONNECT_TO_DEVICE);
        intentFilter.addAction(MyBluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(MyBluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(MyBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(MyBluetoothLeService.ACTION_OTA_STARTED);
        intentFilter.addAction(MyBluetoothLeService.ACTION_OTA_DONE);
        intentFilter.addAction(MyBluetoothLeService.ACTION_OTA_PROCESS_UPDATE);
        intentFilter.addAction(MyBluetoothLeService.ACTION_OTA_FAILED);
        registerReceiver(myBroadcastReceiver, intentFilter);

        Intent serviceIntent = new Intent(this, MyBluetoothLeService.class);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);

        sp = getSharedPreferences("storedAddress", Context.MODE_PRIVATE);
        String storedAddr = sp.getString("address", "");
        if("".equals(storedAddr)) {
            addressEdit.setText("");
        } else {
            addressEdit.setText(storedAddr);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myBroadcastReceiver);
        unbindService(mServiceConnection);
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case MyBluetoothLeService.ACTION_SCANNING:
//                    updateDebugInfo("State: Scanning For Target Device...");
                    break;
                case MyBluetoothLeService.ACTION_CONNECT_TO_DEVICE:
//                    myBluetoothLeService.setmBluetoothGatt(myBluetoothLeService.getmBluetoothDevice().connectGatt(MainActivity.this, false, myBluetoothLeService.mBluetoothGattCallback));
                    myBluetoothLeService.getmBluetoothDevice().connectGatt(MainActivity.this, false, myBluetoothLeService.mBluetoothGattCallback);
//                    Log.i(TAG, "onReceive: Connecting...");
//                    updateDebugInfo("State: Connecting To Target Device...");
                    break;
                case MyBluetoothLeService.ACTION_GATT_CONNECTED:
//                    updateDebugInfo("State: Connected");
                    break;
                case MyBluetoothLeService.ACTION_GATT_DISCONNECTED:
                    int total = intent.getIntExtra("total", -1);
                    int success = intent.getIntExtra("success", -1);
//                    updateDebugInfo("State: Disconnected");

                    debugInfoText.setText("Total - " + total + ", Success - " + success + "\n");
                    debugInfoText.append("Success rate - " + (float)success / (float)total * 100.0 + "%");
                    break;
                case MyBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
//                    updateDebugInfo("State: Service Discovered");
                    break;
                case MyBluetoothLeService.ACTION_OTA_STARTED:
//                    updateDebugInfo("State: OTA Started...", intent.getLongExtra("progress", -1));
                    break;
                case MyBluetoothLeService.ACTION_OTA_DONE:
//                    updateDebugInfo("State: OTA Done", intent.getLongExtra("progress", -1));
                    break;
                case MyBluetoothLeService.ACTION_OTA_FAILED:
//                    updateDebugInfo("State: OTA Failed");
                    break;
                case MyBluetoothLeService.ACTION_OTA_PROCESS_UPDATE:
//                    updateDebugInfo(intent.getLongExtra("progress", -1), intent.getStringExtra("datarate"));
                    break;
            }
        }
    }

    private void updateDebugInfo(long progress, String dataRate) {
        if(progress == -1) {
            return;
        }
        otaProgressText.setText(progress + "%");
        if(dataRate != null) {
            dataRateText.setText(dataRate + "kbps");
        }

    }

    private void updateDebugInfo(final String data, final long size) {
        if(data == null || size == -1) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                debugInfoText.append(data + "\n");
                if(data.equals("State: OTA Done")) {
                    totalTimeText.setText(size + " ms");
                    double dataRate = (double) totalSizeTemp * 8 / (double) (size);
                    String dataRateStr = String.format("%.2f", dataRate);
                    dataRateText.setText(dataRateStr + "kbps");
                } else {
                    totalSizeTemp = size;
                    totalSizeText.setText(size + " bytes");
                }
            }
        });
    }

    private void updateDebugInfo(String data) {
        if(data == null) {
            return;
        } else if(data.equals("State: OTA Failed, retrygng...")) {
            clearDebugInfo();
        }
        debugInfoText.append(data + "\n");
    }


    private void clearDebugInfo() {
        totalTimeText.setText("");
        totalSizeText.setText("");
        otaProgressText.setText("");
        dataRateText.setText("");
        debugInfoText.setText("");
    }

    @Override
    public void onClick(View v) {
        String targetAddress;
        switch(v.getId()) {
            case R.id.ota_stack_btn:
                if(myBluetoothLeService.getTest_cmd() == 0)
                {
                    clearDebugInfo();
                    targetAddress = addressEdit.getText().toString();
//                targetAddress = "58:8E:81:A5:4F:11";
//                targetAddress = "00:0B:57:25:FF:C8";
//                targetAddress = "00:0B:57:31:57:E1";
                    if(myBluetoothLeService.initBle(mBluetoothAdapter, targetAddress) != MyBluetoothLeService.NO_ERROR_INT) {
                        Log.e(TAG, "MyBluetoothLeService Init Error!");
                        Toast.makeText(MainActivity.this, "Init Error!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    myBluetoothLeService.setTest_cmd(1);
                }
//                myBluetoothLeService.startLeScan(true);
                break;
            case R.id.ota_app_btn:
                myBluetoothLeService.setTest_cmd(0);
                break;
//                targetAddress = addressEdit.getText().toString();
            //                targetAddress = "00:0B:57:25:FF:C8";
//                targetAddress = "00:0B:57:31:57:E1";
            //                if(myBluetoothLeService.setFileFormate(fileFormatGroup.getCheckedRadioButtonId())){
//                    myBluetoothLeService.setOtaOption(MyBluetoothLeService.OTA_OPTION_APP);
//                    myBluetoothLeService.setMtu(247);
//                    myBluetoothLeService.startLeScan(true);
//                }else{
//                    Toast.makeText(MainActivity.this, "File not Found.", Toast.LENGTH_SHORT).show();
//                }

            case R.id.scan_btn:
            case R.id.home_btn:
                drawerLayout.openDrawer(GravityCompat.START);
                break;

            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_COARSE_LOCATION:
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Pemission denied", Toast.LENGTH_SHORT).show();
                    finish();
                }else {
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_SD);
                    }
                }
                break;
            case REQUEST_WRITE_SD:
                if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Pemission denied", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    public void setAddress(String address){
        addressEdit.setText(address);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("address", address);
        editor.apply();
    }
}
