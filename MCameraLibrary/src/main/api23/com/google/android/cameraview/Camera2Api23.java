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
import android.hardware.camera2.params.StreamConfigurationMap;

import com.google.android.cameraview.callback.CameraManagerCallBack;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.model.Size;

/**
 * 在Android 6.0系统以上使用Camera2Api23作为CameraView的实现，它与Camera2的区别在于获取输出图片大小的方式略有不同
 */
@TargetApi(23)
class Camera2Api23 extends Camera2Manager {

    Camera2Api23(CameraManagerCallBack callback, CameraPreview preview, Context context,CameraViewOptions options) {
        super(callback, preview, context,options);
    }

    @Override
    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        // Try to get hi-res output sizes
        android.util.Size[] outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (outputSizes != null) {
            for (android.util.Size size : map.getHighResolutionOutputSizes(ImageFormat.JPEG)) {
                sizes.add(new Size(size.getWidth(), size.getHeight()));
            }
        }
        if (sizes.isEmpty()) {
            super.collectPictureSizes(sizes, map);
        }
    }

}
