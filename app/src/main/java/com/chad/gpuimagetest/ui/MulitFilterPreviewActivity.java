package com.chad.gpuimagetest.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.chad.gpuimagetest.MyItemDecoration;
import com.chad.gpuimagetest.R;
import com.chad.gpuimagetest.SurfaceTextureRender;
import com.chad.gpuimagetest.adatper.MulitFilterAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MulitFilterPreviewActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    public static final String TAG = "MulitFilterPreviewAct";

    private SurfaceTextureRender mSurfaceTextureRender;

    private HashMap<TextureView, Integer> textureviewMap = new HashMap<>();
    private MulitFilterAdapter mulitFilterAdapter;
    private RecyclerView recyclerView;
    private FrameLayout singlePlayerContainer;
    private int[] filterResId = {
            -1,
            -1,//－1 default filter GPUImageFilter flag
            R.raw.filter_black_white1,
            R.raw.filter_black_white2,
            R.raw.filter_black_white3,
            R.raw.filter_bopu,
            R.raw.filter_yecan,
            R.raw.filter_qingxing,
            R.raw.filter_guobao,
            R.raw.filter_fenhong,
            R.raw.filter_baohe_low,
            R.raw.filter_honglv,
            R.raw.filter_huanglv,
            R.raw.filter_baohe_high,
            R.raw.filter_test};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mulit_filter_preview);

        singlePlayerContainer = (FrameLayout) findViewById(R.id.single_player_container);
        recyclerView = (RecyclerView) findViewById(R.id.recycler);

        for (String permission : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
            }
        }

        initSinglePlayer();
        initListPlayer();
    }

    private void initRenderThread() {

        mSurfaceTextureRender = new SurfaceTextureRender(this, filterResId);
        mSurfaceTextureRender.setName("TexFromCam Render");
        mSurfaceTextureRender.start();
        mSurfaceTextureRender.waitUntilReady();
    }

    private void initSinglePlayer() {

        TextureView textureView = new TextureView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);//指定死TextureView的宽度
        singlePlayerContainer.addView(textureView, params);

        textureView.setSurfaceTextureListener(this);

        textureviewMap.put(textureView, 0);

    }

    private void initListPlayer() {

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.addItemDecoration(new MyItemDecoration());

        mulitFilterAdapter = new MulitFilterAdapter(this, null);
        recyclerView.setAdapter(mulitFilterAdapter);
    }

    public void filter(View view) {

        if (mulitFilterAdapter.isEmpty()) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < filterResId.length; i++) {
                list.add(filterResId[i]);
            }
            mulitFilterAdapter.setData(list);
            ObjectAnimator.ofFloat(recyclerView, "alpha", 0, 1f).setDuration(500).start();
        } else {
            mulitFilterAdapter.setData(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initRenderThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SurfaceTextureRender.RenderHandler rh = mSurfaceTextureRender.getHandler();
        rh.sendShutdown();
        try {
            mSurfaceTextureRender.join();
        } catch (InterruptedException ie) {
            // not expected
            throw new RuntimeException("join was interrupted", ie);
        }
        mSurfaceTextureRender = null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        Log.d(TAG, "onSurfaceTextureAvailable surfaceTexture=" + surfaceTexture.hashCode());
        if (mSurfaceTextureRender != null) {
            // Normal case -- render thread is running, tell it about the new surface.
            SurfaceTextureRender.RenderHandler rh = mSurfaceTextureRender.getHandler();

//            List<MulitFilterAdapter.FilterBean> filterList = new ArrayList<>();
//            filterList.add(new MulitFilterAdapter.FilterBean(new GPUImageContrastFilter(2.0f)));
//            rh.sendUpdataFilterList(filterList);

            rh.sendSurfaceAvailable(surfaceTexture, i, i1);
        } else {
            // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
            // landscape and a lock screen that requires portrait.  The surface-created
            // message is showing up after onPause().
            //
            // Chances are good that the surface will be destroyed before the activity is
            // unpaused, but we track it anyway.  If the activity is un-paused and we start
            // the RenderThread, the SurfaceHolder will be passed in right after the thread
            // is created.
            Log.i(TAG, "render thread not running");
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        Log.d(TAG, "onSurfaceTextureSizeChanged surfaceTexture=" + surfaceTexture.hashCode());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onSurfaceTextureDestroyed surfaceTexture=" + surfaceTexture.hashCode());
        if (mSurfaceTextureRender != null) {
            SurfaceTextureRender.RenderHandler rh = mSurfaceTextureRender.getHandler();
            rh.sendSurfaceDestroyed(surfaceTexture);
        }
        textureviewMap.remove(surfaceTexture);

        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    public void refreshTextureMap(TextureView textureView, int position) {
        textureviewMap.put(textureView, position);
    }

    public HashMap<TextureView, Integer> getTextureviewMap() {
        return textureviewMap;
    }
}
