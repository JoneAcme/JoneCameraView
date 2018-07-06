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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.cameraview.callback.CameraControlListener;
import com.google.android.cameraview.callback.CameraManagerCallBack;
import com.google.android.cameraview.callback.CameraPictureListener;
import com.google.android.cameraview.callback.CameraVideoRecorderListener;
import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.helper.CameraHelper;
import com.google.android.cameraview.logs.CameraLog;
import com.google.android.cameraview.model.AspectRatio;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Set;

/**
 * 自定义的CameraView
 */
public class CameraView extends FrameLayout implements CameraManagerCallBack {

    private static final String TAG = CameraView.class.getSimpleName();

    public static final int FACING_BACK = CameraConfig.FACING_BACK;
    public static final int FACING_FRONT = CameraConfig.FACING_FRONT;


    private CameraViewOptions options ;
    @IntDef({FACING_BACK, FACING_FRONT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
    }

    public static final int FLASH_OFF = CameraConfig.FLASH_OFF;
    public static final int FLASH_ON = CameraConfig.FLASH_ON;
    public static final int FLASH_TORCH = CameraConfig.FLASH_TORCH;//手电筒状态
    public static final int FLASH_AUTO = CameraConfig.FLASH_AUTO;
    public static final int FLASH_RED_EYE = CameraConfig.FLASH_RED_EYE;

    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flash {
    }

    private boolean mRequestLayoutOnOpen;
    private boolean mAdjustViewBounds;
    private CameraPreview mPreviewImpl;
    private CameraManager mCameraManager;
    private final DisplayOrientationDetector mDisplayOrientationDetector;

    private CameraControlListener mControlListener;

    private CameraVideoRecorderListener mRecorderListener;


    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            mDisplayOrientationDetector = null;
            return;
        }
        options = new CameraViewOptions.Builder(context).create();
        // Internal setup
        mPreviewImpl = createPreviewImpl(context);
        mCameraManager = createCameraViewImpl(context, mPreviewImpl,options);

        // Attributes R.style.Widget_CameraView中是参数默认值
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyleAttr, R.style.Widget_CameraView);
        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));//默认后置摄像头
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (aspectRatio != null) {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        } else {
            setAspectRatio(CameraConfig.DEFAULT_ASPECT_RATIO);//默认宽高比是16:9
        }
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));//默认自动对焦模式
        setFlash(a.getInt(R.styleable.CameraView_flash, CameraConfig.FLASH_AUTO));//默认关闭闪光灯
        a.recycle();

        // Display orientation detector
        mDisplayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                CameraLog.i(TAG, "onDisplayOrientationChanged, degree = %d", displayOrientation);
                mCameraManager.setDisplayOrientation(displayOrientation);
            }
        };

        // Focus marker
        final FocusMarkerView mFocusMarkerView = new FocusMarkerView(getContext());
        addView(mFocusMarkerView);
        mFocusMarkerView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                if (action == MotionEvent.ACTION_UP) {
                    mFocusMarkerView.focus(motionEvent.getX(), motionEvent.getY());
                }

                if (mPreviewImpl != null && mPreviewImpl.getView() != null) {
                    mPreviewImpl.getView().dispatchTouchEvent(motionEvent);
                }
                return true;
            }
        });
    }

    //@NonNull
    private CameraPreview createPreviewImpl(Context context) {
        if (Build.VERSION.SDK_INT < 14) {
            CameraLog.i(TAG, "createPreviewImpl, sdk version = %d, create SurfaceViewPreview", Build.VERSION.SDK_INT);
            return new SurfaceViewPreview(context, this);
        } else {
            CameraLog.i(TAG, "createPreviewImpl, sdk version = %d, create TextureViewPreview", Build.VERSION.SDK_INT);
            return new TextureViewPreview(context, this);
        }
    }

    private CameraManager createCameraViewImpl(Context context, CameraPreview preview,CameraViewOptions cameraViewOptions) {

        if (CameraHelper.getInstance(getContext()).shouldUseCamera1()) {//只使用Camera1的方案
            CameraLog.i(TAG, "createCameraViewImpl, sdk version = %d, create Camera1Manager (for previous experience)", Build.VERSION.SDK_INT);
            return new Camera1Manager(this, preview, context,cameraViewOptions);
        } else {//根据版本可能使用Camera2的方案
            if (Build.VERSION.SDK_INT < 21) {
                CameraLog.i(TAG, "createCameraViewImpl, sdk version = %d, create Camera1Manager", Build.VERSION.SDK_INT);
                return new Camera1Manager(this, preview, context,cameraViewOptions);
            } else if (Build.VERSION.SDK_INT < 23) {
                CameraLog.i(TAG, "createCameraViewImpl, sdk version = %d, create Camera2Manager", Build.VERSION.SDK_INT);
                return new Camera2Manager(this, preview, context,cameraViewOptions);
            } else {
                CameraLog.i(TAG, "createCameraViewImpl, sdk version = %d, create Camera2Api23", Build.VERSION.SDK_INT);
                return new Camera2Api23(this, preview, context,cameraViewOptions);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CameraLog.i(TAG, "onAttachedToWindow");
        if (!isInEditMode()) {
            //support-v4 23.0.1版本的ViewCompat没有getDisplay方法，要25版本才有
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        CameraLog.i(TAG, "onDetachedFromWindow");
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Handle android:adjustViewBounds
        if (mAdjustViewBounds) {
            if (!isCameraOpened()) {//此时相机还没有打开，这里设置一个标志位，等相机打开的时候做一次requestLayout
                reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int height = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int width = (int) (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if (mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) {//横屏的时候宽高比要逆过来
            ratio = ratio.inverse();
        }
        assert ratio != null;
        if (height < width * ratio.getY() / ratio.getX()) {// Measure the TextureView
            mCameraManager.getView().measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(), MeasureSpec.EXACTLY));//这里是为了维持这个宽高比，将高度增加点
        } else {
            mCameraManager.getView().measure(MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));//这里是为了维持这个宽高比，将宽度增加点
        }
        //mCameraManager.getView().measure将触发对应的TextureViewPreview或者SurfaceViewPreview的onSurfaceTextureAvailable方法被调用，进而触发PreviewImpl的mCallback.onSurfaceChanged()被触发
        //对于mCallback.onSurfaceChanged的处理，在Camera1中是重新setUpPreview，并调整相机参数adjustCameraParameters；在Camera2中重新startCaptureSession
        //start方法中有个逻辑是如果Camera2API不行的话会降级到Camera1，这个时候前面的CameraViewImpl按照原始代码逻辑的话就会重建！那么之前设置的callback就相当于白调用了，部分手机例如魅族MX6上可能引起预览模糊的问题
    }

    /**
     * 打开摄像头 (这里做了个优化点，如果上一次启动Camera2失败但是启动Camera1成功的话，那么以后就直接使用Camera1，不需要再进行切换)
     */
    public boolean openCamera() {
        CameraLog.i(TAG, "startCamera camera begin");
        boolean isSuccess = mCameraManager.startCamera();
        if (isSuccess) {
            CameraLog.i(TAG, "startCamera camera success");
        } else {
            CameraLog.i(TAG, "startCamera camera fail, try Camera1Manager");//测试机：小米 5/4c，Vivo X7，Meizu MX6/Pro6，Galaxy S4，Huawei H60-L11 走到这里
            //store the state, and restore this state after fall back o Camera1Manager
            Parcelable state = onSaveInstanceState();
            //Camera2Manager uses legacy hardware layer; fall back to Camera1Manager
            //mCameraManager = new Camera1Manager(mCallbackBridge, createPreviewImpl(getContext()));//要保证start方法先于onMeasure方法被调用，这样的话onMeasure方法中调用mCameraViewImpl.getView().measure之后才能正确调用到callback
            if (mPreviewImpl == null || mPreviewImpl.getView() == null) {//可以避免重复创建，只是替换CameraView，不用替换PreviewImpl的实现，预览组件的大小也维持之前的设置 (aspect ratio没变)
                mPreviewImpl = createPreviewImpl(getContext());
            }
            mCameraManager = new Camera1Manager(this, mPreviewImpl, getContext(),options);
            onRestoreInstanceState(state);
            isSuccess = mCameraManager.startCamera();
            if (isSuccess) {
                CameraLog.i(TAG, "startCamera camera with Camera1Manager success, set to use Camera1Manager in the future");
                CameraHelper.getInstance(getContext()).setUseCamera1InFuture();
            } else {
                CameraLog.i(TAG, "startCamera camera with Camera1Manager fail");
            }
        }
        return isSuccess;
    }

    /**
     * 关闭摄像头
     */
    public void stopCamera() {
        CameraLog.i(TAG, "stopCamera camera");
        mCameraManager.stopCamera();
    }


    public void stopAndReleaseCamera() {
        try {
            stopCamera();
            mCameraManager.releaseCameraManager();
        } catch (Exception e) {
            CameraLog.e(TAG, "stopCamera error:"+e.getMessage());
        }

    }

    /**
     * 判断摄像头是否打开了
     */
    public boolean isCameraOpened() {
        return mCameraManager.isCameraOpened();
    }

    /**
     * 设置使用前置还是后置摄像头
     */
    public void setFacing(@Facing int facing) {
        CameraLog.i(TAG, "setFacing, facing = %s", (facing == FACING_BACK ? "back" : "front"));
        mCameraManager.setFacing(facing);
    }

    /**
     * 前后摄像头的切换
     */
    public void swithCamera() {
        setFacing(getFacing() == CameraView.FACING_FRONT ? CameraView.FACING_BACK : CameraView.FACING_FRONT);
    }

    @Facing
    public int getFacing() {
        //noinspection WrongConstant
        return mCameraManager.getFacing();
    }

    /**
     * 设置自动对焦模式
     */
    public void setAutoFocus(boolean autoFocus) {
        CameraLog.i(TAG, "setAutoFocus, autoFocus = %s", autoFocus);
        mCameraManager.setAutoFocus(autoFocus);
    }

    public boolean getAutoFocus() {
        return mCameraManager.getAutoFocus();
    }

    /**
     * 设置闪光灯模式
     */
    public void setFlash(@Flash int flash) {
        CameraLog.i(TAG, "setFlash, flash = %d (0-off,1-on,2-torch,3-auto,4-redeye)", flash);
        mCameraManager.setFlash(flash);
    }

    @Flash
    public int getFlash() {
        //noinspection WrongConstant
        return mCameraManager.getFlash();
    }

    /**
     * 获取摄像头设备支持的宽高比
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mCameraManager.getSupportedAspectRatios();
    }

    public void setAspectRatio(@NonNull AspectRatio ratio) {
        CameraLog.i(TAG, "setAspectRatio, ratio = %s", ratio.toString());
        if (mCameraManager.setAspectRatio(ratio)) {
            requestLayout();
        }
    }

    @Nullable
    public AspectRatio getAspectRatio() {
        return mCameraManager.getAspectRatio();
    }

    /**
     * 拍照
     */
    public void takePicture() {
        mCameraManager.takePicture();
    }


    /**
     * CameraView的保存状态数据(SavedState)
     */
    protected static class CameraViewSavedState extends BaseSavedState {

        @Facing
        int facing;
        @Flash
        int flash;
        AspectRatio ratio;
        boolean autoFocus;

        @SuppressWarnings("WrongConstant")
        public CameraViewSavedState(Parcel source, ClassLoader loader) {
            super(source);
            facing = source.readInt();
            ratio = source.readParcelable(loader);
            autoFocus = source.readByte() != 0;
            flash = source.readInt();
        }

        public CameraViewSavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeParcelable(ratio, 0);
            out.writeByte((byte) (autoFocus ? 1 : 0));
            out.writeInt(flash);
        }

        public static final Parcelable.Creator<CameraViewSavedState> CREATOR = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<CameraViewSavedState>() {

            @Override
            public CameraViewSavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new CameraViewSavedState(in, loader);
            }

            @Override
            public CameraViewSavedState[] newArray(int size) {
                return new CameraViewSavedState[size];
            }

        });

    }

    @Override
    protected Parcelable onSaveInstanceState() {
        CameraLog.i(TAG, "onSaveInstanceState: facing = %d, autofocus = %s, flash = %d, ratio = %s", getFacing(), getAutoFocus(), getFlash(), getAspectRatio());
        CameraViewSavedState state = new CameraViewSavedState(super.onSaveInstanceState());
        state.facing = getFacing();
        state.ratio = getAspectRatio();
        state.autoFocus = getAutoFocus();
        state.flash = getFlash();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof CameraViewSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        CameraViewSavedState ss = (CameraViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setFacing(ss.facing);
        setAspectRatio(ss.ratio);
        setAutoFocus(ss.autoFocus);
        setFlash(ss.flash);
        CameraLog.i(TAG, "onRestoreInstanceState: facing = %d, autofocus = %s, flash = %d, ratio = %s", getFacing(), getAutoFocus(), getFlash(), getAspectRatio());
    }


    public void reserveRequestLayoutOnOpen() {
        mRequestLayoutOnOpen = true;
    }

    @Override
    public void onCameraOpened() {
        if (mRequestLayoutOnOpen) {
            mRequestLayoutOnOpen = false;
            requestLayout();
        }
        if (null != mControlListener) mControlListener.onCameraOpened(this);
    }

    @Override
    public void onCameraClosed() {
        if (null != mControlListener) mControlListener.onCameraClosed(this);
    }


    @Override
    public void onStartVideoRecorder() {
        if (null != mRecorderListener) mRecorderListener.onStartVideoRecorder();
    }

    @Override
    public void onCompleteVideoRecorder() {
        if (null != mRecorderListener) mRecorderListener.onCompleteVideoRecorder();
    }

    public void setControlListener(CameraControlListener mControlListener) {
        this.mControlListener = mControlListener;
    }

    public void setRecorderListener(CameraVideoRecorderListener mRecorderListener) {
        this.mRecorderListener = mRecorderListener;
    }

    public void startVideoRecorder() {
        mCameraManager.startVideoRecorder();
    }

    public void stopVideoRecorder() {
        mCameraManager.stopVideoRecorder();
    }


    /**
     * 拍照，录像配置
     *
     * @param mCameraOption
     */
    public void setCameraOption(CameraViewOptions mCameraOption) {
        this.options = mCameraOption;
        mCameraManager.setCameraOption(mCameraOption);
    }
}
