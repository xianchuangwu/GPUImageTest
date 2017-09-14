package com.chad.gpuimagetest.adatper;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.chad.gpuimagetest.R;
import com.chad.gpuimagetest.ui.MulitFilterPreviewActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by chad
 * Time 17/9/12
 * Email: wuxianchuang@foxmail.com
 * Description: TODO
 */

public class MulitFilterAdapter extends RecyclerView.Adapter<MulitFilterAdapter.Viewholder> {

    private List<Integer> mData;

    private MulitFilterPreviewActivity activity;

    public MulitFilterAdapter(MulitFilterPreviewActivity mulitFilterPreviewActivity, List<Integer> data) {
        this.mData = data == null ? new ArrayList<Integer>() : data;
        this.activity = mulitFilterPreviewActivity;
    }

    @Override
    public Viewholder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View root = LayoutInflater.from(context).inflate(R.layout.item_mulit_filter_recycler, parent, false);
        return new Viewholder(root);
    }

    @Override
    public void onBindViewHolder(Viewholder holder, int position) {
        holder.textureView.setSurfaceTextureListener(activity);
        activity.refreshTextureMap(holder.textureView, position + 1);
    }

    @Override
    public int getItemCount() {
        if (mData.size() == 0) return 0;
        else return mData.size() - 1;
    }

    public void setData(List<Integer> data) {
        this.mData = data == null ? new ArrayList<Integer>() : data;
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        if (mData.size() == 0) return true;
        return false;
    }

    public class Viewholder extends RecyclerView.ViewHolder {

        public TextureView textureView;

        public Viewholder(View itemView) {
            super(itemView);
            textureView = itemView.findViewById(R.id.texture);
        }
    }
}
