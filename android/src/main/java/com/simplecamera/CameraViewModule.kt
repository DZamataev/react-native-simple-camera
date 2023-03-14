package com.simplecamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.facebook.react.uimanager.UIManagerHelper
import com.simplecamera.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.util.*


@ReactModule(name = CameraViewModule.TAG)
@Suppress("unused")
class CameraViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  companion object {
    const val TAG = "CameraView"
    var RequestCode = 10

    fun parsePermissionStatus(status: Int): String {
      return when (status) {
        PackageManager.PERMISSION_DENIED -> "denied"
        PackageManager.PERMISSION_GRANTED -> "authorized"
        else -> "not-determined"
      }
    }
  }


  private val coroutineScope = CoroutineScope(Dispatchers.Main) // TODO: or Dispatchers.Main?

  private fun cleanup() {
    if (coroutineScope.isActive) {
      coroutineScope.cancel("CameraViewModule has been destroyed.")
    }
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    cleanup()
  }

  override fun invalidate() {
    super.invalidate()
    cleanup()
  }

  override fun getName(): String {
    return TAG
  }

  private fun findCameraView(viewId: Int): CameraView {
    Log.d(TAG, "Finding view $viewId...")
    val view = if (reactApplicationContext != null) UIManagerHelper.getUIManager(reactApplicationContext, viewId)?.resolveView(viewId) as CameraView? else null
    Log.d(TAG,  if (reactApplicationContext != null) "Found view $viewId!" else "Couldn't find view $viewId!")
    return view ?: throw ViewNotFoundError(viewId)
  }

  @ReactMethod
  fun takePhoto(viewTag: Int, options: ReadableMap, promise: Promise) {
    coroutineScope.launch {
      withPromise(promise) {
        val view = findCameraView(viewTag)
        view.takePhoto(options)
      }
    }
  }

  @ReactMethod
  fun startRecording(viewTag: Int, options: ReadableMap, onRecordCallback: Callback) {
    coroutineScope.launch {
      val view = findCameraView(viewTag)
      try {
        view.startRecording(options, onRecordCallback)
      } catch (error: CameraError) {
        val map = makeErrorMap("${error.domain}/${error.id}", error.message, error)
        onRecordCallback(null, map)
      } catch (error: Throwable) {
        val map = makeErrorMap("capture/unknown", "An unknown error occurred while trying to start a video recording!", error)
        onRecordCallback(null, map)
      }
    }
  }

  @ReactMethod
  fun pauseRecording(viewTag: Int, promise: Promise) {
    withPromise(promise) {
      val view = findCameraView(viewTag)
      view.pauseRecording()
      return@withPromise null
    }
  }

  @ReactMethod
  fun resumeRecording(viewTag: Int, promise: Promise) {
    withPromise(promise) {
      val view = findCameraView(viewTag)
      view.resumeRecording()
      return@withPromise null
    }
  }

  @ReactMethod
  fun stopRecording(viewTag: Int, promise: Promise) {
    coroutineScope.launch {
      withPromise(promise) {
        val view = findCameraView(viewTag)
        view.stopRecording()
        return@withPromise null
      }
    }
  }

  @ReactMethod
  fun focus(viewTag: Int, point: ReadableMap, promise: Promise) {
    coroutineScope.launch {
      withPromise(promise) {
        val view = findCameraView(viewTag)
        view.focus(point)
        return@withPromise null
      }
    }
  }

  // TODO: This uses the Camera2 API to list all characteristics of a camera device and therefore doesn't work with Camera1. Find a way to use CameraX for this
  // https://issuetracker.google.com/issues/179925896
  @ReactMethod
  fun getAvailableCameraDevices(promise: Promise) {
    val startTime = System.currentTimeMillis()
    coroutineScope.launch {
      withPromise(promise) {
        val cameraProvider = ProcessCameraProvider.getInstance(reactApplicationContext).await()
        val extensionsManager = ExtensionsManager.getInstanceAsync(reactApplicationContext, cameraProvider).await()

        val manager = reactApplicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
          ?: throw CameraManagerUnavailableError()

        val cameraDevices: WritableArray = Arguments.createArray()

        manager.cameraIdList.forEach loop@{ id ->
          val cameraSelector = CameraSelector.Builder().byID(id).build()

          val characteristics = manager.getCameraCharacteristics(id)
          val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!

          val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
          val isMultiCam = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
          val deviceTypes = characteristics.getDeviceTypes()

          val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)!!
          val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!
          val maxScalerZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
          val supportsDepthCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
          val supportsRawCapture = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
          val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
          else null
          val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            characteristics.get(CameraCharacteristics.INFO_VERSION)
          else null
          val supportsLowLightBoost = extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.NIGHT)
          // see https://developer.android.com/reference/android/hardware/camera2/CameraDevice#regular-capture
          val supportsParallelVideoProcessing = hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY && hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED


          val map = Arguments.createMap()
          map.putString("id", id)
          map.putArray("devices", deviceTypes)
          map.putString("position", parseLensFacing(lensFacing))
          map.putString("name", name ?: "${parseLensFacing(lensFacing)} ($id)")
          map.putBoolean("hasFlash", hasFlash)
          map.putBoolean("hasTorch", hasFlash)
          map.putBoolean("isMultiCam", isMultiCam)
          map.putBoolean("supportsParallelVideoProcessing", supportsParallelVideoProcessing)
          map.putBoolean("supportsRawCapture", supportsRawCapture)
          map.putBoolean("supportsDepthCapture", supportsDepthCapture)
          map.putBoolean("supportsLowLightBoost", supportsLowLightBoost)
          map.putBoolean("supportsFocus", true) // I believe every device here supports focussing
          if (zoomRange != null) {
            map.putDouble("minZoom", zoomRange.lower.toDouble())
            map.putDouble("maxZoom", zoomRange.upper.toDouble())
          } else {
            map.putDouble("minZoom", 1.0)
            map.putDouble("maxZoom", maxScalerZoom.toDouble())
          }
          map.putDouble("neutralZoom", 1.0)
          cameraDevices.pushMap(map)
        }

        val difference = System.currentTimeMillis() - startTime
        Log.w(TAG, "CameraViewModule::getAvailableCameraDevices took: $difference ms")
        return@withPromise cameraDevices
      }
    }
  }

  @ReactMethod
  fun getCameraPermissionStatus(promise: Promise) {
    val status = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.CAMERA)
    promise.resolve(parsePermissionStatus(status))
  }

  @ReactMethod
  fun getMicrophonePermissionStatus(promise: Promise) {
    val status = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.RECORD_AUDIO)
    promise.resolve(parsePermissionStatus(status))
  }

  @ReactMethod
  fun requestCameraPermission(promise: Promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      // API 21 and below always grants permission on app install
      return promise.resolve("authorized")
    }

    val activity = reactApplicationContext.currentActivity
    if (activity is PermissionAwareActivity) {
      val currentRequestCode = RequestCode++
      val listener = PermissionListener { requestCode: Int, _: Array<String>, grantResults: IntArray ->
        if (requestCode == currentRequestCode) {
          val permissionStatus = if (grantResults.isNotEmpty()) grantResults[0] else PackageManager.PERMISSION_DENIED
          promise.resolve(parsePermissionStatus(permissionStatus))
          return@PermissionListener true
        }
        return@PermissionListener false
      }
      activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), currentRequestCode, listener)
    } else {
      promise.reject("NO_ACTIVITY", "No PermissionAwareActivity was found! Make sure the app has launched before calling this function.")
    }
  }

  @ReactMethod
  fun requestMicrophonePermission(promise: Promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      // API 21 and below always grants permission on app install
      return promise.resolve("authorized")
    }

    val activity = reactApplicationContext.currentActivity
    if (activity is PermissionAwareActivity) {
      val currentRequestCode = RequestCode++
      val listener = PermissionListener { requestCode: Int, _: Array<String>, grantResults: IntArray ->
        if (requestCode == currentRequestCode) {
          val permissionStatus = if (grantResults.isNotEmpty()) grantResults[0] else PackageManager.PERMISSION_DENIED
          promise.resolve(parsePermissionStatus(permissionStatus))
          return@PermissionListener true
        }
        return@PermissionListener false
      }
      activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), currentRequestCode, listener)
    } else {
      promise.reject("NO_ACTIVITY", "No PermissionAwareActivity was found! Make sure the app has launched before calling this function.")
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  fun isEmulator(): String {
    val support = (Build.FINGERPRINT.startsWith("generic")
      || Build.FINGERPRINT.startsWith("unknown")
      || Build.MODEL.contains("google_sdk")
      || Build.MODEL.lowercase(Locale.ROOT).contains("droid4x")
      || Build.MODEL.contains("Emulator")
      || Build.MODEL.contains("Android SDK built for x86")
      || Build.MANUFACTURER.contains("Genymotion")
      || Build.HARDWARE.contains("goldfish")
      || Build.HARDWARE.contains("ranchu")
      || Build.HARDWARE.contains("vbox86")
      || Build.PRODUCT.contains("sdk")
      || Build.PRODUCT.contains("google_sdk")
      || Build.PRODUCT.contains("sdk_google")
      || Build.PRODUCT.contains("sdk_x86")
      || Build.PRODUCT.contains("vbox86p")
      || Build.PRODUCT.contains("emulator")
      || Build.PRODUCT.contains("simulator")
      || Build.BOARD.lowercase(Locale.ROOT).contains("nox")
      || Build.BOOTLOADER.lowercase(Locale.ROOT).contains("nox")
      || Build.HARDWARE.lowercase(Locale.ROOT).contains("nox")
      || Build.PRODUCT.lowercase(Locale.ROOT).contains("nox")
      || Build.SERIAL.lowercase(Locale.ROOT)
      .contains("nox")) || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    return if (support) "true" else "false"
  }
}
