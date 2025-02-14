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

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var takePhotoButton: Button

    // Для фотографування
    private lateinit var imageCapture: ImageCapture

    // Потік, на якому оброблятиметься робота з камерою
    private lateinit var cameraExecutor: ExecutorService

    // Контракт для запиту дозволу на камеру
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Якщо користувач надав дозвіл, стартуємо камеру
                startCamera()
            } else {
                // Дозвіл відхилено — можна показати тост, діалог тощо
                Log.e("CameraXApp", "Користувач відхилив дозвіл на камеру")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ініціалізація в'ю
        cameraPreview = findViewById(R.id.cameraPreview)
        takePhotoButton = findViewById(R.id.takePhotoButton)

        // Ініціалізація Executor для потокових операцій
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Перевіряємо дозвіл на камеру та, якщо дано, запускаємо камеру
        checkPermissionAndStartCamera()

        takePhotoButton.setOnClickListener {
            // Створюємо файл, куди будемо зберігати
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
                        // Коли фото збережено – показуємо Toast
                        Toast.makeText(
                            this@MainActivity,
                            "Photo taken and saved:\n${photoFile.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()

                        // Тут можна також вивести логику, наприклад, відкрити файл чи показати його в ImageView
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

    // Перевіряємо дозвіл
    private fun checkPermissionAndStartCamera() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            // Якщо вже є дозвіл, запускаємо камеру
            startCamera()
        } else {
            // Запитуємо дозвіл
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Запускаємо CameraX, щоб показати live-preview у PreviewView
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Отримуємо CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            // Створюємо об'єкт Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            // Налаштовуємо ImageCapture
            imageCapture = ImageCapture.Builder().build()

            // Вибираємо, яку камеру використовувати: задню (default) або передню
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Перед прив'язкою вивільняємо попередні зв'язки
            cameraProvider.unbindAll()

            try {
                // Прив'язуємо наш preview та imageCapture до життєвого циклу Activity
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
        // Завершуємо Executor
        cameraExecutor.shutdown()
    }
}
