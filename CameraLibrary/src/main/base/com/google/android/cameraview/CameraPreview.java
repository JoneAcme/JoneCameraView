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

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;


/**
 * 不同方式实现Preview的抽象基类
 *
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
abstract class CameraPreview {

    private int mWidth;
    private int mHeight;
    private Callback mCallback;


    SurfaceHolder getSurfaceHolder() {
        return null;
    }

    Object getSurfaceTexture() {
        return null;
    }

    void setBufferSize(int width, int height) {
    }

    void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    int getWidth() {
        return mWidth;
    }

    int getHeight() {
        return mHeight;
    }

    void setCallback(Callback callback) {
        mCallback = callback;
    }

    void dispatchSurfaceChanged() {
        mCallback.onSurfaceChanged();
    }


    /**
     * 抽象方法
     */
    abstract Surface getSurface();

    abstract View getView();

    abstract Class getOutputClass();

    abstract void setDisplayOrientation(int displayOrientation);

    abstract boolean isReady();


    /**
     * surface变化时的回调
     */
    interface Callback {
        void onSurfaceChanged();
    }

}

