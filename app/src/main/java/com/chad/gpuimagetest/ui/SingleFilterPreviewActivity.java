package com.chad.gpuimagetest.ui;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;

import com.chad.gpuimagetest.GLRenderSurfaceView;
import com.chad.gpuimagetest.GPUImageFilterTools;
import com.chad.gpuimagetest.R;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;

public class SingleFilterPreviewActivity extends AppCompatActivity {

    private GLRenderSurfaceView glRenderSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.five);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();

        glRenderSurfaceView = new GLRenderSurfaceView(this, mediaPlayer, mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());

        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.container);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(-1, -1);
        relativeLayout.addView(glRenderSurfaceView, layoutParams);

    }


    public void switchFilter(View view) {

        GPUImageFilterTools.showDialog(this, new GPUImageFilterTools.OnGpuImageFilterChosenListener() {

            @Override
            public void onGpuImageFilterChosenListener(final GPUImageFilter filter, final GPUImageFilterTools.FilterType filterType) {
                glRenderSurfaceView.switchFilter(filter);
            }
        });

    }
}
