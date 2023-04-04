package com.simplecamera

import android.annotation.SuppressLint
import android.hardware.camera2.*
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.simplecamera.utils.save
import com.simplecamera.utils.takePicture
import kotlinx.coroutines.*
import java.io.File
import kotlin.system.measureTimeMillis

@SuppressLint("UnsafeOptInUsageError")
suspend fun CameraView.takePhoto(options: ReadableMap): WritableMap = coroutineScope {
  val startFunc = System.nanoTime()
  Log.i(CameraView.TAG, "takePhoto() called")
  if (imageCapture == null) {
    if (photo == true) {
      throw CameraNotReadyError()
    } else {
      throw PhotoNotEnabledError()
    }
  }

  if (options.hasKey("flash")) {
    val flashMode = options.getString("flash")
    imageCapture!!.flashMode = when (flashMode) {
      "on" -> ImageCapture.FLASH_MODE_ON
      "off" -> ImageCapture.FLASH_MODE_OFF
      "auto" -> ImageCapture.FLASH_MODE_AUTO
      else -> throw InvalidTypeScriptUnionError("flash", flashMode ?: "(null)")
    }
  }

  val camera2Info = Camera2CameraInfo.from(camera!!.cameraInfo)
  val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)

  val results = awaitAll(
    async(coroutineContext) {
      Log.d(CameraView.TAG, "Taking picture...")
      val startCapture = System.nanoTime()
      val pic = imageCapture!!.takePicture(takePhotoExecutor)
      val endCapture = System.nanoTime()
      Log.i(CameraView.TAG_PERF, "Finished image capture in ${(endCapture - startCapture) / 1_000_000}ms")
      pic
    },
    async(Dispatchers.IO) {
      Log.d(CameraView.TAG, "Creating temp file...")
      File.createTempFile("mrousavy", ".jpg", context.cacheDir).apply { deleteOnExit() }
    }
  )
  val photo = results.first { it is ImageProxy } as ImageProxy
  val file = results.first { it is File } as File

  @Suppress("BlockingMethodInNonBlockingContext")
  withContext(Dispatchers.IO) {
    Log.d(CameraView.TAG, "Saving picture to ${file.absolutePath}...")
    val milliseconds = measureTimeMillis {
      val flipHorizontally = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
      photo.save(file, flipHorizontally)
    }
    Log.i(CameraView.TAG_PERF, "Finished image saving in ${milliseconds}ms")
  }

  val map = Arguments.createMap()
  map.putString("path", file.absolutePath)
  map.putInt("width", photo.width)
  map.putInt("height", photo.height)

  photo.close()

  Log.d(CameraView.TAG, "Finished taking photo!")

  val endFunc = System.nanoTime()
  Log.i(CameraView.TAG_PERF, "Finished function execution in ${(endFunc - startFunc) / 1_000_000}ms")
  return@coroutineScope map
}
