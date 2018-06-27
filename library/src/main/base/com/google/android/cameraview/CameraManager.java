/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.View;

import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.model.AspectRatio;
import com.google.android.cameraview.model.Size;

import java.util.Set;

/**
 * 不同方式实现CameraView的抽象基类
 */
abstract class CameraManager implements ManagerInterface{

    private final static String TAG = "CameraManager";

    public static final int FOCUS_AREA_SIZE_DEFAULT = 300;
    public static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 1000;
    public static final int DELAY_MILLIS_BEFORE_RESETTING_FOCUS = 3000;

    protected final Callback mCallback;
    protected final CameraPreview mPreview;

    protected MediaRecorder mMediaRecorder;
    protected CamcorderProfile mCamcorderProfile;
    protected Size mVideoSize;

    HandlerThread mBackgroundThread;
    Handler mBackgroundHandler;
    Handler mUiHandler = new Handler(Looper.getMainLooper());

    boolean mIsVideoRecording = false;

    String videoOutPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + System.currentTimeMillis() + ".mp4";
//context.getCacheDir().getAbsolutePath()

    CameraManager(Callback callback, CameraPreview preview) {
        mCallback = callback;
        mPreview = preview;
        startBackgroundThread();
    }

    @Override
    public View getView() {
        return mPreview.getView();
    }


    @Override
    public int getFocusAreaSize() {
        return FOCUS_AREA_SIZE_DEFAULT;
    }

    @Override
    public int getFocusMeteringAreaWeight() {
        return FOCUS_METERING_AREA_WEIGHT_DEFAULT;
    }

    @Override
    public void detachFocusTapListener() {
        if (mPreview != null && mPreview.getView() != null) {
            mPreview.getView().setOnTouchListener(null);
        }
    }

    @Override
    public void releaseVideoRecorder() {
        try {
            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
            }
        } catch (Exception ignore) {

        } finally {
            mMediaRecorder = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (Build.VERSION.SDK_INT > 17) {
            mBackgroundThread.quitSafely();
        } else mBackgroundThread.quit();

        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: ", e);
        } finally {
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    public void releaseCameraManager() {
//        this.mContext = null;
        stopBackgroundThread();
    }



    /**
     * 摄像头相关回调 (camera session callbacks)
     */
    interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onPictureTaken(byte[] data);
    }

}

