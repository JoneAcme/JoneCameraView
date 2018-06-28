package com.google.android.cameraview.configs;

import android.content.Context;

import com.google.android.cameraview.compress.impl.DefaultCompressListener;
import com.google.android.cameraview.compress.impl.DefaultPictureCompress;
import com.google.android.cameraview.compress.impl.DefaultVideoCompress;
import com.google.android.cameraview.compress.inter.CompressListener;
import com.google.android.cameraview.compress.inter.PictureCompress;
import com.google.android.cameraview.compress.inter.VideoCompress;
import com.google.android.cameraview.helper.FileUtils;


public class CameraViewOptions {
    private Context mContext;
    private boolean isCompress;
    private int quality = CameraConfig.MEDIA_QUALITY_MEDIUM;
    private CompressListener mCompressListener;
    private PictureCompress mPictureCompress;
    private VideoCompress mVideoCompress;

    private int videoFrameRate;
    private int videoEncodingBitRate;
    private int videoWidth;
    private int videoHeight;


    public boolean isCompress() {
        return isCompress;
    }

    public int getQuality() {
        return quality;
    }


    public CompressListener getCompressListener() {
        return mCompressListener;
    }

    public PictureCompress getPictureCompress() {
        return mPictureCompress;
    }

    public VideoCompress getVideoCompress() {
        return mVideoCompress;
    }


    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public int getVideoEncodingBitRate() {
        return videoEncodingBitRate;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }


    private CameraViewOptions(Builder builder) {
        mContext = builder.mContext;
        quality = builder.quality;
        isCompress = builder.isCompress;
        mCompressListener = builder.mCompressListener;
        mPictureCompress = builder.mPictureCompress;
        mVideoCompress = builder.mVideoCompress;

        videoFrameRate = builder.videoFrameRate;
        videoEncodingBitRate = builder.videoEncodingBitRate;
        videoWidth = builder.videoWidth;
        videoHeight = builder.videoHeight;

    }


    public static class Builder {
        //是否开启压缩
        private boolean isCompress = true;
        //质量
        private int quality = CameraConfig.MEDIA_QUALITY_MEDIUM;
        //压缩接口
        private PictureCompress mPictureCompress;
        private VideoCompress mVideoCompress;
        //压缩监听接口
        private CompressListener mCompressListener;
        private Context mContext;

        private int videoFrameRate = 25;
        private int videoEncodingBitRate = 900 * 1024;
        private int videoWidth = 1280;
        private int videoHeight = 720;


        public Builder(Context mContext) {
            this.mContext = mContext;

            mPictureCompress = new DefaultPictureCompress();
            mVideoCompress = new DefaultVideoCompress();

            mCompressListener = new DefaultCompressListener();
        }

        public Builder setPictureCompress(PictureCompress mPictureCompress) {
            this.mPictureCompress = mPictureCompress;
            return this;
        }

        public Builder setVideoCompress(VideoCompress mVideoCompress) {
            this.mVideoCompress = mVideoCompress;
            return this;
        }

        public Builder setCompress(boolean compress) {
            isCompress = compress;
            return this;
        }

        public Builder setQuality(@CameraConfig.MediaQuality int quality) {
            this.quality = quality;
            return this;
        }


        public Builder setCompressListener(CompressListener mCompressListener) {
            this.mCompressListener = mCompressListener;
            return this;
        }

        public Builder setVideoFrameRate(int videoFrameRate) {
            this.videoFrameRate = videoFrameRate;
            return this;
        }

        public Builder setVideoEncodingBitRate(int videoEncodingBitRate) {
            this.videoEncodingBitRate = videoEncodingBitRate;
            return this;
        }

        public Builder setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
            return this;
        }

        public Builder setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
            return this;
        }


        public CameraViewOptions create() {
            return new CameraViewOptions(this);
        }

    }


}
