package com.google.android.cameraview.compress.impl;

import android.content.Context;

import com.google.android.cameraview.compress.inter.VideoCompress;

/**
 * @fileName DefaultVideoCompress
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public class DefaultVideoCompress implements VideoCompress {
    @Override
    public String compress(Context mContext, String path, String compressPath) {
        return path;
    }
}
