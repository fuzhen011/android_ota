package com.example.zhfu.ota;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.TabHost;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

public class MyBluetoothLeService extends Service {

    private static final String TAG = "MyBluetoothLeService";
    private MyBinder myBinder = new MyBinder();
    private String targetAddress = null;

    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic controlCharacteristic = null, dataCharacteristic = null;
    private File stackFile, appFile;
    private int mtu;
    private int otaOption = OTA_OPTION_NONE;
    private long startTime = 0;
    private long totalSize = -1;

    private int test_cmd = 0;
    private int test_state = 0;
    private int test_times = 0;
    private int success_times = 0;

    private int bleState = NONE_INIT;

    public static final int IDLE = 0;
    public static final int SCANNING = 1;
    public static final int CONNECTING = 2;
    public static final int TEST_CONNECTED = 3;

    public static final int NONE_INIT = 0;
    public static final int INIT_DONE_IDLE = 1;
    public static final int CONNECTED = 2;
    public static final int DISCOVERED = 3;
    public static final int DFU_MODE = 4;
    public static final int OTA_BEGIN = 5;
    public static final int OTA_UPLOAD_WITH_RSP = 6;
    public static final int OTA_UPLOAD_WITHOUT_RSP = 7;
    public static final int OTA_END = 8;

    public static final int NO_ERROR_INT = 0;
    public static final int TARGET_ADDRESS_NULL = 1;
    public static final int BLE_SCANNER_NOT_FOUND = 2;

    public static final int OTA_SERVICE_NOT_FOUND = 0;
    public static final int OTA_CONTROL_NOT_FOUND = 1;
    public static final int OTA_DATA_NOT_FOUND = 2;
    public static final int OTA_ALL_FOUND = 3;

    public static final int OTA_OPTION_NONE = 0;
    public static final int OTA_OPTION_STACK = 1;
    public static final int OTA_OPTION_APP = 2;

    public static final String ACTION_SCANNING = "com.example.zhfu.ota.ACTION_SCANNING";
    public static final String ACTION_CONNECT_TO_DEVICE = "com.example.zhfu.ota.ACTION_CONNECT_TO_DEVICE";
    public final static String ACTION_GATT_CONNECTED = "com.example.zhfu.ota.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.zhfu.ota.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.zhfu.ota.ACTION_GATT_SERVICES_DISCOVERED";

    public final static String ACTION_OTA_DONE = "com.example.zhfu.ota.ACTION_OTA_DONE";
    public final static String ACTION_OTA_STARTED = "com.example.zhfu.ota.ACTION_OTA_STARTED";
    public final static String ACTION_OTA_PROCESS_UPDATE = "com.example.zhfu.ota.ACTION_OTA_PROCESS_UPDATE";
    public final static String ACTION_OTA_FAILED = "com.example.zhfu.ota.ACTION_OTA_FAILED";

    private static final String OTA_SERVICE_UUID = "1d14d6ee-fd63-4fa1-bfa4-8f47b42119f0";
    private static final String OTA_CONTROL_UUID = "f7bf3564-fb6d-4e53-88a4-5e37e0326063";
    private static final String OTA_DATA_UUID = "984227f3-34fc-4045-a5d0-2c581f81a153";

    public int initBle(BluetoothAdapter adapter, String address) {
        if(address == null || "".equals(address)) {
            Log.e(TAG, "initBle: TARGET_ADDRESS_NULL");
            return TARGET_ADDRESS_NULL;
        } else {
            targetAddress = address;
        }
        if((mBluetoothLeScanner = adapter.getBluetoothLeScanner()) == null) {
            Toast.makeText(this, R.string.ble_scanner_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "initBle: Scanner not found");
            return BLE_SCANNER_NOT_FOUND;
        }
//        stackFile = new File("/sdcard/OTAeblfiles/stack.ebl");
//        appFile = new File("/sdcard/OTAeblfiles/app.ebl");

        otaOption = OTA_OPTION_NONE;
        Log.i(TAG, "initBle: NO_ERROR_INT");
        bleState = INIT_DONE_IDLE;
        test_state = IDLE;
//        test_times = 0;
//        success_times = 0;
        return NO_ERROR_INT;
    }

    public int getTest_cmd() {
        return test_cmd;
    }

    public void setTest_cmd(int test_cmd) {
        if(this.test_cmd != test_cmd)
        {
           if(test_cmd != 0)
           {
               test_times = 0;
               success_times = 0;
              startLeScan(true);
           }else{
              startLeScan(false);
              if(mBluetoothGatt != null)
              {
                 mBluetoothGatt.disconnect();
                  mBluetoothGatt.close();
                  mBluetoothGatt = null;
              }
           }
        }
        this.test_cmd = test_cmd;
    }

    public void startLeScan(boolean enable) {
        if(enable) {
            if(test_state >= SCANNING)
                Log.e(TAG, "State Unexpected" + test_state);
            Log.i(TAG, "startLeScan: Scanning Started....");
            broadcastUpdate(ACTION_SCANNING);
            try{
                mBluetoothLeScanner.startScan(myScanCallback);
            } catch(Exception e) {
                e.printStackTrace();
            }

            test_state = SCANNING;
        } else {
            if(test_state < SCANNING)
                Log.e(TAG, "State Unexpected" + test_state);
            Log.i(TAG, "startLeScan: Scanning Stopped");
        try{
            mBluetoothLeScanner.stopScan(myScanCallback);
        } catch(Exception e) {
            e.printStackTrace();
        }
        test_state = IDLE;
        }
    }

    private void startDiscovery() {
        if((bleState < CONNECTED) || (mBluetoothGatt == null)) {
            return;
        }
        Log.i(TAG, "startDiscovery: Discovery Started...");
        mBluetoothGatt.discoverServices();
    }

    private ScanCallback myScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            Log.i(TAG, "onScanResult: ");
            String intentAction;
            switch(callbackType) {
                case ScanSettings.CALLBACK_TYPE_ALL_MATCHES:
                    String response = result.getDevice().getAddress();
//                    Log.i(TAG, "onScanResult: " + response);
                    if(result.getDevice().getAddress().equals(targetAddress)) {
                        if(test_state != SCANNING) {
                            Log.e(TAG, "Scanned, but state error - " + test_state);
                            break;
                        }
                        mBluetoothDevice = result.getDevice();
                        intentAction = ACTION_CONNECT_TO_DEVICE;
//                        startLeScan(false);
                        Log.i(TAG, "Device found");
                        broadcastUpdate(intentAction);
                        test_state = CONNECTING;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public boolean refreshDeviceCache() {
        if(mBluetoothGatt != null) {
            try {
                BluetoothGatt localBluetoothGatt = mBluetoothGatt;
                Method localMethod = localBluetoothGatt.getClass().getMethod(
                        "refresh", new Class[0]);
                if(localMethod != null) {
                    boolean bool = ((Boolean) localMethod.invoke(
                            localBluetoothGatt, new Object[0])).booleanValue();
                    return bool;
                }
            } catch(Exception localException) {
                Log.i(TAG, "An exception occured while refreshing device");
            }
        }
        return false;
    }

    public final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch(newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        broadcastUpdate(ACTION_GATT_CONNECTED);
                        test_state = TEST_CONNECTED;
                        mBluetoothGatt = gatt;
//                        mBluetoothGatt.disconnect();
                        Log.i(TAG, "Connected");
//                        gatt.disconnect();
                        mtu = 200;
                        if(mBluetoothGatt.requestMtu(mtu)) {
//                            Log.i(TAG, "Requested MTU " + mtu + " Successfully");
                        } else {
//                            Log.i(TAG, "Connected, Requested MTU Failed, use default and start discover services and characteristics");
                            mBluetoothGatt.disconnect();
                        }
//                        if(mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
//                            Log.i(TAG, "onConnectionStateChange: Connection update requested Successfully!!!!");
//                        } else {
//                            Log.i(TAG, "onConnectionStateChange: Connection update requested Failed!!!!!!!!!!!!");
//                        }
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.i(TAG, "Disconnected");
                        test_times++;
                        if(test_state == TEST_CONNECTED)
                        {
                            success_times++;
                        }
                        Log.w(TAG, "Disconnect, status = " + status);
                        Log.i(TAG, "total times: " + test_times + ", success times: " + success_times + ", rate = " + ((float)success_times/test_times * 100.0) + "%");
                        broadcastUpdate(ACTION_GATT_DISCONNECTED);
                        mBluetoothGatt = gatt;
//                        closeAll();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        test_state = SCANNING;
//                        startLeScan(true);
//                        boolean res = refreshDeviceCache();
//                        Log.i(TAG, "onConnectionStateChange: refresh: " + res);
//                        Log.i(TAG, "onConnectionStateChange: Disconnected");
//                        if(bleState == DFU_MODE) {
//                            bleState = INIT_DONE_IDLE;
//                            startLeScan(true);
//                            Log.i(TAG, "onConnectionStateChange: DFU Disconnect, Scan again");
//                        } else if(bleState > DFU_MODE && bleState != OTA_END) {
//                            broadcastUpdate(ACTION_OTA_FAILED);
//                            bleState = INIT_DONE_IDLE;
////                            startLeScan(true);
//                            closeAll();
//                            Log.i(TAG, "onConnectionStateChange: OTA Failed");
//                        }
//                        bleState = INIT_DONE_IDLE;
                        break;
                    default:
                        Log.i(TAG, "new GATT state" + newState);
                        break;
                }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                bleState = DISCOVERED;
                Log.i(TAG, "Services and Charateristics Discovery Done...");
                switch(foundOtaServiceAndCharacteristic()) {
                    case OTA_SERVICE_NOT_FOUND:
                        Log.e(TAG, "OTA_SERVICE_NOT_FOUND");
                        errorCloseAll();
                        break;
                    case OTA_CONTROL_NOT_FOUND:
                        Log.e(TAG, "OTA_CONTROL_NOT_FOUND");
                        errorCloseAll();
                        break;
                    case OTA_DATA_NOT_FOUND:
                        Log.i(TAG, "OTA_DATA_NOT_FOUND");
                        byte[] bytes = new byte[1];
                        bytes[0] = '\0';
                        writeCharacteristic(controlCharacteristic, bytes);
                        bleState = DFU_MODE;
                        break;
                    case OTA_ALL_FOUND:
                        bleState = OTA_BEGIN;
                        Log.i(TAG, "OTA_ALL_FOUND, Set to DFU mode...");
                        writeCharacteristic(controlCharacteristic, new byte[]{0});
                        break;
                    default:
                        Log.i(TAG, "onServicesDiscovered: ERROR!!!!");
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                if(characteristic.getUuid().equals(controlCharacteristic.getUuid())) {
                    Log.i(TAG, "<Write response from OTA control characteristic START................>");
                    if(bleState == OTA_BEGIN) {
                        Log.i(TAG, "Set to DFU mode(write 0x00 to OTA control characteristic)... OK");
                        if((dataCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                            Log.i(TAG, "Set Write type to WITH response");
                            dataCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            bleState = OTA_UPLOAD_WITH_RSP;
                        } else if((dataCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            Log.i(TAG, "Set Write WITHOUT response");
                            dataCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                            bleState = OTA_UPLOAD_WITHOUT_RSP;
                        } else {
                            Log.e(TAG, "ERROR: Write type error!!! Write type = " + dataCharacteristic.getWriteType());
                            errorCloseAll();
                            return;
                        }
                        writeOtaDataToTarget();
                    } else if(bleState == OTA_UPLOAD_WITH_RSP || bleState == OTA_UPLOAD_WITHOUT_RSP) {
                        Log.i(TAG, "OTA Finished successfully(write 0x03 to OTA control characteristic)... OK");
                        otaOption = OTA_OPTION_NONE;
                        bleState = OTA_END;
                        long timeNow = System.currentTimeMillis();
                        long totalTime = timeNow - startTime;
                        broadcastUpdate(ACTION_OTA_DONE, totalTime, null);
                        closeAll();
                    }
                    Log.i(TAG, "<Write response from OTA control characteristic END................>");
                }
//                else if(characteristic.getUuid().equals(dataCharacteristic.getUuid())){
//                    Log.i(TAG, "onCharacteristicWrite: data Characteristic write successfully");
//                }
            } else {
                if(bleState == OTA_UPLOAD_WITHOUT_RSP || bleState == OTA_UPLOAD_WITH_RSP) {
                    if(characteristic.getUuid().equals(controlCharacteristic.getUuid())) {
                        Log.e(TAG, "Failed to write 0x03 to control characteristic... Error response = " + status);
                    } else if(characteristic.getUuid().equals(dataCharacteristic.getUuid())) {
                        Log.e(TAG, "Failed to write data to data characteristic... Error response = " + status);
                    }
                    errorCloseAll();
                    /*
                    * When this error happens, check if the device has been programmed with the Gecko Bootloader.
                    * */
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU Exchanged: " + mtu + " bytes. ---> Starting discover services and characteristics.");
                MyBluetoothLeService.this.mtu = mtu;
                Log.i(TAG, "Disconnecting");
                mBluetoothGatt.disconnect();
            }
        }
    };

    public void writeOtaDataToTarget() {
        if(otaOption == OTA_OPTION_NONE) {
            Log.e(TAG, "writeOtaDataToTarget: OTA Option NONE!!!!");
            errorCloseAll();
        } else if(otaOption == OTA_OPTION_STACK) {
            totalSize = stackFile.length();
        } else if(otaOption == OTA_OPTION_APP) {
            totalSize = appFile.length();
        }

        if(totalSize != 0L) {
            Log.i(TAG, "writeOtaDataToTarget: total length = " + totalSize);
        } else {
            Log.e(TAG, "writeOtaDataToTarget: file size error");
            errorCloseAll();
            return;
        }

        startTime = System.currentTimeMillis();

        new Thread(new Runnable() {
            byte[] bytes = new byte[mtu - 3];
            FileInputStream fis = null;
            int len = -1;
            long progress = 0;

            @Override
            public void run() {
                long totalSentSize = 0;
                long timeNow;
                if(bleState != OTA_UPLOAD_WITHOUT_RSP && bleState != OTA_UPLOAD_WITH_RSP) {
                    Log.e(TAG, "run: State Error: " + bleState);
                    return;
                }
                broadcastUpdate(ACTION_OTA_STARTED, totalSize, null);

                try {
                    if(otaOption == OTA_OPTION_STACK) {
                        fis = new FileInputStream(stackFile);
                    } else if(otaOption == OTA_OPTION_APP) {
                        fis = new FileInputStream(appFile);
                    }

                    while(true) {
                        len = fis.read(bytes);
                        if(len != -1) {
                            if((bleState == OTA_UPLOAD_WITHOUT_RSP) || (bleState == OTA_UPLOAD_WITH_RSP)) {
                                if(len == mtu - 3) {
                                    while(!writeCharacteristic(dataCharacteristic, bytes)) {
                                        if((bleState != OTA_UPLOAD_WITHOUT_RSP) && (bleState != OTA_UPLOAD_WITH_RSP)) {
                                            Log.e(TAG, "Write data characteristic state ERROR, bleState = " + bleState);
                                            errorCloseAll();
                                            return;
                                        }
                                        Thread.sleep(5);
                                    }
                                } else {
                                    byte[] lastBytes = new byte[len];
                                    System.arraycopy(bytes, 0, lastBytes, 0, len);
                                    while(!writeCharacteristic(dataCharacteristic, lastBytes)) {
                                        if((bleState != OTA_UPLOAD_WITHOUT_RSP) && (bleState != OTA_UPLOAD_WITH_RSP)) {
                                            Log.e(TAG, "Write data characteristic state ERROR, bleState = " + bleState);
                                            errorCloseAll();
                                            return;
                                        }
                                        Thread.sleep(5);
                                    }
                                }
//                                Log.i(TAG, "sleep 10 ms - interval between packets");
                                Thread.sleep(10);
                            } else {
                                Log.e(TAG, "Error State while writing to Data characteristic, bleState = " + bleState);
                                errorCloseAll();
                                return;
                            }
                            totalSentSize += len;
                            if(totalSentSize * 100 / totalSize - progress > 0) {
                                String dataRateStr = null;
                                timeNow = System.currentTimeMillis();
                                progress = totalSentSize * 100 / totalSize;
                                if(progress % 5 == 0) {
                                    double dataRate = (double) totalSentSize * 8 / (double) (timeNow - startTime);
                                    dataRateStr = String.format("%.2f", dataRate);
                                }
                                broadcastUpdate(ACTION_OTA_PROCESS_UPDATE, progress, dataRateStr);
                            }
                        } else {
                            String dataRateStr = null;
                            timeNow = System.currentTimeMillis();
                            double dataRate = (double) totalSentSize * 8 / (double) (timeNow - startTime);
                            dataRateStr = String.format("%.2f", dataRate);
                            broadcastUpdate(ACTION_OTA_PROCESS_UPDATE, 100, dataRateStr);
                            Log.i(TAG, "run: finished, write 0x03 to control.");
                            while(!writeCharacteristic(controlCharacteristic, new byte[]{0x03})) {
                                Thread.sleep(20);
                            }
                            Log.i(TAG, "run: OTA sending Data DONE, total bytes: " + totalSentSize);
                            break;
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    if(fis != null) {
                        try {
                            fis.close();
                            fis = null;
                        } catch(IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] bytes) {
        characteristic.setValue(bytes);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void closeAll() {
        mBluetoothGatt.disconnect();
        mBluetoothDevice = null;
        targetAddress = null;
    }

    private void errorCloseAll() {
        Log.e(TAG, "ERROR CLOSE ALL!!!");
        closeAll();
    }

    private int foundOtaServiceAndCharacteristic() {
        BluetoothGattService mBluetoothGattService = mBluetoothGatt.getService(UUID.fromString(OTA_SERVICE_UUID));
        if(mBluetoothGattService == null) {
            Log.e(TAG, "foundOtaServiceAndCharacteristic: Service not found");
            return OTA_SERVICE_NOT_FOUND;
        }
        controlCharacteristic = mBluetoothGattService.getCharacteristic(UUID.fromString(OTA_CONTROL_UUID));
        if(controlCharacteristic == null) {
            Log.e(TAG, "foundOtaServiceAndCharacteristic: Control Char not found");
            return OTA_CONTROL_NOT_FOUND;
        }
        dataCharacteristic = mBluetoothGattService.getCharacteristic(UUID.fromString(OTA_DATA_UUID));
        if(dataCharacteristic == null) {
            Log.i(TAG, "foundOtaServiceAndCharacteristic: Data Characteristic NOT FOUND....");
            return OTA_DATA_NOT_FOUND;
        }
        return OTA_ALL_FOUND;
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        intent.putExtra("total", test_times);
        intent.putExtra("success", success_times);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, long progress, String dataRate) {
        final Intent intent = new Intent(action);
        intent.putExtra("progress", progress);
        intent.putExtra("datarate", dataRate);
        sendBroadcast(intent);
    }

    public class MyBinder extends Binder {
        MyBluetoothLeService getMyBluetoothLeServiceInstance() {
            return MyBluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public void setMtu(int mtu) {
        int payloadSize = mtu - 3;
        int yushu = payloadSize % 4;
        if(yushu == 0) {
            this.mtu = mtu;
        } else {
            this.mtu = mtu - yushu;
        }
    }

    public void setOtaOption(int otaOption) {
        if(otaOption >= 0 && otaOption <= 3) {
            this.otaOption = otaOption;
        }
    }

    public boolean setFileFormate(int fileFormate) {
        String path = Environment.getExternalStorageDirectory().getPath();
        if(fileFormate == R.id.gbl_select) {
            stackFile = new File(path + "/OTAfiles/stack.gbl");
            appFile = new File(path + "/OTAfiles/app.gbl");
        } else if(fileFormate == R.id.ebl_select) {
            stackFile = new File(path + "/OTAfiles/stack.ebl");
            appFile = new File(path + "/OTAfiles/app.ebl");
        } else {
            return false;
        }
        if(!stackFile.exists() && !appFile.exists()) {
            Log.e(TAG, "initBle: EBL_FILE_NOT_FOUND");
            return false;
        }
        return true;
    }

    public BluetoothDevice getmBluetoothDevice() {
        return mBluetoothDevice;
    }
}
