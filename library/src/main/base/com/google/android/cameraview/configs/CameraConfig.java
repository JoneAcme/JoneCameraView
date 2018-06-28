package com.google.android.cameraview.configs;

import android.support.annotation.IntDef;

import com.google.android.cameraview.model.AspectRatio;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @fileName CameraConfig
 * Created by YiangJone on 2018/6/27.
 * @describe
 */


public final class CameraConfig {

    public static final int MEDIA_QUALITY_LOWEST = 30;
    public static final int MEDIA_QUALITY_LOW = 40;
    public static final int MEDIA_QUALITY_MEDIUM = 50;
    public static final int MEDIA_QUALITY_HIGH = 80;
    public static final int MEDIA_QUALITY_HIGHEST = 100;

    public static final int MEDIA_ACTION_VIDEO = 100;
    public static final int MEDIA_ACTION_PHOTO = 101;

    public static final AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(16, 9);//如果是16:9的话显示图片的时候可以填充整个屏幕
    public static final AspectRatio SECOND_ASPECT_RATIO = AspectRatio.of(4, 3);//如果是4:3的话显示图片的时候会上下留黑很多空间

    public static final int FACING_BACK = 0;
    public static final int FACING_FRONT = 1;

    public static final int FLASH_OFF = 0;
    public static final int FLASH_ON = 1;
    public static final int FLASH_TORCH = 2;
    public static final int FLASH_AUTO = 3;
    public static final int FLASH_RED_EYE = 4;

    public static final int LANDSCAPE_90 = 90;
    public static final int LANDSCAPE_270 = 270;


    @IntDef({MEDIA_QUALITY_LOWEST, MEDIA_QUALITY_LOW, MEDIA_QUALITY_MEDIUM, MEDIA_QUALITY_HIGH, MEDIA_QUALITY_HIGHEST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaQuality {
    }

    @IntDef({MEDIA_ACTION_VIDEO, MEDIA_ACTION_PHOTO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaAction {
    }

    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlashMode {
    }
}
