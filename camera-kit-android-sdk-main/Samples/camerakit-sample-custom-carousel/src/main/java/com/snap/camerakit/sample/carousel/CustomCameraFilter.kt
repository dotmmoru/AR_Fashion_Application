package com.snap.camerakit.sample.carousel

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
class CustomCameraFilter: CameraFilter {
    companion object {
        private const val TAG = "CustomCameraFilter"
    }
    @SuppressLint("RestrictedApi")
    override fun filter(cameraInfos: MutableList<CameraInfo>): MutableList<CameraInfo> {
        val cameraIterator: MutableIterator<CameraInfo> = cameraInfos.iterator()
        var camera: CameraInfo? = null
        while (cameraIterator.hasNext()) {
            camera = cameraIterator.next()
            val getImplementationType: String = camera.implementationType
            Log.i(TAG, "getImplementationType: $getImplementationType")
        }
        val linkedHashSet: MutableList<CameraInfo> = mutableListOf()

        if (camera != null) {
            linkedHashSet.add(camera)
        }
        return linkedHashSet
    }
}