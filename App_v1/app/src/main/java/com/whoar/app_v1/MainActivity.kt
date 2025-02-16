package com.whoar.app_v1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.Toast
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var takePhotoButton: Button

    // For taking a photo
    private lateinit var imageCapture: ImageCapture

    // The thread on which camera work will be processed
    private lateinit var cameraExecutor: ExecutorService

    // Contract for requesting camera permission
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // If the user granted permission, start the camera
                startCamera()
            } else {
                // Permission denied — you can show a toast, dialog, etc.
                Log.e("CameraXApp", "User denied camera permission")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View initialization
        cameraPreview = findViewById(R.id.cameraPreview)
        takePhotoButton = findViewById(R.id.takePhotoButton)

        // Initialize the Executor for background operations
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check for camera permission, and if granted, start the camera
        checkPermissionAndStartCamera()

        takePhotoButton.setOnClickListener {
            // Create a file to store the photo
            val photoFile = File(
                externalMediaDirs.firstOrNull(),
                "photo_${System.currentTimeMillis()}.jpg"
            )
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        // When the photo is saved, show a Toast
                        Toast.makeText(
                            this@MainActivity,
                            "Photo taken and saved:\n${photoFile.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()

                        // You can also add logic here, for example, open the file or display it in an ImageView
                        Log.d("CameraXApp", "Photo saved: ${photoFile.absolutePath}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to take photo: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("CameraXApp", "Photo capture failed: ${exception.message}", exception)
                    }
                }
            )
        }

    }

    // Check the permission
    private fun checkPermissionAndStartCamera() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            // If permission is already granted, start the camera
            startCamera()
        } else {
            // Request permission
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Start CameraX to show a live preview in PreviewView
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Get the CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            // Create a Preview object
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            // Configure ImageCapture
            imageCapture = ImageCapture.Builder().build()

            // Create a custom selector
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->

                    // Try to find an external camera
                    val externalCameraInfo = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
                    }
                    if (externalCameraInfo != null) {
                        return@addCameraFilter listOf(externalCameraInfo)
                    }

                    // If there's no external camera — look for a back camera
                    val backCameraInfo = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                    }
                    if (backCameraInfo != null) {
                        return@addCameraFilter listOf(backCameraInfo)
                    }

                    // If there's no back camera — look for a front camera
                    val frontCameraInfo = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_BACK
                    }
                    if (frontCameraInfo != null) {
                        return@addCameraFilter listOf(frontCameraInfo)
                    }

                    // If there are no cameras at all
                    emptyList()
                }
                .build()

            // Unbind previous use cases before rebinding
            cameraProvider.unbindAll()

            try {
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraXApp", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down the Executor
        cameraExecutor.shutdown()
    }
}
