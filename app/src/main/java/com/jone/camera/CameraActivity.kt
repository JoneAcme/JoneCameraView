package com.jone.camera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import android.os.Build

fun Activity.hasPermission(permission: String, requestCode: Int): Boolean {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val hasPermission = checkSelfPermission(permission)
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            return false
        } else {
            return true
        }
    }
    return true
}

class CameraActivity : AppCompatActivity() {

    private val CODE_REQUEST_CAMERA = 100
    private val TAG = "CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnCamera.setOnClickListener {
            mCameraView.startVideoRecorder()
        }
        btnStop.setOnClickListener {
            mCameraView.stopVideoRecorder()
        }
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    private fun startCamera() {
        val hasPermission = hasPermission(Manifest.permission.CAMERA, CODE_REQUEST_CAMERA)
        if (hasPermission) {
            try {
                mCameraView.start()
            } catch (e: Exception) {
                Log.e(TAG, "start camera fail", e)
            }
        }
    }

    override fun onPause() {
        try {
            mCameraView.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop camera fail", e)
        }

        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (CODE_REQUEST_CAMERA == requestCode && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startCamera()
    }

}
