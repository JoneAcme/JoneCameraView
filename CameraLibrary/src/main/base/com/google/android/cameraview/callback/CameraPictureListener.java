package com.google.android.cameraview.callback;

import com.google.android.cameraview.CameraView;

/**
 * @fileName CameraPictureListener
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface CameraPictureListener {
    //data is JPEG data
    void onPictureTaken(CameraView cameraView, byte[] data);
}
