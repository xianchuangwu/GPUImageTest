package com.chad.gpuimagetest.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.chad.gpuimagetest.MySurfaceView;

public class CustomMediaPlayerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new MySurfaceView(this));
    }
}
