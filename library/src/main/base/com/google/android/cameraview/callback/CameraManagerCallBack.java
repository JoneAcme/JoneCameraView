package com.google.android.cameraview.callback;

/**
 * @fileName CameraManagerCallBack
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface CameraManagerCallBack {
    void onCameraOpened();

    void onCameraClosed();

//    void onPictureTaken(byte[] data);

    void onStartVideoRecorder();

//    void onCompleteVideoRecorder(String outPath);
    void onCompleteVideoRecorder();
}
