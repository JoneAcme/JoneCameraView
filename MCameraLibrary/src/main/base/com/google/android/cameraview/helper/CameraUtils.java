package com.google.android.cameraview.helper;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.logs.CameraLog;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @fileName CameraUtils
 * Created by YiangJone on 2018/6/27.
 * @describe
 */


public class CameraUtils {
    private final static String TAG = "CameraUtils";
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static CamcorderProfile getCamcorderProfile(@CameraConfig.MediaQuality int mediaQuality, String cameraId) {
        if (TextUtils.isEmpty(cameraId)) {
            return null;
        }
        int cameraIdInt = Integer.parseInt(cameraId);
        return getCamcorderProfile(mediaQuality, cameraIdInt);
    }


    public static CamcorderProfile getCamcorderProfile(@CameraConfig.MediaQuality int mediaQuality, int cameraId) {
        if (Build.VERSION.SDK_INT > 10) {
            if (mediaQuality == CameraConfig.MEDIA_QUALITY_HIGHEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_HIGH) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
                } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
                }
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_MEDIUM) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
                }
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_LOW) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
                }
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_LOWEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            }
        } else {
            if (mediaQuality == CameraConfig.MEDIA_QUALITY_HIGHEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_HIGH) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_MEDIUM) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_LOW) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else if (mediaQuality == CameraConfig.MEDIA_QUALITY_LOWEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            }
        }
    }


    public static File getOutputMediaFile(Context context, @CameraConfig.MediaAction int mediaAction, @Nullable String pathToDirectory, @Nullable String fileName) {
        final File mediaStorageDir = generateStorageDir(context, pathToDirectory);
        File mediaFile = null;

        if (mediaStorageDir != null) {
            if (fileName == null) {
                final String timeStamp = simpleDateFormat.format(new Date());
                if (mediaAction == CameraConfig.MEDIA_ACTION_PHOTO) {
                    fileName = "IMG_" + timeStamp;
                } else if (mediaAction == CameraConfig.MEDIA_ACTION_VIDEO) {
                    fileName = "VID_" + timeStamp;
                }

            }
            final String mediaStorageDirPath = mediaStorageDir.getPath();
            if (mediaAction == CameraConfig.MEDIA_ACTION_PHOTO) {
                mediaFile = new File(mediaStorageDirPath + File.separator + fileName + ".jpg");
            } else if (mediaAction == CameraConfig.MEDIA_ACTION_VIDEO) {
                mediaFile = new File(mediaStorageDirPath + File.separator + fileName + ".mp4");
            }
        }

        return mediaFile;
    }

    public static File generateStorageDir(Context context, @Nullable String pathToDirectory) {
        File mediaStorageDir = null;
        if (pathToDirectory != null) {
            mediaStorageDir = new File(pathToDirectory);
        } else {
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), context.getPackageName());
        }

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Failed to create directory.");
                return null;
            }
        }

        return mediaStorageDir;
    }



    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     * <p>
     * This calculation is used for orienting the preview
     * <p>
     * Note: This is not the same calculation as the camera rotation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    public static int calcDisplayOrientation(Camera.CameraInfo mCameraInfo, int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    /**
     * Calculate camera rotation
     * <p>
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     * <p>
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    public static int calcCameraRotation(Camera.CameraInfo mCameraInfo,int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    /**
     * 根据屏幕旋转的度数来判断是否是横屏
     */
    public static boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == CameraConfig.LANDSCAPE_90 || orientationDegrees == CameraConfig.LANDSCAPE_270);
    }


    public static Bitmap rotationBitmap(byte[] data) {
        Bitmap bitmapPicture;
        int orientation = Exif.getOrientation(data);
        CameraLog.i(TAG, "takePictureInternal, orientation::::::"+orientation);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        switch (orientation) {
            case 90:
                bitmapPicture = rotateImage(bitmap, 90);

                break;
            case 180:
                bitmapPicture = rotateImage(bitmap, 180);

                break;
            case 270:
                bitmapPicture = rotateImage(bitmap, 270);

                break;

            default:
                bitmapPicture = bitmap;
                break;
        }
        return bitmapPicture;
    }


    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix,
                true);
    }

}
