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
  PictureCompress | 图片压缩接口 |  setControlListener
  VideoCompress |  视频压缩接口 |  setRecorderListener
  CompressListener |  压缩进度接口 |  setRecorderListener


    最后需要在开启相机前，调用CameraView.setCameraOption

