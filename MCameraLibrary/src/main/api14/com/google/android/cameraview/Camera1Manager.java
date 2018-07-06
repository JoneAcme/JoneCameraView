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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.SparseArrayCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import com.google.android.cameraview.callback.CameraManagerCallBack;
import com.google.android.cameraview.compress.CompressUtils;
import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.helper.CameraUtils;
import com.google.android.cameraview.helper.Exif;
import com.google.android.cameraview.helper.FileUtils;
import com.google.android.cameraview.logs.CameraLog;
import com.google.android.cameraview.model.AspectRatio;
import com.google.android.cameraview.model.Size;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Camera1类是使用Camera 1 API实现的CameraView，在Android 4.0系统上使用的是这个CameraView的实现
 */
@SuppressWarnings("deprecation")
class Camera1Manager extends CameraManager {

    private static final String TAG = Camera1Manager.class.getSimpleName();

    private static final long MIN_TIME_FOR_AUTOFOCUS = 2000;//拍照时最短的自动对焦时间限制

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(CameraConfig.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(CameraConfig.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(CameraConfig.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(CameraConfig.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(CameraConfig.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int mCameraId;
    private Camera mCamera;
    private Camera.Parameters mCameraParameters;
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private int mFacing;
    private int mFlash;
    private boolean mAutoFocus;
    private int mDisplayOrientation;

    private AspectRatio mAspectRatio;
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();

    private boolean mShowingPreview;
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    private final AtomicBoolean isAutoFocusInProgress = new AtomicBoolean(false);

    private Handler mHandler = new Handler();
    private Camera.AutoFocusCallback mAutofocusCallback;//这个貌似并没有起到作用，后期考虑删除

    public Camera1Manager(CameraManagerCallBack callback, CameraPreview preview, Context context,CameraViewOptions options) {
        super(callback, preview, context,options);
        if (mPreview != null) {
            mPreview.setCallback(new CameraPreview.Callback() {
                @Override
                public void onSurfaceChanged() {
                    if (mCamera != null) {
                        setUpPreview();
                        adjustCameraParameters();
                    }
                }
            });
        }
    }

    @Override
    public boolean startCamera() {
        if (!chooseCamera()) {
            return false;
        }

        openCamera();

        if (mPreview.isReady()) {
            setUpPreview();
        }
        mShowingPreview = true;

        mCamera.startPreview();
        return true;
    }

    @Override
    public void stopCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        mShowingPreview = false;
        isPictureCaptureInProgress.set(false);
        isAutoFocusInProgress.set(false);
        releaseCamera();
    }

    // Suppresses Camera#setPreviewTexture
    @SuppressLint("NewApi")
    private void setUpPreview() {
        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                final boolean needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14;//辅导中needsToStopPreview一定是false
                CameraLog.i(TAG, "setUpPreview, outputClass is SurfaceHolder, needsToStopPreview = %s", needsToStopPreview);
                if (needsToStopPreview) {
                    mCamera.stopPreview();
                }
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
                if (needsToStopPreview) {
                    mCamera.startPreview();
                }
            } else {//Android 4.0(API 14)以上才有了TextureView，辅导一定是走这里
                CameraLog.i(TAG, "setUpPreview, outputClass is SurfaceTexture");
                mCamera.setPreviewTexture((SurfaceTexture) mPreview.getSurfaceTexture());//两个分支最主要的区别在于这里，给Camera设置Preview对应的Surface
            }
        } catch (IOException e) {
            CameraLog.i(TAG, "setUpPreview, fail IOException message: ", e.getMessage());
            //throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    public void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stopCamera();
            startCamera();
        }
    }

    @Override
    public int getFacing() {
        return mFacing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        //mPreviewSizes和mPictureSizes都有各自支持的比例，这里是求出mPreviewSizes中那些在mPictureSizes中也存在的比例列表 => javayhu 后来我在adjustCameraParameters中也做了这个操作
        SizeMap idealAspectRatios = mPreviewSizes;
        List<AspectRatio> ratiosToDelete = new ArrayList<>();
        for (AspectRatio aspectRatio : idealAspectRatios.ratios()) {
            if (mPictureSizes.sizes(aspectRatio) == null) {
                //idealAspectRatios.remove(aspectRatio);
                ratiosToDelete.add(aspectRatio);
            }
        }
        for (AspectRatio ratio : ratiosToDelete) {
            idealAspectRatios.remove(ratio);
        }
        return idealAspectRatios.ratios();
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (mAspectRatio == null || !isCameraOpened()) {
            CameraLog.i(TAG, "setAspectRatio, mAspectRatio is null? %s, camera open? %s", mAspectRatio == null, isCameraOpened());
            mAspectRatio = ratio;// Handle this later when camera is opened
            return true;
        } else if (!mAspectRatio.equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                //throw new UnsupportedOperationException(ratio + " is not supported");
                CameraLog.i(TAG, "setAspectRatio, ratio [%s] is not supported", ratio.toString());
                return false;
            } else {
                mAspectRatio = ratio;
                adjustCameraParameters();
                return true;
            }
        }
        return false;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        if (setAutoFocusInternal(autoFocus)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    public boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public void setFlash(int flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {
            //throw new IllegalStateException("Camera is not ready. Call startCamera() before takePicture().");
            CameraLog.i(TAG, "Camera is not ready, call startCamera() before takePicture()");
            return;
        }

        if (getAutoFocus()) {
            CameraLog.i(TAG, "takePicture => autofocus");
            mCamera.cancelAutoFocus();
            //mCamera.autoFocus进行自动对焦，对焦好了之后再拍照，魅族MX6手机上对焦比较慢，导致这里可能需要等待好几秒才拍照成功
            //这里为了更好的体验，限制3秒之内一定要进行拍照，也就是说3秒钟之内对焦还没有成功的话那就直接进行拍照
            isAutoFocusInProgress.getAndSet(true);
            try {//从数据上报来看，部分相机自动对焦失败会发生crash，所以这里需要catch住，如果自动对焦失败了，那么就直接进行拍照
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (isAutoFocusInProgress.get()) {
                            CameraLog.i(TAG, "takePicture, auto focus => takePictureInternal");
                            isAutoFocusInProgress.set(false);
                            takePictureInternal();
                        }
                    }
                });
            } catch (Exception error) {
                if (isAutoFocusInProgress.get()) {
                    CameraLog.i(TAG, "takePicture, autofocus exception => takePictureInternal", error);
                    isAutoFocusInProgress.set(false);
                    takePictureInternal();
                }
            }

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isAutoFocusInProgress.get()) {
                        CameraLog.i(TAG, "takePicture, cancel focus => takePictureInternal");
                        isAutoFocusInProgress.set(false);
                        takePictureInternal();
                    }
                }
            }, MIN_TIME_FOR_AUTOFOCUS);
        } else {
            CameraLog.i(TAG, "takePicture => takePictureInternal");
            takePictureInternal();
        }
    }

    //上面的mCamera.autoFocus中的onAutoFocus这个回调会被调用两次，所以takePictureInternal方法中使用isPictureCaptureInProgress来控制takePicture的调用
    private void takePictureInternal() {
        if (isCameraOpened() && !isPictureCaptureInProgress.getAndSet(true)) {
            mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    CameraLog.i(TAG, "takePictureInternal, onPictureTaken");
                    isPictureCaptureInProgress.set(false);
                    compressImage(data, mCameraOption);
                    camera.cancelAutoFocus();
                    camera.startPreview();
                }
            });
        }
    }


    @Override
    public void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            CameraLog.i(TAG, "Camera1Manager setDisplayOrientation, displayOrientation = %d, not changed", displayOrientation);
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            int rotation = CameraUtils.calcCameraRotation(mCameraInfo, displayOrientation);
            mCameraParameters.setRotation(rotation);
            mCamera.setParameters(mCameraParameters);
            final boolean needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14;
            if (needsToStopPreview) {
                mCamera.stopPreview();
            }
            int orientation = CameraUtils.calcDisplayOrientation(mCameraInfo, displayOrientation);
            mCamera.setDisplayOrientation(orientation);
            if (needsToStopPreview) {
                mCamera.startPreview();
            }
            CameraLog.i(TAG, "Camera1Manager setDisplayOrientation, new orientation = %d, camera rotation = %d, camera orientation = %d", displayOrientation, rotation, orientation);
        }
    }


    private boolean chooseCamera() {
        int count = Camera.getNumberOfCameras();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mFacing) {
                mCameraId = i;
                CameraLog.i(TAG, "chooseCamera, CameraId = %d", mCameraId);
                return true;
            }
        }
        CameraLog.e(TAG, "chooseCamera, no camera available");
        return false;
    }

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        mCamera = Camera.open(mCameraId);
        mCameraParameters = mCamera.getParameters();

        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        CameraLog.i(TAG, "openCamera, supportedPreviewSizes: " + mPreviewSizes);

        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        CameraLog.i(TAG, "openCamera, supportedPictureSizes: " + mPictureSizes);

        //调整Preview sizes
        adjustPreviewSizes();
        CameraLog.i(TAG, "openCamera, adjustPreviewSizes: %s", mPreviewSizes);//应该不至于有个手机没有一个可以统一的AspectRatio

        // AspectRatio
        if (mAspectRatio == null) {
            mAspectRatio = CameraConfig.DEFAULT_ASPECT_RATIO;
        }

        adjustCameraParameters();
        mCamera.setDisplayOrientation(CameraUtils.calcDisplayOrientation(mCameraInfo, mDisplayOrientation));
        Log.e(TAG, "setDisplayOrientation:" + CameraUtils.calcDisplayOrientation(mCameraInfo, mDisplayOrientation));

        mCallback.onCameraOpened();
    }

    //删除在图片预览大小中那些没有必要的大小，因为这些大小在输出图片中不可能有这个比例(宽高比) => 为了保证预览图片、输出图片和AspectRatio三个的比例值是一样的才行！
    private void adjustPreviewSizes() {
        List<AspectRatio> ratiosToDelete = new ArrayList<>();
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            if (!mPictureSizes.ratios().contains(ratio)) {
                //mPreviewSizes.remove(ratio);
                ratiosToDelete.add(ratio);
            }
        }
        for (AspectRatio ratio : ratiosToDelete) {
            mPreviewSizes.remove(ratio);
        }
    }

    private void adjustCameraParameters() {
        SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
        if (sizes == null) { // Not supported
            CameraLog.i(TAG, "adjustCameraParameters, ratio[%s] is not supported", mAspectRatio);
            mAspectRatio = chooseAspectRatio();
            sizes = mPreviewSizes.sizes(mAspectRatio);
            CameraLog.i(TAG, "adjustCameraParameters, change to ratio to %s", mAspectRatio);
        }
        Size previewSize = choosePreviewSize(sizes);

        mVideoSize = previewSize;
        // Always re-apply camera parameters
        //final Size pictureSize = mPictureSizes.sizes(mAspectRatio).last();// Largest picture size in this ratio
        Size pictureSize = choosePictureSize();

        if (mShowingPreview) {
            mCamera.stopPreview();//在重新设置CameraParameters之前需要停止预览
        }

        mCameraParameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        mCameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        mCameraParameters.setRotation(CameraUtils.calcCameraRotation(mCameraInfo, mDisplayOrientation));
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);
        mCamera.setParameters(mCameraParameters);

        CameraLog.i(TAG, "adjustCameraParameters, PreviewSize = %s, PictureSize = %s, AspectRatio = %s, AutoFocus = %s, Flash = %s", previewSize, pictureSize, mAspectRatio, mAutoFocus, mFlash);

        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size choosePreviewSize(SortedSet<Size> sizes) {
        if (!mPreview.isReady()) { // Not yet laid out
            CameraLog.i(TAG, "choosePreviewSize, preview is not ready, return size: %s", sizes.first());
            return sizes.first(); // Return the smallest size
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (CameraUtils.isLandscape(mDisplayOrientation)) {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        } else {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }
        Size result = null;
        for (Size size : sizes) { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;
            }
            result = size;
        }
        return result;
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio aspectRatio = null;
        if (mPreviewSizes.ratios().contains(CameraConfig.DEFAULT_ASPECT_RATIO)) {//首先看16:9是否支持
            aspectRatio = CameraConfig.DEFAULT_ASPECT_RATIO;
        } else if (mPreviewSizes.ratios().contains(CameraConfig.SECOND_ASPECT_RATIO)) {//再看4:3是否支持
            aspectRatio = CameraConfig.SECOND_ASPECT_RATIO;
        } else {//两个都不支持的话就取它支持的第一个作为当前的宽高比
            aspectRatio = mPreviewSizes.ratios().iterator().next();
        }
        CameraLog.i(TAG, "chooseAspectRatio, aspect ratio changed to " + aspectRatio.toString());
        return aspectRatio;
    }

    /**
     * 这里针对具体需求调整最合适的宽高比和输出图片大小
     * 对于不同的手机而言，大部分都支持16:9(4:3)的比例，同时大部分也都支持输出1920x1080(800x600)的图片大小，图片文件大小大概在500KB(200KB)左右
     */
    private Size choosePictureSize() {
        if (mAspectRatio.equals(CameraConfig.DEFAULT_ASPECT_RATIO)) {
            SortedSet<Size> sizes = mPictureSizes.sizes(mAspectRatio);
            Size[] preferedSizes = new Size[]{new Size(1920, 1080), new Size(1280, 720)};//几个比较合适的输出大小
            for (Size size : preferedSizes) {
                if (sizes.contains(size)) {
                    return size;
                }
            }
            //前面几个合适的大小都没有的话，那么就使用中间那个大小
            return getMiddleSize(sizes);
        } else if (mAspectRatio.equals(CameraConfig.SECOND_ASPECT_RATIO)) {
            SortedSet<Size> sizes = mPictureSizes.sizes(mAspectRatio);
            Size[] preferedSizes = new Size[]{new Size(1440, 1080), new Size(1280, 960), new Size(1024, 768), new Size(800, 600)};//几个比较合适的输出大小
            for (Size size : preferedSizes) {
                if (sizes.contains(size)) {
                    return size;
                }
            }
            //前面几个合适的大小都没有的话，那么就使用中间那个大小
            return getMiddleSize(sizes);
        } else {
            SortedSet<Size> sizes = mPictureSizes.sizes(mAspectRatio);
            return getMiddleSize(sizes);
        }
    }

    //前面几个合适的大小都没有的话，那么就使用中间那个大小 (即使是中间这个大小也并不能保证它满足我们的需求，比如得到的图片还是很大，但是这种情况实在太少了)
    private Size getMiddleSize(SortedSet<Size> sizes) {
        int length = sizes.size() / 2, i = 0;
        for (Size item : sizes) {
            if (i == length) {
                return item;
            }
            i++;
        }
        return sizes.last();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;

            mCallback.onCameraClosed();
        }
    }


    /**
     * 设置自动对焦
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        mAutoFocus = autoFocus;
        if (isCameraOpened()) {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                attachFocusTapListener();
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                CameraLog.i(TAG, "setAutoFocusInternal, FOCUS_MODE_CONTINUOUS_PICTURE, autoFocus = true");
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                detachFocusTapListener();
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                CameraLog.i(TAG, "setAutoFocusInternal, FOCUS_MODE_FIXED, autoFocus = %s", autoFocus);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                detachFocusTapListener();
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                CameraLog.i(TAG, "setAutoFocusInternal, FOCUS_MODE_INFINITY, autoFocus = %s", autoFocus);
            } else {
                detachFocusTapListener();
                mCameraParameters.setFocusMode(modes.get(0));//getSupportedFocusModes方法返回的列表至少有一个元素
                CameraLog.i(TAG, "setAutoFocusInternal, mode = %s, autoFocus = %s", modes.get(0), autoFocus);
            }
            return true;
        } else {
            CameraLog.i(TAG, "setAutoFocusInternal, camera not open, autoFocus = %s", autoFocus);
            return false;
        }
    }

    /**
     * 设置闪光灯
     */
    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            List<String> modes = mCameraParameters.getSupportedFlashModes();//如果不支持设置闪关灯的话，getSupportedFlashModes方法会返回null
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                CameraLog.i(TAG, "setFlashInternal, flash = %d", flash);
                return true;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = CameraConfig.FLASH_OFF;
                CameraLog.i(TAG, "setFlashInternal, flash is FLASH_OFF");
                return true;
            }
            return false;
        } else {
            mFlash = flash;
            CameraLog.i(TAG, "setFlashInternal, camera not open, flash = %d", flash);
            return false;
        }
    }

    //手动对焦参考方案：https://github.com/lin18/cameraview/commit/47b8a4e493cdb5f1085333577d55b749443047e9
    @TargetApi(14)
    private void attachFocusTapListener() {
        mPreview.getView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (mCamera != null) {
                        Camera.Parameters parameters = mCamera.getParameters();
                        String focusMode = parameters.getFocusMode();
                        Rect rect = calculateFocusArea(event.getX(), event.getY());
                        List<Camera.Area> meteringAreas = new ArrayList<>();
                        meteringAreas.add(new Camera.Area(rect, getFocusMeteringAreaWeight()));

                        if (parameters.getMaxNumFocusAreas() != 0 && focusMode != null &&
                                (focusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ||
                                        focusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ||
                                        focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ||
                                        focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                                ) {
                            if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                                return false; //cannot autoFocus
                            }
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                            parameters.setFocusAreas(meteringAreas);
                            if (parameters.getMaxNumMeteringAreas() > 0) {
                                parameters.setMeteringAreas(meteringAreas);
                            }
                            mCamera.setParameters(parameters);

                            try {
                                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                    @Override
                                    public void onAutoFocus(boolean success, Camera camera) {
                                        resetFocus(success, camera);
                                    }
                                });
                            } catch (Exception error) {
                                //ignore this exception
                                CameraLog.e(TAG, "attachFocusTapListener, autofocus fail case 1", error);
                            }
                        } else if (parameters.getMaxNumMeteringAreas() > 0) {
                            if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                                return false; //cannot autoFocus
                            }
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                            parameters.setFocusAreas(meteringAreas);
                            parameters.setMeteringAreas(meteringAreas);
                            mCamera.setParameters(parameters);

                            try {
                                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                    @Override
                                    public void onAutoFocus(boolean success, Camera camera) {
                                        resetFocus(success, camera);
                                    }
                                });
                            } catch (Exception error) {
                                //ignore this exception
                                CameraLog.e(TAG, "attachFocusTapListener, autofocus fail case 2", error);
                            }
                        } else {
                            try {
                                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                    @Override
                                    public void onAutoFocus(boolean success, Camera camera) {
                                        if (mAutofocusCallback != null) {
                                            mAutofocusCallback.onAutoFocus(success, camera);
                                        }
                                    }
                                });
                            } catch (Exception error) {
                                //ignore this exception
                                CameraLog.e(TAG, "attachFocusTapListener, autofocus fail case 3", error);
                            }
                        }
                    }
                }
                return true;
            }
        });
    }

    @TargetApi(14)
    private void resetFocus(final boolean success, final Camera camera) {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (camera != null) {
                    camera.cancelAutoFocus();
                    try {
                        Camera.Parameters params = camera.getParameters();//数据上报中红米Note3在这里可能crash
                        if (params != null && !params.getFocusMode().equalsIgnoreCase(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                            //之前这里并没有考虑相机是否支持FOCUS_MODE_CONTINUOUS_PICTURE，可能是因为这个原因导致部分三星机型上调用后面的setParameters失败
                            if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                                params.setFocusAreas(null);
                                params.setMeteringAreas(null);
                                camera.setParameters(params);//数据上报中三星低端机型在这里可能crash
                            }
                        }
                    } catch (Exception error) {
                        //ignore this exception
                        CameraLog.e(TAG, "resetFocus, camera getParameters or setParameters fail", error);
                    }

                    if (mAutofocusCallback != null) {
                        mAutofocusCallback.onAutoFocus(success, camera);
                    }
                }
            }
        }, DELAY_MILLIS_BEFORE_RESETTING_FOCUS);
    }

    private Rect calculateFocusArea(float x, float y) {
        int buffer = getFocusAreaSize() / 2;
        int centerX = calculateCenter(x, mPreview.getView().getWidth(), buffer);
        int centerY = calculateCenter(y, mPreview.getView().getHeight(), buffer);
        return new Rect(
                centerX - buffer,
                centerY - buffer,
                centerX + buffer,
                centerY + buffer
        );
    }

    private static int calculateCenter(float coord, int dimen, int buffer) {
        int normalized = (int) ((coord / dimen) * 2000 - 1000);
        if (Math.abs(normalized) + buffer > 1000) {
            if (normalized > 0) {
                return 1000 - buffer;
            } else {
                return -1000 + buffer;
            }
        } else {
            return normalized;
        }
    }

    @Override
    public boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();
        try {
            mMediaRecorder.reset();
            mCamera.lock();
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

            mCamcorderProfile = CameraUtils.getCamcorderProfile(CameraConfig.MEDIA_QUALITY_MEDIUM, mCameraId);

            //MediaRecorder.OutputFormat.MPEG_4    实现开始静音
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//            mMediaRecorder.setOutputFormat(mCamcorderProfile.fileFormat);
            //设置视频帧率，可省略  25
//            mMediaRecorder.setVideoFrameRate(mCamcorderProfile.videoFrameRate);
            mMediaRecorder.setVideoFrameRate(mCameraOption.getVideoFrameRate());
//            mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
            mMediaRecorder.setVideoSize(mCameraOption.getVideoWidth(), mCameraOption.getVideoHeight());
            //提高帧频率，录像模糊，花屏，绿屏可写上调试 10 * 1024 * 1024
//            mMediaRecorder.setVideoEncodingBitRate(mCamcorderProfile.videoBitRate);
            mMediaRecorder.setVideoEncodingBitRate(mCameraOption.getVideoEncodingBitRate());
            //格式  MediaRecorder.VideoEncoder.H264
            mMediaRecorder.setVideoEncoder(mCamcorderProfile.videoCodec);


            //设置所录制的声音的编码位率。
            mMediaRecorder.setAudioEncodingBitRate(mCamcorderProfile.audioBitRate);
            //设置录制的音频通道数。
            mMediaRecorder.setAudioChannels(mCamcorderProfile.audioChannels);
            //设置所录制的声音的采样率。
            mMediaRecorder.setAudioSamplingRate(mCamcorderProfile.audioSampleRate);
            //设置所录制的声音的编码格式。    MediaRecorder.AudioEncoder.AMR_NB
            mMediaRecorder.setAudioEncoder(mCamcorderProfile.audioCodec);

            videoPath = FileUtils.getVideoLocalPath(mContext);
            mMediaRecorder.setOutputFile(videoPath);
            int oritation = CameraUtils.calcDisplayOrientation(mCameraInfo, mDisplayOrientation);
            //设置输出的视频播放的方向提示
            mMediaRecorder.setOrientationHint(mFacing == CameraConfig.FACING_FRONT ? (oritation + 180) % 360 : oritation);
            mMediaRecorder.setPreviewDisplay(mPreview.getSurface());

            mMediaRecorder.prepare();

            return true;
        } catch (IllegalStateException error) {
            Log.e(TAG, "IllegalStateException preparing MediaRecorder: " + error.getMessage());
        } catch (IOException error) {
            Log.e(TAG, "IOException preparing MediaRecorder: " + error.getMessage());
        } catch (Throwable error) {
            Log.e(TAG, "Error during preparing MediaRecorder: " + error.getMessage());
        }
        releaseVideoRecorder();
        return false;
    }

    @Override
    public void startVideoRecorder() {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (prepareVideoRecorder()) {
                    mMediaRecorder.start();
                    Log.e(TAG, "mMediaRecorder startCamera!");
                    mIsVideoRecording = true;
                    mCallback.onStartVideoRecorder();
                }
            }
        });
    }

    @Override
    public void stopVideoRecorder() {
        if (mIsVideoRecording) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mMediaRecorder != null) mMediaRecorder.stop();
                    } catch (Exception ignore) {
                        Log.e(TAG, "mMediaRecorder stopCamera error:" + ignore);
                    }

                    mIsVideoRecording = false;
                    releaseVideoRecorder();

                    mCallback.onCompleteVideoRecorder();
                    Log.d(TAG, "mMediaRecorder stopCamera!");
                    compressVideo(videoPath, mCameraOption);
                }
            });
        }
    }

    @Override
    public void releaseVideoRecorder() {
        super.releaseVideoRecorder();
        try {
            // lock camera for later use
            mCamera.lock();
        } catch (Exception ignore) {
        }
    }


}
