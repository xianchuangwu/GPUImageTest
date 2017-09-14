package com.chad.gpuimagetest;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer;

/**
 * Created by chad
 * Time 17/9/8
 * Email: wuxianchuang@foxmail.com
 * Description: TODO
 */

public class GLRenderSurfaceView extends GLSurfaceView {

    private GPUImageRenderer renderer;

    public GLRenderSurfaceView(Context context, MediaPlayer mediaPlayer, int width, int height) {
        super(context);
        setEGLContextClientVersion(2);

        GPUImageFilterGroup filterGroup = new GPUImageFilterGroup();
        filterGroup.addFilter(new GPUImageExtTexFilter());
        filterGroup.addFilter(new GPUImageFilter());
        renderer = new GPUImageRenderer(filterGroup);

//        renderer.setUpSurfaceTexture(mediaPlayer);
        //传递一个camera对象，就能实现相机滤镜预览
        Camera camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        renderer.setUpSurfaceTexture(camera);

        renderer.setSourceSize(width, height);

        setRenderer(renderer);
    }

    public void switchFilter(GPUImageFilter gpuImageFilter) {
        GPUImageFilterGroup filterGroup = new GPUImageFilterGroup();
        filterGroup.addFilter(new GPUImageExtTexFilter());
        filterGroup.addFilter(gpuImageFilter);
        renderer.setFilter(filterGroup);
    }
}
