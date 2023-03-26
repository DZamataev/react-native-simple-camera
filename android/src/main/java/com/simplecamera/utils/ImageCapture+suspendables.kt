package com.simplecamera.utils

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend inline fun ImageCapture.takePicture(options: ImageCapture.OutputFileOptions, executor: Executor) = suspendCoroutine<ImageCapture.OutputFileResults> { cont ->
  this.takePicture(
    options, executor,
    object : ImageCapture.OnImageSavedCallback {
      override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        cont.resume(outputFileResults)
      }

      override fun onError(exception: ImageCaptureException) {
        cont.resumeWithException(exception)
      }
    }
  )
}
