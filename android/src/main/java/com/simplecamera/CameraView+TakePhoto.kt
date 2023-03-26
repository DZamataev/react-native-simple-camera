package com.simplecamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.ImageDecoder
import android.hardware.camera2.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
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


fun CameraView.getOutputDirectory(): String {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    "${Environment.DIRECTORY_DCIM}/SimpleCamera/"
  } else {
    "${context.getExternalFilesDir(Environment.DIRECTORY_DCIM)}/SimpleCamera/"
  }
}

@SuppressLint("UnsafeOptInUsageError")
fun CameraView.outputFileOptions(name: String, savePath: String): ImageCapture.OutputFileOptions {
  val camera2Info = Camera2CameraInfo.from(camera!!.cameraInfo)
  val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)

  // Setup image capture metadata
  val metadata = ImageCapture.Metadata().apply {
    // Mirror image when using the front camera
    isReversedHorizontal = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
  }
  MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  // Options fot the output image file
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val contentValues = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, name)
      put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
      put(MediaStore.MediaColumns.RELATIVE_PATH, savePath)
    }

    val contentResolver = context.contentResolver

    // Create the output uri
    val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    ImageCapture.OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
  } else {
    File(savePath).mkdirs()
    val file = File(savePath, "${name}.jpg")

    ImageCapture.OutputFileOptions.Builder(file)
  }.setMetadata(metadata).build()
}

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


  val map = Arguments.createMap()
  withContext(Dispatchers.IO) {
    val name = System.currentTimeMillis().toString()
    val savePath = getOutputDirectory()

    val pic = imageCapture!!.takePicture(outputFileOptions(name, savePath), takePhotoExecutor)
    val uri = pic.savedUri ?: throw java.lang.RuntimeException("errorTakePhoto")
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
      MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    map.putInt("width", bitmap.width)
    map.putInt("height", bitmap.height)
    map.putString("path", "${savePath}${name}.jpg")
  }

  Log.d(CameraView.TAG, "Finished taking photo!")

  val endFunc = System.nanoTime()
  Log.i(CameraView.TAG_PERF, "Finished function execution in ${(endFunc - startFunc) / 1_000_000}ms")
  return@coroutineScope map
}
