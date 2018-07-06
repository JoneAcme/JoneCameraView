package com.google.android.cameraview;

import android.graphics.Bitmap;
import android.view.View;

import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.model.AspectRatio;

import java.util.Set;

/**
 * @fileName ManagerInterface
 * Created by YiangJone on 2018/6/27.
 * @describe
 */


public interface ManagerInterface {

    boolean startCamera();

    void stopCamera();

    boolean isCameraOpened();

    void setFacing(int facing);

    int getFacing();

    Set<AspectRatio> getSupportedAspectRatios();

    boolean setAspectRatio(AspectRatio ratio);

    AspectRatio getAspectRatio();

    void setAutoFocus(boolean autoFocus);

    boolean getAutoFocus();

    void setFlash(int flash);

    int getFlash();

    void takePicture();

    void setDisplayOrientation(int displayOrientation);

    boolean prepareVideoRecorder();

    void startVideoRecorder();

    void stopVideoRecorder();

    void releaseVideoRecorder();

    void detachFocusTapListener();

    int getFocusMeteringAreaWeight();

    int getFocusAreaSize();

    View getView();

    void releaseCameraManager();

    void setCameraOption(CameraViewOptions mCameraOption);

    void compressVideo(String localPath,CameraViewOptions mCameraOption);

    void compressImage(byte[] data, CameraViewOptions mCameraOption);
}
