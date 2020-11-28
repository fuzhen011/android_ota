package com.example.zhfu.ota;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by zhfu on 5/25/2017.
 */

public class ChooseDeviceFragment extends Fragment implements ChooseDeviceAdapter.OnItemClickListener{
    private static final String TAG = "ChooseDeviceFragment";

    private RecyclerView recyclerView;
    private ChooseDeviceAdapter adapter;
    private List<Device> pList;
    private BluetoothLeScanner bleScanner;

    private BroadcastReceiver receiver;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: ");
        View view = inflater.inflate(R.layout.choose_device, container, false);
        pList = new ArrayList<>();
        recyclerView = (RecyclerView) view.findViewById(R.id.recyc_view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onActivityCreated: ");
        super.onActivityCreated(savedInstanceState);
        adapter = new ChooseDeviceAdapter(this, pList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        final BluetoothManager bleManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        if(bleManager != null) {
            BluetoothAdapter mBLEAdapter = bleManager.getAdapter();
            bleScanner = mBLEAdapter.getBluetoothLeScanner();
//            startScan();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.OPEN_DRAWER_ACTION);
        intentFilter.addAction(MainActivity.CLOSE_DRAWER_ACTION);

         receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()){
                    case MainActivity.OPEN_DRAWER_ACTION:
                        startScan();
                        break;
                    case MainActivity.CLOSE_DRAWER_ACTION:
                        stopScan();
                        break;
                    default:
                        break;
                }
            }
        };
        getActivity().registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().unregisterReceiver(receiver);
    }

    @Override
    public void onItemClick(View view, int position) {
        Log.i(TAG, "onItemClick: position: "+position);
        MainActivity mainActivity = (MainActivity)getActivity();
        mainActivity.drawerLayout.closeDrawers();
        mainActivity.setAddress(pList.get(position).getBdAddr());
    }

    private void startScan(){
        if(bleScanner == null){
            return;
        }
        Log.i(TAG, "startScan: ");
        bleScanner.startScan(scanCallback);
    }

    private void stopScan(){
        if(bleScanner == null){
            return;
        }
        Log.i(TAG, "stopScan: ");
        bleScanner.stopScan(scanCallback);
    }

    ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            switch(callbackType) {
                case ScanSettings.CALLBACK_TYPE_ALL_MATCHES:
//                    Log.i(TAG, "onScanResult: all match");
                    BluetoothDevice bleDevice = result.getDevice();
                    Device device = new Device(bleDevice.getName(), bleDevice.getAddress(), result.getRssi());
                    Iterator<Device> iterator = pList.iterator();
                    boolean changed = false, matched = false;
                    int position = 0;
                    while(iterator.hasNext()) {
                        Device deviceIterator = iterator.next();
                        if(device.getBdAddr().equals(deviceIterator.getBdAddr())) {
                            matched = true;
                            if(device.getRssi() != deviceIterator.getRssi()) {
                                deviceIterator.setRssi(device.getRssi());
                                changed = true;
                            }
                            position = pList.indexOf(deviceIterator);
                            break;
                        }
                    }
                    if(!matched) {
//                Log.i(TAG, "Doesn't match, add new one--------------------------------");
                        pList.add(device);
                        adapter.notifyItemChanged(pList.size() - 1);
                    } else if(changed) {
//                Log.i(TAG, "Matched, update:------------------------- "+position);
                        adapter.notifyItemChanged(position);
                    }
                    break;
                case ScanSettings.CALLBACK_TYPE_FIRST_MATCH:
                    Log.i(TAG, "onScanResult: first match");
                    break;
                case ScanSettings.CALLBACK_TYPE_MATCH_LOST:
                    Log.i(TAG, "onScanResult: match lost");
                    break;
                default:
                    break;
            }
        }
    };
}
