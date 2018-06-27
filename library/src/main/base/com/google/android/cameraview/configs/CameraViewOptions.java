package com.google.android.cameraview.configs;

/**
 * @fileName CameraViewOptions
 * Created by YiangJone on 2018/6/27.
 * @describe
 */


public class CameraViewOptions {
    private String outFile;
    private boolean isCompress;
    private int quality;

    private CameraViewOptions(Builder builder) {
        this.outFile = builder.outFile;
        this.isCompress = builder.isCompress;
        this.quality = builder.quality;
    }


    public static class Builder {
        private String outFile;
        private boolean isCompress;
        private int quality;


        public void setOutFile(String outFile) {
            this.outFile = outFile;
        }

        public void setCompress(boolean compress) {
            isCompress = compress;
        }

        public void setQuality(@CameraConfig.MediaQuality int quality) {
            this.quality = quality;
        }
    }
}
