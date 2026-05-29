package com.example.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class RecoveryCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "RecoveryCameraManager"
    }

    fun captureSnapshot(
        lifecycleOwner: LifecycleOwner,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Configure image capture options
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Default to FRONT camera for recovery evidence (capturing current unauthorized holder)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Unbind previous bindings
                cameraProvider.unbindAll()

                // Bind to lifecycle
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )

                // Define output file
                val outputDir = context.cacheDir
                val filePrefix = "evidence_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}"
                val outputFile = File.createTempFile(filePrefix, ".jpg", outputDir)

                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "Successfully saved snapshot to ${outputFile.absolutePath}")
                            onSuccess(outputFile)
                            // Unbind camera once picture is taken
                            cameraProvider.unbindAll()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture failure: ${exception.message}", exception)
                            onError(exception)
                            cameraProvider.unbindAll()
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed: ${e.message}", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
