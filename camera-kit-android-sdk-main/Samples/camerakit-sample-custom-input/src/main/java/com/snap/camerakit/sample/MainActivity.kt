package com.snap.camerakit.sample

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Size
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.GestureDetector
import android.view.MotionEvent
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
import android.animation.ValueAnimator
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
import android.widget.Button
import android.widget.ImageButton
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

private const val DEFAULT_IMAGE_INPUT_FIELD_OF_VIEW = 50F

class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var session: Session
    private lateinit var inputSurface: Surface
    private lateinit var inputSurfaceUpdateCallback: Choreographer.FrameCallback

    private lateinit var cameraPreview: PreviewView

    private lateinit var inputSurfaceTexture: SurfaceTexture
    private lateinit var cameraExecutor: ExecutorService

    private val choreographer = Choreographer.getInstance()
    private val closeOnDestroy = mutableListOf<Closeable>()

    private lateinit var buttonLens1: ImageButton
    private lateinit var buttonLens2: ImageButton
    private lateinit var buttonLens3: ImageButton

    private var selectedButton: ImageButton? = null

    private val lensIds = listOf(LENS_ID_1, LENS_ID_2, LENS_ID_3)
    private var currentLensIndex = 0

    companion object {
        const val TOKEN = "eyJhbGciOiJIUzI1NiIsImtpZCI6IkNhbnZhc1MyU0hNQUNQcm9kIiwidHlwIjoiSldUIn0.eyJhdWQiOiJjYW52YXMtY2FudmFzYXBpIiwiaXNzIjoiY2FudmFzLXMyc3Rva2VuIiwibmJmIjoxNzM5NzA2OTc2LCJzdWIiOiI3MzZkMzM5Yi03NGIyLTRkMjgtYWQ3NS0zYWExMDc2YzI1YzJ-U1RBR0lOR342YTdmOTA2Zi0zYWJjLTRmMWItYjFkYi02OWM0Y2I2ZWIxMDMifQ.YWxF9attURAA0psdgFdeLLAtaqdJPNQM41rpSb2e51Y"
        const val LENS_GROUP_ID = "ed8aeaf7-12fc-4631-893c-de7705b119fc"
        const val LENS_ID_1 = "b93ca434-dce5-46ae-9c26-3e55d7a745e9"
        const val LENS_ID_2 = "c2cd19d9-1592-4297-9322-c1dcae6094da"
        const val LENS_ID_3 = "84cbe1b1-5864-4e7d-b47f-edfdb155e98d"
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Log.e("CameraXApp", "User denied camera permission")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize buttons
        buttonLens1 = findViewById<ImageButton>(R.id.button_lens1)
        buttonLens2 = findViewById<ImageButton>(R.id.button_lens2)
        buttonLens3 = findViewById<ImageButton>(R.id.button_lens3)

        // Set click listeners
        buttonLens1.setOnClickListener { onLensButtonClick(buttonLens1, lensIds[0]) }
        buttonLens2.setOnClickListener { onLensButtonClick(buttonLens2, lensIds[1]) }
        buttonLens3.setOnClickListener { onLensButtonClick(buttonLens3, lensIds[2]) }
        session = Session(context = this).apply {
            applyLens(lensIds[currentLensIndex])
        }

        val inputWidth = 1440
        val inputHeight = 2560

        inputSurfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(inputWidth, inputHeight)
            detachFromGLContext()
        }

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

        inputSurface = Surface(inputSurfaceTexture)

        val previewTextureView = findViewById<TextureView>(R.id.camerakit_output_preview)
        val outputCloseable = session.processor.connectOutput(previewTextureView)
        closeOnDestroy.add(outputCloseable)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Mark the first button as selected initially
        onLensButtonClick(buttonLens1, lensIds[0])
    }

    private fun Session.applyLens(lensId: String) {
        lenses.repository.observe(
            LensesComponent.Repository.QueryCriteria.ById(lensId, LENS_GROUP_ID)
        ) { result ->
            result.whenHasFirst { requestedLens ->
                lenses.processor.apply(requestedLens)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeOnDestroy.forEach { it.close() }
        cameraExecutor.shutdown()
        session.close()
        inputSurface.release()
        inputSurfaceTexture.release()
    }

    private fun onLensButtonClick(button: ImageButton, lensId: String) {
        // Reset the size of the previously selected button
        selectedButton?.apply {
            animateButtonSize(this, resources.getDimensionPixelSize(R.dimen.button_size))
        }

        // Increase the size of the clicked button
        animateButtonSize(button, resources.getDimensionPixelSize(R.dimen.button_size_selected))

        // Apply the selected lens
        session.applyLens(lensId)

        // Update the selected button reference
        selectedButton = button
    }

    private fun animateButtonSize(button: ImageButton, targetSize: Int) {
        val startSize = button.width
        val animator = ValueAnimator.ofInt(startSize, targetSize)
        animator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Int
            button.layoutParams.width = animatedValue
            button.layoutParams.height = animatedValue
            button.requestLayout()
        }
        animator.duration = 200 // Animation duration in milliseconds
        animator.start()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            preview.setSurfaceProvider { request ->
                val resolution: Size = request.resolution
                inputSurfaceTexture.setDefaultBufferSize(resolution.width, resolution.height)
                request.provideSurface(inputSurface, ContextCompat.getMainExecutor(this)) { result -> }
            }

            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    val externalCamera = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
                    }
                        ?: cameraInfos.find { cameraInfo ->
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                            lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                        }
                        ?: cameraInfos.find { cameraInfo ->
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