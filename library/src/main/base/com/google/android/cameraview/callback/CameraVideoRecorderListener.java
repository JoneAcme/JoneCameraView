package com.google.android.cameraview.callback;

/**
 * @fileName CameraVideoRecorderListener
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface CameraVideoRecorderListener {
    void onStartVideoRecorder();

//    void onCompleteVideoRecorder(String outPath);
    void onCompleteVideoRecorder();
}
