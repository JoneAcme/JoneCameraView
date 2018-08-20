package com.google.android.cameraview.callback;

import com.google.android.cameraview.CameraView;

/**
 * @fileName CameraControlListener
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public interface CameraControlListener {

    void onCameraOpened(CameraView cameraView);

    void onCameraClosed(CameraView cameraView);

//    public void onCameraError();

}