# JoneCameraView

##自定义Camera
    参考 [https://github.com/hujiaweibujidao/CameraView](https://github.com/hujiaweibujidao/CameraView)
    对该项目的二次开发，
    添加 拍照、录像功能，
    并添加自定义压缩、视频录制参数、完成回调等接口


## CameraView 中的接口
name | description | use
---- | --- | ---
CameraControlListener | 监听相机开关的接口 |  mCameraView.setControlListener
CameraVideoRecorderListener |  监听录像开始、结束的接口 |  mCameraView.setRecorderListener

## 其他参数

### CameraViewOptions
    包含是否开启压缩，图片压缩接口，视频压缩接口，压缩进度接口。可自行实现接口。

    默认实现：
         图片压缩质量取值为 CameraViewOptions 的quality
         视频压缩 暂无，返回原视频地址


## CameraViewOptions.Builder 中的接口
  name | description | use
  ---- | --- | ---
  PictureCompress | 图片压缩接口 |  setPictureCompress
  VideoCompress |  视频压缩接口 |  setVideoCompress
  CompressListener |  压缩进度接口 |  setCompressListener


    最后需要在开启相机前，调用CameraView.setCameraOption

## 返回方式

    返回值一律通过 CompressListener 中的 onCompressSuccess 返回：

    onCompressSuccess(@CameraConfig.MediaAction int action,String localPath,String compressPath)

    param | description
      ---- | ---
      action | 拍照/录像 状态区分
      localPath | 未经压缩处理过的本地地址
      compressPath | 压缩后的地址


    action 的取值:

      action | description
      ---- | ---
      CameraConfig.MEDIA_ACTION_VIDEO | 录像返回
      CameraConfig.MEDIA_ACTION_PHOTO | 拍照返回
