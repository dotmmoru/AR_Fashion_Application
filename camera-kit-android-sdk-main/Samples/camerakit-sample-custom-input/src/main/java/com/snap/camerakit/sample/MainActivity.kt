package com.snap.camerakit.sample

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Size
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.snap.camerakit.Session
import com.snap.camerakit.connectOutput
import com.snap.camerakit.inputFrom
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.whenHasFirst
import java.io.Closeable

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop


private const val DEFAULT_IMAGE_INPUT_FIELD_OF_VIEW = 50F

/**
 * A simple activity that demonstrates how to create a custom [com.snap.camerakit.ImageProcessor.Input] based on a
 * [SurfaceTexture] which is filled with contents from an image. In reality, CameraKit expects an input source to be a
 * proper realtime camera stream however the contrived use of an image here is provided solely as a simplified example.
 */
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var session: Session
    private lateinit var inputSurface: Surface
    private lateinit var inputSurfaceUpdateCallback: Choreographer.FrameCallback

    private lateinit var cameraPreview: PreviewView

    private lateinit var inputSurfaceTexture: SurfaceTexture
    private lateinit var cameraExecutor: ExecutorService

    private val choreographer = Choreographer.getInstance()
    private val closeOnDestroy = mutableListOf<Closeable>()

    companion object {
        const val TOKEN = "eyJhbGciOiJIUzI1NiIsImtpZCI6IkNhbnZhc1MyU0hNQUNQcm9kIiwidHlwIjoiSldUIn0.eyJhdWQiOiJjYW52YXMtY2FudmFzYXBpIiwiaXNzIjoiY2FudmFzLXMyc3Rva2VuIiwibmJmIjoxNzM5NzA2OTc2LCJzdWIiOiI3MzZkMzM5Yi03NGIyLTRkMjgtYWQ3NS0zYWExMDc2YzI1YzJ-U1RBR0lOR342YTdmOTA2Zi0zYWJjLTRmMWItYjFkYi02OWM0Y2I2ZWIxMDMifQ.YWxF9attURAA0psdgFdeLLAtaqdJPNQM41rpSb2e51Y"
        const val LENS_GROUP_ID = "ed8aeaf7-12fc-4631-893c-de7705b119fc"
        const val LENS_ID_1 = "b93ca434-dce5-46ae-9c26-3e55d7a745e9"
        const val LENS_ID_2 = "6c8ee7b5-6dff-4d59-9ce0-ea212b4871e7"
        const val LENS_ID_3 = "84cbe1b1-5864-4e7d-b47f-edfdb155e98d"
    }

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

        session = Session(context = this) .apply {
            lenses.repository.observe(
                LensesComponent.Repository.QueryCriteria.ById(LENS_ID_1, LENS_GROUP_ID)
            ) { result ->
                result.whenHasFirst { requestedLens ->
                    lenses.processor.apply(requestedLens)
                }
            }
        }

// Determine the initial size of the input stream.
// (This size can be changed to suit the camera resolution if necessary)
        val inputWidth = 1440
        val inputHeight = 2560

        // Create a SurfaceTexture and configure it
        inputSurfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(inputWidth, inputHeight)
            // Be sure to detach from the GL context before using as input for CameraKit
            detachFromGLContext()
        }

        // Creating an input for CameraKit with SurfaceTexture
        val input = inputFrom(
            surfaceTexture = inputSurfaceTexture,
            width = inputWidth,
            height = inputHeight,
            facingFront = true,
            rotationDegrees = 0,
            horizontalFieldOfView = 50F,
            verticalFieldOfView = 50F
        )
        session.processor.connectInput(input)

        // We create a Surface, which we pass to CameraX (this same surface is associated with inputSurfaceTexture)
        inputSurface = Surface(inputSurfaceTexture)

        // Connect the CameraKit output to TextureView to visualize the processed video stream
        val previewTextureView = findViewById<TextureView>(R.id.camerakit_output_preview)
        val outputCloseable = session.processor.connectOutput(previewTextureView)
        closeOnDestroy.add(outputCloseable)

        // Initializing the Executor for the camera
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Checking if you have permission to use the camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            // Ask request
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Close all resources that were open
        closeOnDestroy.forEach { it.close() }
        cameraExecutor.shutdown()
        session.close()
        inputSurface.release()
        inputSurfaceTexture.release()
    }


    @OptIn(ExperimentalCamera2Interop::class) private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Create Preview use case
            val preview = Preview.Builder().build()

            // use SurfaceProvider to share inputSurface
            preview.setSurfaceProvider { request ->
                // Update buffer size SurfaceTexture according to the requirements of CameraX
                val resolution: Size = request.resolution
                inputSurfaceTexture.setDefaultBufferSize(resolution.width, resolution.height)

                // Passing the existing surface into the request
                request.provideSurface(inputSurface, ContextCompat.getMainExecutor(this)) { result ->
                    // The result can be processed as needed.
                }
            }

            // We form the CameraSelector by analogy with the code from the second script
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    // Trying to find an external camera
                    val externalCamera = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
                    }
                        ?: cameraInfos.find { cameraInfo ->
                            //If there is no external camera, use the rear camera.
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                            lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                        }
                        ?: cameraInfos.find { cameraInfo ->
                            // If there is no rear camera, try the front camera.
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                            lensFacing == CameraCharacteristics.LENS_FACING_BACK
                        }
                    if (externalCamera != null) listOf(externalCamera) else emptyList()
                }
                .build()

            cameraProvider.unbindAll()
            try {
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
