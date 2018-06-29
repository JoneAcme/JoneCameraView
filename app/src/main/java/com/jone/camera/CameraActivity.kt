package com.jone.camera

import android.Manifest
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import com.google.android.cameraview.CameraView
import com.google.android.cameraview.callback.CameraControlListener
import com.google.android.cameraview.callback.CameraVideoRecorderListener
import com.google.android.cameraview.compress.inter.CompressListener
import com.google.android.cameraview.configs.CameraViewOptions
import com.tbruyelle.rxpermissions2.RxPermissions


class CameraActivity : AppCompatActivity(), CameraControlListener, CameraVideoRecorderListener, CompressListener {


    private val CODE_REQUEST_CAMERA = 100
    private val TAG = "CameraActivity"

    private val FLASH_ICONS = intArrayOf(R.drawable.ic_flash_auto, R.drawable.ic_flash_off, R.drawable.ic_flash_on)

    private val FLASH_OPTIONS = intArrayOf(CameraView.FLASH_AUTO, CameraView.FLASH_OFF, CameraView.FLASH_ON)

    private var currentFlash = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnCamera.setOnClickListener {
            mCameraView.startVideoRecorder()
        }
        btnStop.setOnClickListener {
            mCameraView.stopVideoRecorder()
        }
        btnPicture.setOnClickListener {
            mCameraView.takePicture()
        }
        btnChooice.setOnClickListener {
            mCameraView.swithCamera()
        }
        ivFlash.setOnClickListener {
            currentFlash++
            ivFlash.setImageResource(FLASH_ICONS[currentFlash % 3])
            mCameraView.flash = FLASH_OPTIONS[currentFlash % 3]
        }
//        mCameraView.facing = (CameraView.FACING_FRONT)
        mCameraView.setControlListener(this)
        mCameraView.setRecorderListener(this)

        val viewOptions = CameraViewOptions.Builder(this).setCompressListener(this).create()
        mCameraView.setCameraOption(viewOptions)

    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    private fun startCamera() {

        RxPermissions(this).request(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        ).subscribe { t: Boolean ->
            if (t) {
                mCameraView.openCamera()
            } else {
                finish()
            }
        }

    }

    override fun onPause() {
        try {
            mCameraView.stopCamera()
        } catch (e: Exception) {
            Log.e(TAG, "stopCamera camera fail", e)
        }

        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (CODE_REQUEST_CAMERA == requestCode && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startCamera()
    }


    override fun onStartVideoRecorder() {
        Log.e(TAG, "onStartVideoRecorder")
    }

    override fun onCompleteVideoRecorder() {
        Log.e(TAG, "onCompleteVideoRecorder ")
    }

    override fun onCameraOpened(cameraView: CameraView) {
        Log.e(TAG, "onCameraOpened")
    }


    override fun onCameraClosed(cameraView: CameraView) {
        Log.e(TAG, "onCameraClosed")
    }

    override fun onStartCompress() {
        Log.e(TAG, "onStartCompress")
    }

    override fun onCompressFail() {
        Log.e(TAG, "onCompressFail")
    }

    override fun onCompressSuccess(action: Int,localPath:String, compressPath: String) {
        Log.e(TAG, "onCompressSuccess: localPath:$localPath    compressPath:$compressPath" )
    }

}
