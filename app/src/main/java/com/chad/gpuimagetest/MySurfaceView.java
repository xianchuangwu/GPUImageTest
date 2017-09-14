package com.chad.gpuimagetest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by chad
 * Time 17/9/11
 * Email: wuxianchuang@foxmail.com
 * Description: TODO 仿MediaPLayer,展示surfaceview surfaceholer surface的关系
 */

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder surfaceHolder;

    private boolean stop = false;
    private Paint paint;
    private float width;
    private float height;

    private int count;

    public MySurfaceView(Context context) {
        super(context);
        this.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;

        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(80);
        paint.setStyle(Paint.Style.STROKE);

        width = getMeasuredWidth();
        height = getMeasuredHeight();

        CustomPlayerThread customPlayerThread = new CustomPlayerThread();
        customPlayerThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        width = getMeasuredWidth();
        height = getMeasuredHeight();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stop = true;
    }

    private class CustomPlayerThread extends Thread {

        @Override
        public void run() {
            super.run();
            while (!stop) {

                Canvas canvas = surfaceHolder.lockCanvas();

                //清空画布
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                long currentTimeMillis = System.currentTimeMillis();

//                String text = String.valueOf(currentTimeMillis);
                String text = String.valueOf(count);

                canvas.drawText(text, 0, text.length(), width / 3, height / 2, paint);

                surfaceHolder.unlockCanvasAndPost(canvas);

                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                count++;

            }
        }
    }


}
