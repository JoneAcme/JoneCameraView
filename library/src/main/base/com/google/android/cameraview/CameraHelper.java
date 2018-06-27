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
import android.content.SharedPreferences;

/**
 * 相机辅助类
 *
 * Created by javayhu on 9/8/17.
 */
public class CameraHelper {

    private static CameraHelper sInstance;
    private static final String SP_NAME = "camera";
    private static final String KEY_USE_CAMERA1 = "key_use_camera1";

    private SharedPreferences mSharedPreferences;

    private CameraHelper(Context context) {
        mSharedPreferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static CameraHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CameraHelper(context);
        }
        return sInstance;
    }

    public void setUseCamera1InFuture() {
        if (mSharedPreferences != null) {
            mSharedPreferences.edit().putBoolean(KEY_USE_CAMERA1, true).apply();
        }
    }

    //是否应该使用Camera1 API
    public boolean shouldUseCamera1() {
        if (mSharedPreferences != null) {
            return mSharedPreferences.getBoolean(KEY_USE_CAMERA1, false);
        }
        return false;
    }

}
