package com.snap.camerakit.sample

import android.graphics.BitmapFactory
import android.graphics.RectF
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
import com.snap.camerakit.lenses.LENS_GROUP_ID_BUNDLED
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.whenHasFirst
import java.io.Closeable
import kotlin.math.min

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
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

        // Basic CameraKit Session use case to apply a first bundled lens that is available.
        session = Session(this).apply {
            lenses.repository.get(LensesComponent.Repository.QueryCriteria.Available(LENS_GROUP_ID_BUNDLED)) { result ->
                result.whenHasFirst { lens ->
                    lenses.processor.apply(lens)
                }
            }
        }

        // Визначаємо початковий розмір вхідного потоку.
        // (За потреби цей розмір можна змінити відповідно до роздільної здатності камери)
        val inputWidth = 1440
        val inputHeight = 2560

        // Створюємо SurfaceTexture і налаштовуємо його
        inputSurfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(inputWidth, inputHeight)
            // Обов'язково від'єднуємо від GL-контексту перед використанням як вхід для CameraKit
            detachFromGLContext()
        }

        // Створюємо вхід для CameraKit із SurfaceTexture
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

        // Створюємо Surface, який передамо у CameraX (ця ж поверхня пов'язана з inputSurfaceTexture)
        inputSurface = Surface(inputSurfaceTexture)

        // Підключаємо вихід CameraKit до TextureView для візуалізації обробленого відеопотоку
        val previewTextureView = findViewById<TextureView>(R.id.camerakit_output_preview)
        val outputCloseable = session.processor.connectOutput(previewTextureView)
        closeOnDestroy.add(outputCloseable)

        // Ініціалізуємо Executor для камери
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Перевірка наявності дозволу на використання камери
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            // Запит дозволу
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Закриваємо всі ресурси, що були відкриті
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

            // Створюємо Preview use case
            val preview = Preview.Builder().build()

            // Використовуємо власний SurfaceProvider для передачі inputSurface
            preview.setSurfaceProvider { request ->
                // Оновлюємо розмір буфера SurfaceTexture згідно з вимогами CameraX
                val resolution: Size = request.resolution
                inputSurfaceTexture.setDefaultBufferSize(resolution.width, resolution.height)

                // Передаємо існуючу поверхню в запит
                request.provideSurface(inputSurface, ContextCompat.getMainExecutor(this)) { result ->
                    // Результат можна обробити за потреби
                }
            }

            // Формуємо CameraSelector за аналогією з кодом з другого скрипта
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    // Спроба знайти зовнішню камеру
                    val externalCamera = cameraInfos.find { cameraInfo ->
                        val camera2Info = Camera2CameraInfo.from(cameraInfo)
                        val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
                    }
                        ?: cameraInfos.find { cameraInfo ->
                            // Якщо зовнішньої немає – використовуємо задню камеру
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                            lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                        }
                        ?: cameraInfos.find { cameraInfo ->
                            // Якщо задньої немає – пробуємо передню камеру
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
