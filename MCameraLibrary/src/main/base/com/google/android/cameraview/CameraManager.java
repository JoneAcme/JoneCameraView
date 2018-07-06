package com.google.android.cameraview;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.View;

import com.google.android.cameraview.callback.CameraManagerCallBack;
import com.google.android.cameraview.compress.CompressUtils;
import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.helper.CameraUtils;
import com.google.android.cameraview.model.Size;


/**
 * 不同方式实现CameraView的抽象基类
 */
abstract class CameraManager implements ManagerInterface {

    private final static String TAG = "CameraManager";

    public static final int FOCUS_AREA_SIZE_DEFAULT = 300;
    public static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 1000;
    public static final int DELAY_MILLIS_BEFORE_RESETTING_FOCUS = 3000;

    protected final CameraManagerCallBack mCallback;
    protected final CameraPreview mPreview;

    protected MediaRecorder mMediaRecorder;
    protected CamcorderProfile mCamcorderProfile;
    protected Size mVideoSize;

    protected CameraViewOptions mCameraOption;
    protected Context mContext;
    HandlerThread mBackgroundThread;
    Handler mBackgroundHandler;
    Handler mUiHandler = new Handler(Looper.getMainLooper());

    String videoPath;
    boolean mIsVideoRecording = false;


    CameraManager(CameraManagerCallBack callback, CameraPreview preview, Context context,CameraViewOptions options) {
        mContext = context;
        mCallback = callback;
        mPreview = preview;
        this.mCameraOption = options;
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


    @Override
    public void setCameraOption(CameraViewOptions mCameraOption) {
        this.mCameraOption = mCameraOption;
    }

    @Override
    public void releaseCameraManager() {
        //        this.mContext = null;
        stopBackgroundThread();
    }


    @Override
    public void compressVideo(String localPath,CameraViewOptions mCameraOption) {
        CompressUtils.ansyVideoCompress(mContext,localPath, mCameraOption);
    }

    @Override
    public void compressImage(byte[] data, CameraViewOptions mCameraOption) {
        Bitmap bitmap = CameraUtils.rotationBitmap(data);
        CompressUtils.ansyPictrueCompress(mContext, bitmap, mCameraOption);
    }


}

