package com.chad.gpuimagetest;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.baidu.cloud.media.player.BDCloudMediaPlayer;
import com.baidu.cloud.media.player.IMediaPlayer;
import com.chad.gpuimagetest.egl.EglCore;
import com.chad.gpuimagetest.egl.GlUtil;
import com.chad.gpuimagetest.egl.WindowSurface;
import com.chad.gpuimagetest.ui.MulitFilterPreviewActivity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import jp.co.cyberagent.android.gpuimage.GPUImageBrightnessFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageExtRotationTexFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.GPUImageLookupFilter;

/**
 * Created by chad
 * Time 17/9/12
 * Email: wuxianchuang@foxmail.com
 * Description: TODO
 */

public class SurfaceTextureRender extends Thread implements SurfaceTexture.OnFrameAvailableListener, IMediaPlayer.OnPreparedListener {

    public static final String TAG = "SurfaceTextureRender";

    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     * <p>
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    public static class RenderHandler extends Handler {
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_REDRAW = 5;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<SurfaceTextureRender> mWeakRenderThread;

        /**
         * Call from render thread.
         */
        public RenderHandler(SurfaceTextureRender rt) {
//            super();
            mWeakRenderThread = new WeakReference<SurfaceTextureRender>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceTexture holder, int width, int height) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                    width, height, holder));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(SurfaceTexture holder, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height, holder));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed(SurfaceTexture holder) {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED, 0, 0, holder));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.i(TAG, "RenderHandler [" + this + "]: what=" + what);

            SurfaceTextureRender renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    renderThread.surfaceAvailable((SurfaceTexture) msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged((SurfaceTexture) msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    renderThread.surfaceDestroyed((SurfaceTexture) msg.obj);
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.frameAvailable();
                    break;

                case MSG_REDRAW:
                    renderThread.draw();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    // Object must be created on render thread to get correct Looper, but is used from
    // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
    // constructed object.
    private volatile RenderHandler mHandler;

    // Used to wait for the thread to start.
    private Object mStartLock = new Object();
    private boolean mReady = false;

    //    private MediaPlayer mediaPlayer;
    private BDCloudMediaPlayer bdCloudMediaPlayer;
    private EglCore mEglCore;

    // Receives the output from the camera preview.
    private SurfaceTexture mCameraTexture;

    MulitFilterPreviewActivity activity;

    HashMap<SurfaceTexture, WindowSurface> windowSurfacesMap = new HashMap<>();
    HashMap<Integer, GPUImageFilterGroup> gpuImageFilters = new HashMap<>();

    int mTextureId = -1;

    final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;

    private int[] filterResId;

    private int surfaceWidth;
    private int surfaceHeight;

    private Surface mSurface;

    /**
     * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
     * Activity.
     */
    public SurfaceTextureRender(MulitFilterPreviewActivity activity, int[] resId) {
        this.activity = activity;
        this.filterResId = resId;
    }

    @Override
    public void run() {
        Looper.prepare();

        // We need to create the Handler before reporting ready.
        mHandler = new RenderHandler(this);
        synchronized (mStartLock) {
            mReady = true;
            mStartLock.notify();    // signal waitUntilReady()
        }

        // Prepare EGL and open the camera before we start handling messages.
        mEglCore = new EglCore(null, 0);
//        mediaPlayer = MediaPlayer.create(activity, R.raw.five);
//        mediaPlayer.setLooping(true);
//        mediaPlayer.start();

        BDCloudMediaPlayer.setAK("3ad8d97bb16243cf924b00d23e2b9659");
        bdCloudMediaPlayer = new BDCloudMediaPlayer(activity.getApplicationContext());
        bdCloudMediaPlayer.setOption(1, "protocol_whitelist", "file,http,https,rtp,tcp,tls,udp,data");
        try {
            bdCloudMediaPlayer.setDataSource("/storage/emulated/0/five.mp4");
            bdCloudMediaPlayer.setLooping(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        bdCloudMediaPlayer.prepareAsync();
        bdCloudMediaPlayer.setOnPreparedListener(this);

        Looper.loop();

        Log.i(TAG, "looper quit");
//        mediaPlayer.release();
//            releaseGl(null);

        if (gpuImageFilters.size() > 0) {
            for (GPUImageFilterGroup gpuImageFilterGroup : gpuImageFilters.values()) {
                gpuImageFilterGroup.destroy();
            }
        }

        mEglCore.release();

        synchronized (mStartLock) {
            mReady = false;
        }
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        bdCloudMediaPlayer.start();
    }

    /**
     * Waits until the render thread is ready to receive messages.
     * <p>
     * Call from the UI thread.
     */
    public void waitUntilReady() {
        synchronized (mStartLock) {
            while (!mReady) {
                try {
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    /**
     * Shuts everything down.
     */
    private void shutdown() {
        Log.i(TAG, "shutdown");
        Looper.myLooper().quit();
    }

    /**
     * Returns the render thread's Handler.  This may be called from any thread.
     */
    public RenderHandler getHandler() {
        return mHandler;
    }

    /**
     * Draws the scene and submits the buffer.
     */
    private void draw() {

        for (Map.Entry<SurfaceTexture, WindowSurface> entry : windowSurfacesMap.entrySet()) {

            SurfaceTexture holder = entry.getKey();

            int position = -1;
            for (Map.Entry<TextureView, Integer> entryTV : activity.getTextureviewMap().entrySet()) {
                if (holder.equals(entryTV.getKey().getSurfaceTexture())) {
                    position = entryTV.getValue();
                    break;
                }
            }
            if (position >= 0) {
                GPUImageFilterGroup filter = gpuImageFilters.get(position);
                if (filter != null) {
                    GlUtil.checkGlError("draw start >" + holder.hashCode());
                    WindowSurface windowSurface = entry.getValue();
                    windowSurface.makeCurrent();
                    filter.onDraw(mTextureId, mGLCubeBuffer, mGLTextureBuffer);
                    windowSurface.swapBuffers();
                    GlUtil.checkGlError("draw done >" + holder.hashCode());
                }

            }

        }
    }

    /**
     * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
     */
    private void surfaceAvailable(SurfaceTexture holder, int width, int height) {

        surfaceWidth = width;
        surfaceHeight = height;

        Log.i(TAG, "RenderThread surfaceCreated holder=" + holder.hashCode());
        Surface surface = new Surface(holder);
        WindowSurface mWindowSurface1 = new WindowSurface(mEglCore, surface, false);
        synchronized (windowSurfacesMap) {
            windowSurfacesMap.put(holder, mWindowSurface1);
            mWindowSurface1.makeCurrent();
        }
//        GLES20.glViewport(0, 0, width, height);

        if (windowSurfacesMap.size() <= 1) {
            // only create once

            mTextureId = getPreviewTexture();
            Log.i(TAG, "mTextureId=" + mTextureId);
            mCameraTexture = new SurfaceTexture(mTextureId);

            mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            mGLCubeBuffer.put(CUBE).position(0);

            mGLTextureBuffer = ByteBuffer.allocateDirect(GPUImageExtRotationTexFilter.FULL_RECTANGLE_TEX_COORDS.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            mGLTextureBuffer.put(GPUImageExtRotationTexFilter.FULL_RECTANGLE_TEX_COORDS).position(0);

//                mGLTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
//                        .order(ByteOrder.nativeOrder())
//                        .asFloatBuffer();
//                mGLTextureBuffer.put(TEXTURE_NO_ROTATION).position(0);

            Log.i(TAG, "surfaceChanged should only once here");
            // create all filter
            if (gpuImageFilters.size() > 0) {
                gpuImageFilters.clear();
            }
            Resources resources = activity.getResources();
            for (int i = 0; i < filterResId.length; ++i) {
                GPUImageFilterGroup gpuImageFilter = new GPUImageFilterGroup();

//                gpuImageFilter.addFilter(new GPUImageExtTexFilter());

                GPUImageExtRotationTexFilter ext = new GPUImageExtRotationTexFilter();
                ext.setTexMatrix(mSTMatrix);
                gpuImageFilter.addFilter(ext);

                if (filterResId[i] == -1) {
                    gpuImageFilter.addFilter(new GPUImageFilter());
                } else {
                    GPUImageLookupFilter lookupFilter = new GPUImageLookupFilter();
//                    lookupFilter.setBitmap(BitmapFactory.decodeResource(resources, filterResId[i]));
                    lookupFilter.setBitmap(BitmapFactory.decodeResource(resources, R.raw.filter_black_white2));
                    gpuImageFilter.addFilter(lookupFilter);

//                    gpuImageFilter.addFilter(new GPUImageBrightnessFilter());
                }
                gpuImageFilter.init();
                GLES20.glUseProgram(gpuImageFilter.getProgram());
                gpuImageFilter.onOutputSizeChanged(width, height);
                gpuImageFilters.put(i, gpuImageFilter);
            }

            mCameraTexture.setOnFrameAvailableListener(this);
            //Sets up anything that depends on the window size.
            mSurface = new Surface(mCameraTexture);
            bdCloudMediaPlayer.setSurface(mSurface);
        }

    }

    private void setSinglePlayerFilter() {
        final GPUImageFilter oldFilter = gpuImageFilters.get(0);
        if (oldFilter != null) {
            oldFilter.destroy();
        }

        GPUImageFilterGroup gpuImageFilter = new GPUImageFilterGroup();
        gpuImageFilter.addFilter(new GPUImageExtTexFilter());
        if (filterResId[0] == -1) {
            GPUImageFilter imageFilter = new GPUImageFilter();
            gpuImageFilter.addFilter(imageFilter);
        } else {
            GPUImageLookupFilter lookupFilter = new GPUImageLookupFilter();
            lookupFilter.setBitmap(BitmapFactory.decodeResource(activity.getResources(), filterResId[0]));
            gpuImageFilter.addFilter(lookupFilter);
        }
        gpuImageFilter.init();
        gpuImageFilter.onOutputSizeChanged(surfaceWidth, surfaceHeight);
        gpuImageFilters.put(0, gpuImageFilter);
    }

    /**
     * Handles the surfaceChanged message.
     * <p>
     * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
     * could also be called with a Surface created on a previous run.  So this may not
     * be called.
     */
    private void surfaceChanged(SurfaceTexture surfaceHolder, int width, int height) {
        Log.i(TAG, "RenderThread surfaceChanged " + width + "x" + height + ";surfaceHolder=" + surfaceHolder.hashCode());
    }

    /**
     * Handles the surfaceDestroyed message.
     */
    private void surfaceDestroyed(SurfaceTexture surfaceHolder) {
        // In practice this never appears to be called -- the activity is always paused
        // before the surface is destroyed.  In theory it could be called though.
        releaseGl(surfaceHolder);
    }

    // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mHandler.sendFrameAvailable();
    }

    public int getPreviewTexture() {
        int textureId = -1;
        if (textureId == GlUtil.NO_TEXTURE) {
            textureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        }
        return textureId;
    }

    /**
     * Releases most of the GL resources we currently hold (anything allocated by
     * surfaceAvailable()).
     * <p>
     * Does not release EglCore.
     */
    private void releaseGl(SurfaceTexture surfaceHolder) {
        GlUtil.checkGlError("releaseGl start");

        WindowSurface windowSurface = windowSurfacesMap.get(surfaceHolder);
        if (windowSurface != null) {

            windowSurfacesMap.remove(surfaceHolder);
            windowSurface.release();
        }
        GlUtil.checkGlError("releaseGl done");

    }

    float[] mSTMatrix = new float[16];

    /**
     * Handles incoming frame of data from the camera.
     */
    private void frameAvailable() {
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mSTMatrix);
        draw();
    }

}
