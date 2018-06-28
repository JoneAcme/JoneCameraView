package com.google.android.cameraview.compress.inter;

import android.content.Context;

/**
 * @fileName VideoCompress
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface VideoCompress {
    String compress(Context mContext, String path, String compressPath);
}
