package com.google.android.cameraview.helper;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @fileName FileUtils
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final String VideoCacheFileName = "/video";
    private static final String PictureCacheFileName = "/picture";
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private  static final String DIRECTORY_NAME = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Camera";


    public static String getLocalPath(Context mContext) {
        return getOutputMediaFile(mContext, CameraConfig.MEDIA_ACTION_PHOTO, DIRECTORY_NAME, null).getAbsolutePath();
    }
    public static String getVideoLocalPath(Context mContext) {
        return getOutputMediaFile(mContext, CameraConfig.MEDIA_ACTION_VIDEO, DIRECTORY_NAME, null).getAbsolutePath();
    }
    public static String getVideoCacheDirPath(Context mContext) {
        return getOutputMediaFile(mContext, CameraConfig.MEDIA_ACTION_VIDEO, mContext.getCacheDir() + VideoCacheFileName, null).getAbsolutePath();
    }

    public static String getPictureCacheDirPath(Context mContext) {
        return getOutputMediaFile(mContext, CameraConfig.MEDIA_ACTION_PHOTO, mContext.getCacheDir() + PictureCacheFileName, null).getAbsolutePath();
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

    public static long getFileSize(File file) throws Exception {
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        } else {
            file.createNewFile();
            Log.e("获取文件大小", "文件不存在!");
        }
        return size;
    }

    public static String getFileSize(String filePath) {

        try {
            String fileSizeString = "";
            DecimalFormat df = new DecimalFormat("#.00");
            long fileS = getFileSize(new File(filePath));
            if (fileS < 1024) {
                fileSizeString = df.format((double) fileS) + "B";
            } else if (fileS < 1048576) {
                fileSizeString = df.format((double) fileS / 1024) + "KB";
            } else if (fileS < 1073741824) {
                fileSizeString = df.format((double) fileS / 1048576) + "MB";
            } else {
                fileSizeString = df.format((double) fileS / 1073741824) + "GB";
            }
            return fileSizeString;
        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }
    }

    public static void deleteFile(String filePath) {
        File file = new File(filePath);
        if(file.exists()) file.deleteOnExit();
    }
}
