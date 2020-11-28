package com.example.zhfu.ota;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by zhfu on 3/27/2017.
 */

public class ChooseDeviceAdapter extends RecyclerView.Adapter<ChooseDeviceAdapter.ViewHolder> implements View.OnClickListener{
    private static final String TAG = "ChooseDeviceAdapter";
    private static final int SIGNAL_HIGH = -50;
    private static final int SIGNAL_MID = -70;
    private static final int SIGNAL_LOW = -90;

    private OnItemClickListener mOnItemClickListener = null;
    private List<Device> pList;

    @Override
    public void onClick(View v) {
        if(mOnItemClickListener != null){
            mOnItemClickListener.onItemClick(v, (int)v.getTag());
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View view = null;
        TextView bdAddr, deviceName, rssiValue;
        ImageView rssiImg;
        ViewHolder(View itemView) {
            super(itemView);
            view = itemView;
            bdAddr = (TextView) itemView.findViewById(R.id.bd_addr);
            deviceName = (TextView) itemView.findViewById(R.id.device_name);
            rssiValue = (TextView) itemView.findViewById(R.id.rssi_txt);
            rssiImg = (ImageView) itemView.findViewById(R.id.rssi_image);
        }
    }

    public void setClickListener(OnItemClickListener listener){
        mOnItemClickListener = listener;
    }

    interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public ChooseDeviceAdapter(OnItemClickListener mOnItemClickListener, List<Device> pList) {
        this.mOnItemClickListener = mOnItemClickListener;
        this.pList = pList;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        view.setOnClickListener(this);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Device device = pList.get(position);
        holder.bdAddr.setText(device.getBdAddr());
        holder.deviceName.setText(device.getName());
        holder.rssiValue.setText(String.valueOf(device.getRssi()));
        int rssiTemp = device.getRssi();
        if(rssiTemp < SIGNAL_LOW){
            holder.rssiImg.setImageResource(R.drawable.verylow);
        }else if(rssiTemp < SIGNAL_MID){
            holder.rssiImg.setImageResource(R.drawable.low);
        }else if(rssiTemp < SIGNAL_HIGH){
            holder.rssiImg.setImageResource(R.drawable.mid);
        }else {
            holder.rssiImg.setImageResource(R.drawable.high);
        }
        holder.view.setTag(position);
    }

    @Override
    public int getItemCount() {
        return pList.size();
    }
}
