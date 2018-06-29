package com.google.android.cameraview.compress.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.cameraview.compress.CompressUtils;
import com.google.android.cameraview.compress.inter.CompressListener;
import com.google.android.cameraview.compress.inter.PictureCompress;

/**
 * @fileName DefaultPictureCompress
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public class DefaultPictureCompress implements PictureCompress {
    private final String TAG = "DefaultPictureCompress";


    @Override
    public String compress(Context mContext, Bitmap bitmap, String compressPath, int quality) {

        boolean isSuccess = CompressUtils.saveBitmap(bitmap, compressPath, quality);

        return isSuccess ? compressPath : null;
    }
}
