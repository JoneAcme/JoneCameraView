package com.google.android.cameraview.compress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.cameraview.configs.CameraConfig;
import com.google.android.cameraview.configs.CameraViewOptions;
import com.google.android.cameraview.helper.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * @fileName CompressUtils
 * Created by YiangJone on 2018/6/28.
 * @describe
 */


public class CompressUtils {
    private static final String TAG = "CompressUtils";
    private static final Handler mHandler = new Handler(Looper.getMainLooper());


    public static void ansyPictrueCompress(final Context mContext, final Bitmap bitmap, final CameraViewOptions mCameraOption) {
        if (mContext == null || mCameraOption == null || bitmap == null) {
            if (null != mCameraOption && null != mCameraOption.getCompressListener())
                mCameraOption.getCompressListener().onCompressFail();
            return;
        }
        final String path = FileUtils.getLocalPath(mContext);

        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {

                String cachePath = FileUtils.getPictureCacheDirPath(mContext);

                //原图存储
                boolean normal = saveBitmap(bitmap, path, 100);
                if (mCameraOption.isCompress() && mCameraOption.getPictureCompress() != null) {
                    cachePath = mCameraOption.getPictureCompress().compress(mContext, bitmap, cachePath, mCameraOption.getQuality());

                    if (cachePath != null && !"".equals(cachePath)) {
                        e.onNext(cachePath);
                    } else {
                        e.onError(new Throwable("ansyPictrueCompress fail"));
                    }
                } else {
                    if (normal) {
                        e.onNext(path);
                    } else {
                        e.onError(new Throwable("ansyPictrueCompress fail"));
                    }
                }
                refreshSystemGallery(mContext, path);

            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.d(TAG, "ansyPictrueCompress onSubscribe");
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != mCameraOption.getCompressListener())
                                    mCameraOption.getCompressListener().onStartCompress();
                            }
                        });
                    }

                    @Override
                    public void onNext(String s) {
                        if (mCameraOption.getCompressListener() != null)
                            mCameraOption.getCompressListener().onCompressSuccess(CameraConfig.MEDIA_ACTION_PHOTO, path, s);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "ansyPictrueCompress onError:" + e.getMessage());
                        if (mCameraOption.getCompressListener() != null)
                            mCameraOption.getCompressListener().onCompressFail();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    public static boolean saveBitmap(Bitmap bitmap, String outPath, int quality) {
        try {
            FileOutputStream fos = new FileOutputStream(outPath);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            fos.write(bos.toByteArray());

            Log.d(TAG, "saveBitmap:" + outPath + "  size:" + FileUtils.getFileSize(outPath));
            Log.d(TAG, "saveBitmap:" + outPath + "  rotation:" + new ExifInterface(outPath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "saveBitmap ERROR!!:" + e.getMessage());
            e.printStackTrace();
            return false;
        }

    }

    public static void ansyVideoCompress(final Context mContext, final String localPath, final CameraViewOptions mCameraOption) {
        if (mContext == null || mCameraOption == null) {
            if (null != mCameraOption && null != mCameraOption.getCompressListener())
                mCameraOption.getCompressListener().onCompressFail();
            return;
        }

        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {

                String cachePath = FileUtils.getPictureCacheDirPath(mContext);


                Log.d(TAG, "VideoCompress local :" +
                        localPath +
                        "   size:" + FileUtils.getFileSize(localPath));

                if (mCameraOption.isCompress() && mCameraOption.getVideoCompress() != null) {
                    String compressPath = mCameraOption.getVideoCompress().compress(mContext, localPath, cachePath);
                    if (compressPath != null && !"".equals(compressPath)) {
                        e.onNext(compressPath);
                        Log.d(TAG, "VideoCompress compressPath :" +
                                compressPath +
                                "    size:" + FileUtils.getFileSize(compressPath));
                    } else {
                        e.onError(new Throwable("ansyVideoCompress fail"));
                    }
                } else {
                    e.onNext(localPath);
                }

                refreshSystemGallery(mContext, localPath);

            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.d(TAG, "ansyVideoCompress onSubscribe");
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != mCameraOption.getCompressListener())
                                    mCameraOption.getCompressListener().onStartCompress();
                            }
                        });
                    }

                    @Override
                    public void onNext(String s) {
                        if (mCameraOption.getCompressListener() != null)
                            mCameraOption.getCompressListener().onCompressSuccess(CameraConfig.MEDIA_ACTION_VIDEO, localPath, s);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "ansyVideoCompress onError:" + e.getMessage());
                        if (mCameraOption.getCompressListener() != null)
                            mCameraOption.getCompressListener().onCompressFail();
                    }

                    @Override
                    public void onComplete() {

                    }
                });

    }

    public static void refreshSystemGallery(Context mContext, String path) {
        try {
            MediaScannerConnection.scanFile(mContext, new String[]{path}, null, null);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}
