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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import com.google.android.cameraview.callback.CameraManagerCallBack;
import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.helper.CameraUtils;
import com.google.android.cameraview.helper.FileUtils;
import com.google.android.cameraview.logs.CameraLog;
import com.google.android.cameraview.model.AspectRatio;
import com.google.android.cameraview.model.Size;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Camera2是使用Camera 2 API实现的CameraView，从Android 5.0系统以上使用的是这个CameraView的实现
 */
@SuppressWarnings("MissingPermission")
@TargetApi(21)
class Camera2Manager extends CameraManager {

    private static final String TAG = "Camera2Manager";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(CameraConfig.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(CameraConfig.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static final int MAX_PREVIEW_WIDTH = 1920;//Camera2Manager API 最大的预览宽度
    private static final int MAX_PREVIEW_HEIGHT = 1080;//Camera2Manager API 最大的预览高度

    private String mCameraId;
    private CameraDevice mCamera;
    private final android.hardware.camera2.CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private int mFacing;
    private int mFlash;
    private boolean mAutoFocus;
    private int mDisplayOrientation;

    private AspectRatio mAspectRatio = CameraConfig.DEFAULT_ASPECT_RATIO;
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();

    private ImageReader mImageReader;

    public Camera2Manager(CameraManagerCallBack callback, CameraPreview preview, Context context,CameraViewOptions options) {
        super(callback, preview,context,options);
        mCameraManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (mPreview != null) {
            mPreview.setCallback(new CameraPreview.Callback() {
                @Override
                public void onSurfaceChanged() {
                    CameraLog.i(TAG, "CameraPreview.Callback, onSurfaceChanged => startCaptureSession");
                    startCaptureSession();
                }
            });
        }
    }

    @Override
    public boolean startCamera() {
        if (!chooseCamera()) {
            return false;
        }

        collectCameraInfo();
        prepareImageReader();

        if (!startOpeningCamera()) {
            return false;
        }

        return true;
    }

    @Override
    public void stopCamera() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
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
        //感觉这里可以和Camera1保持一致，返回处理之后的结果，这里没做的原因是在collectCameraInfo方法处理了
        return mPreviewSizes.ratios();
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (ratio == null) {
            CameraLog.i(TAG, "setAspectRatio, ratio is null");
            return false;
        }
        if (ratio.equals(mAspectRatio)) {
            CameraLog.i(TAG, "setAspectRatio, ratio equals to mAspectRatio");
            return false;
        }
        if (!mPreviewSizes.ratios().contains(ratio)) {
            CameraLog.i(TAG, "setAspectRatio, camera not support this ratio? is mPreviewSizes empty? %s", mPreviewSizes.isEmpty());
            return false;
        }

        mAspectRatio = ratio;
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            CameraLog.i(TAG, "setAspectRatio => startCaptureSession");
            startCaptureSession();
        }
        return true;
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
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mAutoFocus = !mAutoFocus; // Revert
                    CameraLog.e(TAG, "setAutoFocus, fail to set autofocus", e);
                }
            }
        }
    }

    @Override
    public boolean getAutoFocus() {
        return mAutoFocus;
    }

    @Override
    public void setFlash(int flash) {
        if (mFlash == flash) {
            return;
        }
        int saved = mFlash;
        mFlash = flash;
        if (mPreviewRequestBuilder != null) {
            updateFlash();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mFlash = saved; // Revert
                    CameraLog.e(TAG, "setFlash, fail to set flash", e);
                }
            }
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {//和Camera1保持一致
            CameraLog.i(TAG, "Camera is not ready, call startCamera() before takePicture()");
            return;
        }

        if (mAutoFocus) {
            CameraLog.i(TAG, "takePicture => lockFocus");
            lockFocus();
        } else {
            CameraLog.i(TAG, "takePicture => captureStillPicture");
            captureStillPicture();
        }
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        mPreview.setDisplayOrientation(mDisplayOrientation);
    }

    /**
     * 选择摄像头设备 (可能修改mCameraId, mCameraCharacteristics, mFacing的值)
     * <p>
     * https://source.android.com/devices/camera/versioning
     */
    private boolean chooseCamera() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(mFacing);
            final String[] ids = mCameraManager.getCameraIdList();
            if (ids.length == 0) { // No camera
                //throw new RuntimeException("No camera available");
                CameraLog.e(TAG, "chooseCamera, no camera available");
                return false;
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {//legacy 旧系统的，也就是对Camera2支持比较低的级别，这种级别的Camera不建议使用，否则容易产生很多问题
                    CameraLog.e(TAG, "chooseCamera, level is null or LEVEL_LEGACY");
                    continue;
                }
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    //throw new NullPointerException("Unexpected state: LENS_FACING null");
                    CameraLog.e(TAG, "chooseCamera, unexpected state: LENS_FACING null");
                    return false;
                }
                if (internal == internalFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    CameraLog.i(TAG, "chooseCamera, CameraId = " + mCameraId);
                    return true;
                }
            }

            //没找到合适的摄像头就尝试使用第一个摄像头设备
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer level = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                CameraLog.e(TAG, "chooseCamera, level is null or LEVEL_LEGACY");
                return false;//前面是在for循环中遍历，所以如果当前遍历的这个camera不行的话可以continue到下一个，而这里不行的话就必须直接return
            }
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);//back, front, external
            if (internal == null) {
                //throw new NullPointerException("Unexpected state: LENS_FACING null");
                CameraLog.e(TAG, "chooseCamera, unexpected state: LENS_FACING null");
                return false;
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    CameraLog.i(TAG, "chooseCamera, CameraId = 0, mFacing = " + mFacing);
                    return true;
                }
            }

            //The operation can reach here when the only camera device is an external one. We treat it as facing back.
            CameraLog.e(TAG, "chooseCamera, current camera device is an external one");
            mFacing = CameraConfig.FACING_BACK;
            return true;
        } catch (CameraAccessException e) {
            //throw new RuntimeException("Failed to get a list of camera devices", e);
            CameraLog.e(TAG, "chooseCamera, failed to get a list of camera devices", e);
            return false;
        }
    }

    /**
     * 收集摄像头的数据 (可能修改mPreviewSizes, mPictureSizes, mAspectRatio的值)
     */
    private void collectCameraInfo() {
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            //throw new IllegalStateException("Failed to get configuration map: " + mCameraId);
            CameraLog.e(TAG, "collectCameraInfo, Failed to get configuration map: " + mCameraId);
            return;
        }

        //获取支持的图片预览大小
        mPreviewSizes.clear();
        collectPreviewSizes(mPreviewSizes, map);
        CameraLog.i(TAG, "collectCameraInfo, collectPreviewSizes: %s", mPreviewSizes);
        //Google Pixel: [3:2]:{480x320,720x480,}; [3:4]:{240x320,480x640,}; [4:3]:{160x120,320x240,480x360,640x480,800x600,1024x768,1280x960,1440x1080,}; [5:3]:{800x480,1280x768,}; [9:11]:{144x176,}; [11:9]:{176x144,352x288,}; [16:9]:{640x360,1280x720,1920x1080,};

        //获取支持的图片输出大小
        mPictureSizes.clear();
        collectPictureSizes(mPictureSizes, map);
        CameraLog.i(TAG, "collectCameraInfo, collectPictureSizes: %s", mPictureSizes);
        //Google Pixel: [1:1]:{2976x2976,}; [3:2]:{480x320,720x480,}; [3:4]:{240x320,480x640,}; [4:3]:{160x120,320x240,480x360,640x480,800x600,1024x768,1280x960,1440x1080,1600x1200,2048x1536,2592x1944,3200x2400,3264x2448,4000x3000,4048x3036,}; [5:3]:{800x480,1280x768,}; [9:11]:{144x176,}; [11:9]:{176x144,352x288,}; [16:9]:{640x360,1280x720,1920x1080,2688x1512,3840x2160,};
        //Nexus 5: [3:2]:{720 x 480, }; [4:3]:{320 x 240, 640 x 480, 800 x 600, 1024 x 768, 1280 x 960, 1600 x 1200, 2048 x 1536, 2592 x 1944, 3200 x 2400, 3264 x 2448, }; [5:3]:{800 x 480, 1280 x 768, }; [11:9]:{176 x 144, 352 x 288, }; [16:9]:{1280 x 720, 1920 x 1080, };

        //调整Preview sizes
        adjustPreviewSizes();
        CameraLog.i(TAG, "collectCameraInfo, adjustPrevewSizes: %s", mPreviewSizes);

        //选择合适的宽高比
        chooseAspectRatio();
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

    private void chooseAspectRatio() {
        if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
            if (mPreviewSizes.ratios().contains(CameraConfig.DEFAULT_ASPECT_RATIO)) {//首先看16:9是否支持
                mAspectRatio = CameraConfig.DEFAULT_ASPECT_RATIO;
            } else if (mPreviewSizes.ratios().contains(CameraConfig.SECOND_ASPECT_RATIO)) {//再看4:3是否支持
                mAspectRatio = CameraConfig.SECOND_ASPECT_RATIO;
            } else {//两个都不支持的话就取它支持的第一个作为当前的宽高比
                mAspectRatio = mPreviewSizes.ratios().iterator().next();
            }
            CameraLog.i(TAG, "chooseAspectRatio, aspect ratio changed to " + mAspectRatio.toString());
        }
    }

    private void collectPreviewSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(mPreview.getOutputClass())) {
            int width = size.getWidth();
            int height = size.getHeight();
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                sizes.add(new Size(width, height));
            }
        }
    }

    //Camera2Api23主要就是override了这个方法，它可以调用map.getHighResolutionOutputSizes方法获取更高分辨率的输出图片
    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            sizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    //ImageReader和mAspectRatio的值有关，如果mAspectRatio改变了的话需要重新创建ImageReader
    private void prepareImageReader() {
        if (mImageReader != null) {
            mImageReader.close();
        }
        Size outputSize = choosePictureSize();
        mImageReader = ImageReader.newInstance(outputSize.getWidth(), outputSize.getHeight(), ImageFormat.JPEG, /* maxImages */ 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
        CameraLog.i(TAG, "prepareImageReader, size: %d x %d", outputSize.getWidth(), outputSize.getHeight());
    }

    /**
     * 这里可以针对具体需求调整最合适的宽高比和输出图片大小
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

    //获取中间那个大小
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

    /**
     * 打开摄像头，在mCameraDeviceCallback中处理回调
     */
    private boolean startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
            return true;
        } catch (CameraAccessException e) {
            //throw new RuntimeException("Failed to open camera: " + mCameraId, e);
            CameraLog.e(TAG, "startOpeningCamera, failed to open camera " + mCameraId, e);
            return false;
        }
    }

    /**
     * CameraDevice.StateCallback
     */
    private final CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            CameraLog.i(TAG, "mCameraDeviceCallback, onOpened => startCaptureSession");
            mCamera = camera;
            mCallback.onCameraOpened();
            startCaptureSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            CameraLog.i(TAG, "mCameraDeviceCallback, onClosed");
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            CameraLog.i(TAG, "mCameraDeviceCallback, onDisconnected");
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            CameraLog.e(TAG, "mCameraDeviceCallback, onError: " + camera.getId() + " (" + error + ")");
            mCamera = null;
        }

    };

    /**
     * 开启一个capture session来显示相机的预览图像 (可能会修改mPreviewRequestBuilder)
     * <p>
     * 这个方法有三个地方会调用
     * 1.CameraDevice.StateCallback, onOpened => startCaptureSession    此时进入的时候往往还没有准备好会直接返回
     * 2.CameraPreview.Callback, onSurfaceChanged => startCaptureSession  一般在这里进入的时候才会真正去startCaptureSession
     * 3.setAspectRatio    调整了宽高比之后需要重置ImageReader和startCaptureSession
     * <p>
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mPreviewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link #mSessionCallback}.</p>
     */
    private void startCaptureSession() {
        //往往在CameraDevice.StateCallback的onOpened中调用startCaptureSession时mPreview和mImageReader都还没有准备好，所以这个时候可以直接返回了
        if (!isCameraOpened() || !mPreview.isReady() || mImageReader == null) {
            CameraLog.i(TAG, "startCaptureSession, Camera open? %s, Preview ready? %s, ImageReader created? %s", isCameraOpened(), mPreview.isReady(), mImageReader == null);
            return;
        }

        Size previewSize = chooseOptimalSize();
        CameraLog.i(TAG, "startCaptureSession, chooseOptimalSize = %s", previewSize.toString());

        mVideoSize = previewSize;

        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());//Camera2Manager API有效
        Surface surface = mPreview.getSurface();
        try {
            //先后调用Camera的createCaptureRequest和createCaptureSession方法
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionCallback, null);
            //capture的图像内容将输出到Preview的surface和ImageReader的surface中，在mSessionCallback中处理createCaptureSession的回调
        } catch (CameraAccessException e) {
            //throw new RuntimeException("Failed to startCamera camera session");
            CameraLog.e(TAG, "startCaptureSession, failed to startCamera camera session", e);
        }
    }

    /**
     * CameraCaptureSession.StateCallback
     */
    private final CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCamera == null) {
                CameraLog.e(TAG, "mSessionCallback, onConfigured, Camera is null");
                return;
            }
            CameraLog.i(TAG, "mSessionCallback, onConfigured, CameraCaptureSession created");
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash();
            try {
                //创建好了CameraCaptureSession之后就不停地发送preview request，回调在在mCaptureCallback中处理
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            } catch (CameraAccessException e) {
                //Log.e(TAG, "Failed to startCamera camera preview because it couldn't access camera", e);
                CameraLog.e(TAG, "mSessionCallback, onConfigured, failed to startCamera camera preview because it couldn't access camera", e);
            } catch (IllegalStateException e) {
                //Log.e(TAG, "Failed to startCamera camera preview.", e);
                //如果摄像头已经配置打开过了，再配置的话会报错：java.lang.IllegalStateException: Session has been closed; further changes are illegal.
                CameraLog.e(TAG, "mSessionCallback, onConfigured, failed to startCamera camera preview", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            //Log.e(TAG, "Failed to configure capture session.");
            CameraLog.e(TAG, "mSessionCallback, onConfigureFailed, failed to configure capture session");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            CameraLog.i(TAG, "mSessionCallback, onClosed");
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }

    };

    /**
     * 根据摄像头支持的mPreviewSizes和surface的大小选择一个最优的大小来显示摄像头的预览图像
     * <p>
     * 例如：对于Google Pixel来说，surface大小 width = 1731, height = 2308，得到的 OptimalSize = 1440 x 1080 (4:3最大支持的图片大小)
     * <p>
     * Chooses the optimal preview size based on {@link #mPreviewSizes} and the surface size.
     */
    private Size chooseOptimalSize() {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }
        SortedSet<Size> candidates = mPreviewSizes.sizes(mAspectRatio);

        // Pick the smallest of those big enough
        for (Size size : candidates) {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last();
    }

    private void updateAutoFocus() {
        if (mAutoFocus) {
            int[] modes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 || (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                detachFocusTapListener();
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                CameraLog.d(TAG, "updateAutoFocus, auto focus is not supported, so AF_MODE = CONTROL_AF_MODE_OFF");
            } else {//the AF algorithm modifies the lens position continually to attempt to provide a constantly-in-focus image stream
                attachFocusTapListener();
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                CameraLog.d(TAG, "updateAutoFocus, AF_MODE = CONTROL_AF_MODE_CONTINUOUS_PICTURE");
            }
        } else {
            detachFocusTapListener();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            CameraLog.d(TAG, "updateAutoFocus, AF_MODE = CONTROL_AF_MODE_OFF");
        }
    }

    //手动对焦参考方案：https://github.com/lin18/cameraview/commit/47b8a4e493cdb5f1085333577d55b749443047e9
    private void attachFocusTapListener() {
        mPreview.getView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (mCamera != null) {
                        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                        if (rect == null) return true;

                        int areaSize = getFocusAreaSize();
                        int right = rect.right;
                        int bottom = rect.bottom;
                        int viewWidth = mPreview.getView().getWidth();
                        int viewHeight = mPreview.getView().getHeight();
                        int ll, rr;
                        Rect newRect;
                        int centerX = (int) event.getX();
                        int centerY = (int) event.getY();
                        ll = ((centerX * right) - areaSize) / viewWidth;
                        rr = ((centerY * bottom) - areaSize) / viewHeight;
                        int focusLeft = clamp(ll, 0, right);
                        int focusBottom = clamp(rr, 0, bottom);
                        newRect = new Rect(focusLeft, focusBottom, focusLeft + areaSize, focusBottom + areaSize);
                        MeteringRectangle meteringRectangle = new MeteringRectangle(newRect, getFocusMeteringAreaWeight());
                        MeteringRectangle[] meteringRectangleArr = {meteringRectangle};

                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArr);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRectangleArr);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        //在Google Pixel上测试手动对焦时，发现如果当前摄像头的画面非常暗的话，这里会打开一下闪光灯以便对焦

                        try {
                            if (mCaptureSession != null) {
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                            }
                        } catch (CameraAccessException e) {
                            CameraLog.e(TAG, "attachFocusTapListener", e);
                        }
                    }
                }
                return true;
            }
        });
    }

    private int clamp(int x, int min, int max) {
        if (x < min) {
            return min;
        } else if (x > max) {
            return max;
        } else {
            return x;
        }
    }

    private void updateFlash() {
        switch (mFlash) {
            case CameraConfig.FLASH_OFF:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                CameraLog.d(TAG, "updateFlash, AE_MODE = CONTROL_AE_MODE_ON, FLASH_MODE = FLASH_MODE_OFF");
                break;
            case CameraConfig.FLASH_ON:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                CameraLog.d(TAG, "updateFlash, AE_MODE = CONTROL_AE_MODE_ON_ALWAYS_FLASH, FLASH_MODE = FLASH_MODE_OFF");
                break;
            case CameraConfig.FLASH_TORCH:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                CameraLog.d(TAG, "updateFlash, AE_MODE = CONTROL_AE_MODE_ON, FLASH_MODE = MODE_TORCH");
                break;
            case CameraConfig.FLASH_AUTO:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                CameraLog.d(TAG, "updateFlash, AE_MODE = CONTROL_AE_MODE_ON_AUTO_FLASH, FLASH_MODE = FLASH_MODE_OFF");
                break;
            case CameraConfig.FLASH_RED_EYE:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                CameraLog.d(TAG, "updateFlash, AE_MODE = CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE, FLASH_MODE = FLASH_MODE_OFF");
                break;
        }
    }

    /**
     * 拍照之前先要锁住对焦
     * <p>
     * Locks the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        CameraLog.i(TAG, "lockFocus, set CONTROL_AF_TRIGGER = CONTROL_AF_TRIGGER_START");
        try {
            mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            //Log.e(TAG, "Failed to lock focus.", e);
            CameraLog.e(TAG, "lockFocus, fail to lock focus,", e);
        }
    }

    /**
     * 拍照
     */
    private void captureStillPicture() {
        try {
            CaptureRequest.Builder captureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            CameraLog.d(TAG, "captureStillPicture, AF_MODE = " + captureRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (mFlash) {
                case CameraConfig.FLASH_OFF:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    CameraLog.d(TAG, "captureStillPicture, AE_MODE = CONTROL_AE_MODE_ON, FLASH_MODE = FLASH_MODE_OFF");
                    break;
                case CameraConfig.FLASH_ON:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    CameraLog.d(TAG, "captureStillPicture, AE_MODE = CONTROL_AE_MODE_ON_ALWAYS_FLASH");
                    break;
                case CameraConfig.FLASH_TORCH:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    CameraLog.d(TAG, "captureStillPicture, AE_MODE = CONTROL_AE_MODE_ON, FLASH_MODE = FLASH_MODE_TORCH");
                    break;
                case CameraConfig.FLASH_AUTO:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    CameraLog.d(TAG, "captureStillPicture, AE_MODE = CONTROL_AE_MODE_ON_AUTO_FLASH");
                    break;
                case CameraConfig.FLASH_RED_EYE:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    CameraLog.d(TAG, "captureStillPicture, AE_MODE = CONTROL_AE_MODE_ON_AUTO_FLASH");
                    break;
            }

            // Calculate JPEG orientation.
            @SuppressWarnings("ConstantConditions")
            int sensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    (sensorOrientation + mDisplayOrientation * (mFacing == CameraConfig.FACING_FRONT ? 1 : -1) + 360) % 360);

            // Stop preview and capture a still picture.
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    CameraLog.i(TAG, "captureStillPicture, onCaptureCompleted => unlockFocus");
                    unlockFocus();
                }
            }, null);
        } catch (CameraAccessException e) {
            //Log.e(TAG, "Cannot capture a still picture.", e);
            CameraLog.e(TAG, "captureStillPicture, fail to capture still picture", e);
        }
    }

    /**
     * 解锁自动对焦并重启相机预览 (一般是拍照之后调用)
     * <p>
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after capturing a still picture.
     */
    private void unlockFocus() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        CameraLog.d(TAG, "unlockFocus, set CONTROL_AF_TRIGGER = CONTROL_AF_TRIGGER_CANCEL");
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            updateAutoFocus();
            updateFlash();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            CameraLog.d(TAG, "unlockFocus, set CONTROL_AF_TRIGGER = CONTROL_AF_TRIGGER_IDLE");
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
        } catch (CameraAccessException e) {
            //Log.e(TAG, "Failed to restart camera preview.", e);
            CameraLog.e(TAG, "captureStillPicture, fail to restart camera preview", e);
        }
    }

    /**
     * PictureCaptureCallback
     */
    private PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback() {

        @Override
        public void onPrecaptureRequired() {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            CameraLog.d(TAG, "mCaptureCallback, onPrecaptureRequired, set CONTROL_AE_PRECAPTURE_TRIGGER = CONTROL_AE_PRECAPTURE_TRIGGER_START");
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                CameraLog.d(TAG, "mCaptureCallback, onPrecaptureRequired, set CONTROL_AE_PRECAPTURE_TRIGGER = CONTROL_AE_PRECAPTURE_TRIGGER_IDLE");
            } catch (CameraAccessException e) {
                //Log.e(TAG, "Failed to run precapture sequence.", e);
                CameraLog.e(TAG, "mCaptureCallback, onPrecaptureRequired", e);
            }
        }

        @Override
        public void onReady() {
            CameraLog.i(TAG, "mCaptureCallback, onReady => captureStillPicture");
            captureStillPicture();
        }

    };

    /**
     * CameraCaptureSession.CaptureCallback (用于拍照)
     */
    private static abstract class PictureCaptureCallback extends CameraCaptureSession.CaptureCallback {

        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private int mState;

        PictureCaptureCallback() {
        }

        void setState(int state) {
            mState = state;
            CameraLog.i(TAG, "PictureCaptureCallback, set state = %d", mState);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            //CameraLog.i(TAG, "PictureCaptureCallback, onCaptureProgressed");//该log会刷屏
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            //CameraLog.i(TAG, "PictureCaptureCallback, onCaptureCompleted");//该log会刷屏，每次有新的一帧预览图片的时候就会回调onCaptureProgressed和onCaptureCompleted方法
            process(result);
        }

        private void process(@NonNull CaptureResult result) {
            switch (mState) {
                case STATE_LOCKING: {
                    Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) {
                        break;
                    }
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING);
                            onReady();
                        } else {
                            setState(STATE_LOCKED);
                            onPrecaptureRequired();
                        }
                    }
                    break;
                }
                case STATE_PRECAPTURE: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_WAITING);
                    }
                    break;
                }
                case STATE_WAITING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                    break;
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        public abstract void onReady();

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        public abstract void onPrecaptureRequired();

    }

    /**
     * 方法captureStillPicture调用mCaptureSession.capture之后ImageReader将会收到image data，从而回调onImageAvailable方法
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireNextImage()) {
                CameraLog.i(TAG, "ImageReader, onImageAvailable, image size = %d x %d, time = %s", image.getWidth(), image.getHeight(), String.valueOf(image.getTimestamp()));

                Image.Plane[] planes = image.getPlanes();
                if (planes.length > 0) {
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

//                    mCallback.onPictureTaken(data);

                    compressImage(data,mCameraOption);
                }
            }
        }

    };


    @Override
    public boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

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

            //设置输出的视频播放的方向提示
            mMediaRecorder.setOrientationHint(mFacing == CameraConfig.FACING_FRONT ?(mDisplayOrientation+180)%360:mDisplayOrientation);
            Log.e(TAG, "setOrientationHint : " + mDisplayOrientation);
            videoPath =  FileUtils.getVideoLocalPath(mContext);
            mMediaRecorder.setOutputFile(videoPath);

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
                closePreviewSession();
                if (prepareVideoRecorder()) {
                    try {
                        mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        final List<Surface> surfaces = new ArrayList<>();

                        final Surface previewSurface = mPreview.getSurface();
                        surfaces.add(previewSurface);
                        mPreviewRequestBuilder.addTarget(previewSurface);

                        Surface mWorkingSurface = mMediaRecorder.getSurface();
                        surfaces.add(mWorkingSurface);
                        mPreviewRequestBuilder.addTarget(mWorkingSurface);

                        mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                mCaptureSession = cameraCaptureSession;

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                try {
                                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
                                } catch (Exception e) {
                                }

                                try {
                                    mMediaRecorder.start();
                                } catch (Exception ignore) {
                                    Log.e(TAG, "mMediaRecorder.startCamera(): ", ignore);
                                }

                                mIsVideoRecording = true;

                                mCallback.onStartVideoRecorder();
//                                mUiiHandler.post(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        cameraVideoListener.onVideoRecordStarted(mVideoSize);
//                                    }
//                                });
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Log.d(TAG, "onConfigureFailed");
                            }
                        }, mBackgroundHandler);


                    } catch (Exception e) {
                        Log.e(TAG, "startVideoRecord: ", e);
                    }
                }
            }
        });
    }

    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            try {
                mCaptureSession.abortCaptures();
            } catch (Exception ignore) {
            } finally {
                mCaptureSession = null;
            }
        }
    }

    @Override
    public void stopVideoRecorder() {
        if (mIsVideoRecording) {
            closePreviewSession();
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.stop();
                } catch (Exception ignore) {
                }
            }
            mIsVideoRecording = false;
            releaseVideoRecorder();

            mCallback.onCompleteVideoRecorder();
            Log.d(TAG, "mMediaRecorder stopCamera!");
            compressVideo(videoPath,mCameraOption);
        }
    }
}
