package com.google.android.cameraview.compress.inter;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * @fileName PictureCompress
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface PictureCompress {

    String compress(Context mContext, Bitmap bitmap, String compressPath, int quality);
}
