package com.google.android.cameraview.compress.inter;

import com.google.android.cameraview.configs.CameraConfig;

/**
 * @fileName CompressListener
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface CompressListener {
    void onStartCompress();

    void onCompressFail();

    void onCompressSuccess(@CameraConfig.MediaAction int action,String localPath,String compressPath);
}
