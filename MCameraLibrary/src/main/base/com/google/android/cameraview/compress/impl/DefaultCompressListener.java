package com.google.android.cameraview.compress.impl;

import android.util.Log;

import com.google.android.cameraview.compress.inter.CompressListener;

/**
 * @fileName DefaultCompressListener
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public class DefaultCompressListener implements CompressListener {
    private final String TAG = "DefaultCompressListener";

    @Override
    public void onStartCompress() {
        Log.e(TAG, "onStartCompress");
    }

    @Override
    public void onCompressFail() {
        Log.e(TAG, "onCompressFail");
    }

    @Override
    public void onCompressSuccess(int action, String outPath, String compressPath) {
        Log.e(TAG, "onCompressSuccess:localPath" + outPath +"  compressPath:"+compressPath);
    }
}
