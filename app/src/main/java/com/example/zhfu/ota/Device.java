package com.example.zhfu.ota;

import android.bluetooth.BluetoothDevice;

/**
 * Created by zhfu on 3/27/2017.
 * Description:
 */

public class Device {
    private String name = null, bdAddr = null;
    private int rssi = 0;

    public Device() {
    }

    public Device(String name, String bdAddr, int rssi) {
        this.name = name;
        this.bdAddr = bdAddr;
        this.rssi = rssi;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBdAddr() {
        return bdAddr;
    }

    public void setBdAddr(String bdAddr) {
        this.bdAddr = bdAddr;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
}
