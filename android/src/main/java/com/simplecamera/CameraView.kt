package com.simplecamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.camera2.*
import android.util.Log
import android.view.*
import android.view.View.OnTouchListener
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.core.impl.*
import androidx.camera.extensions.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.*
import com.facebook.react.bridge.*
import com.simplecamera.*
import com.simplecamera.utils.*
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await

@Suppress(
    "KotlinJniMissingFunction"
) // I use fbjni, Android Studio is not smart enough to realize that.
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class CameraView(context: Context) : FrameLayout(context), LifecycleOwner {
  companion object {
    const val TAG = "CameraView"
    const val TAG_PERF = "CameraView.performance"

    private val propsThatRequireSessionReconfiguration =
        arrayListOf("cameraId", "photo", "video", "aspectRatio")
    private val arrayListOfZoom = arrayListOf("zoom")
  }

  private var mDisplayOrientationDetector: DisplayOrientationDetector
  var displayOrientation = 0
  var deviceOrientation = 0

  // react properties
  // props that require reconfiguring
  var cameraId: String? = null
  var aspectRatio = AspectRatio.RATIO_16_9

  // use-cases
  var photo: Boolean? = null
  var video: Boolean? = null
  var audio: Boolean? = null

  // other props
  var isActive = false
  var torch = "off"
  var zoom: Float = 1f // in "factor"
  var orientation: String? = null
  var enableZoomGesture = false
    set(value) {
      field = value
      setOnTouchListener(if (value) touchEventListener else null)
    }
  var enableReadCode = false
    set(value) {
      field = value
      update(propsThatRequireSessionReconfiguration)
    }

  // private properties
  private var isMounted = false
  private val reactContext: ReactContext
    get() = context as ReactContext

  @Suppress("JoinDeclarationAndAssignment") internal val previewView: PreviewView
  private val cameraExecutor = Executors.newSingleThreadExecutor()
  internal val takePhotoExecutor = Executors.newSingleThreadExecutor()
  internal val recordVideoExecutor = Executors.newSingleThreadExecutor()
  internal var coroutineScope = CoroutineScope(Dispatchers.Main)

  internal var camera: Camera? = null
  internal var imageCapture: ImageCapture? = null
  internal var videoCapture: VideoCapture<Recorder>? = null
  private var imageAnalyzer: ImageAnalysis? = null
  private var preview: Preview? = null

  internal var activeVideoRecording: Recording? = null

  private val scaleGestureListener: ScaleGestureDetector.SimpleOnScaleGestureListener
  private val scaleGestureDetector: ScaleGestureDetector
  private val touchEventListener: OnTouchListener

  private val lifecycleRegistry: LifecycleRegistry
  private var hostLifecycleState: Lifecycle.State
  private var previousProps: ArrayList<String>? = null

  private val inputRotation: Int
    get() {
      return context.displayRotation
    }
  private val outputRotation: Int
    get() {
      return if (orientation != null) {
        // user is overriding output orientation
        when (orientation!!) {
          "portrait" -> Surface.ROTATION_0
          "landscapeRight" -> Surface.ROTATION_90
          "portraitUpsideDown" -> Surface.ROTATION_180
          "landscapeLeft" -> Surface.ROTATION_270
          else -> throw InvalidTypeScriptUnionError("orientation", orientation!!)
        }
      } else {
        // use same as input rotation
        inputRotation
      }
    }

  private var minZoom: Float = 1f
  private var maxZoom: Float = 1f

  init {
    previewView = PreviewView(context)
    previewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    previewView.setBackgroundColor(Color.BLACK)
    setBackgroundColor(Color.BLACK)
    previewView.installHierarchyFitter() // If this is not called correctly, view finder will be
    // black/blank
    addView(previewView)

    scaleGestureListener =
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
          override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = max(min((zoom * detector.scaleFactor), maxZoom), minZoom)
            update(arrayListOfZoom)
            return true
          }
        }
    scaleGestureDetector = ScaleGestureDetector(context, scaleGestureListener)
    touchEventListener = OnTouchListener { _, event ->
      return@OnTouchListener scaleGestureDetector.onTouchEvent(event)
    }

    hostLifecycleState = Lifecycle.State.INITIALIZED
    lifecycleRegistry = LifecycleRegistry(this)
    reactContext.addLifecycleEventListener(
        object : LifecycleEventListener {
          override fun onHostResume() {
            hostLifecycleState = Lifecycle.State.RESUMED
            updateLifecycleState()
            // workaround for https://issuetracker.google.com/issues/147354615, preview must be
            // bound on resume
            update(propsThatRequireSessionReconfiguration)
          }

          override fun onHostPause() {
            hostLifecycleState = Lifecycle.State.CREATED
            updateLifecycleState()
          }

          override fun onHostDestroy() {
            hostLifecycleState = Lifecycle.State.DESTROYED
            updateLifecycleState()
            cameraExecutor.shutdown()
            takePhotoExecutor.shutdown()
            recordVideoExecutor.shutdown()
            reactContext.removeLifecycleEventListener(this)
          }
        }
    )

    // Display orientation detector
    mDisplayOrientationDetector =
        object : DisplayOrientationDetector(context) {
          override fun onDisplayOrientationChanged(
              displayOrientation: Int,
              deviceOrientation: Int
          ) {
            this@CameraView.displayOrientation = displayOrientation
            this@CameraView.deviceOrientation = deviceOrientation
            debugLog("📸 onDisplayOrientationChanged")
            when (deviceOrientation) {
              0 -> orientation = "portrait"
              90 -> orientation = "landscapeLeft"
              270 -> orientation = "landscapeRight"
              180 -> orientation = "portraitUpsideDown"
            }
            updateOrientation()
          }
        }
  }

  override fun onConfigurationChanged(newConfig: Configuration?) {
    super.onConfigurationChanged(newConfig)
    updateOrientation()
    debugLog("📸 onConfigurationChanged")
  }

  @SuppressLint("RestrictedApi")
  private fun updateOrientation() {
    preview?.targetRotation = inputRotation
    imageCapture?.targetRotation = outputRotation
    videoCapture?.targetRotation = outputRotation
  }

  override fun getLifecycle(): Lifecycle {
    return lifecycleRegistry
  }

  /**
   * Updates the custom Lifecycle to match the host activity's lifecycle, and if it's active we
   * narrow it down to the [isActive] and [isAttachedToWindow] fields.
   */
  private fun updateLifecycleState() {
    val lifecycleBefore = lifecycleRegistry.currentState
    debugLog(
        "📸 updateLifecycleState lifecycleBefore: $lifecycleBefore hostLifecycleState: $hostLifecycleState"
    )
    if (hostLifecycleState == Lifecycle.State.RESUMED) {
      // Host Lifecycle (Activity) is currently active (RESUMED), so we narrow it down to the view's
      // lifecycle
      if (isActive && isAttachedToWindow) {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
      } else {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
      }
    } else {
      // Host Lifecycle (Activity) is currently inactive (STARTED or DESTROYED), so that overrules
      // our view's lifecycle
      lifecycleRegistry.currentState = hostLifecycleState
    }
    Log.d(
        TAG,
        "Lifecycle went from ${lifecycleBefore.name} -> ${lifecycleRegistry.currentState.name} (isActive: $isActive | isAttachedToWindow: $isAttachedToWindow)"
    )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    debugLog("📸 onAttachedToWindow $isMounted")
    updateLifecycleState()
    if (this.previousProps != null) {
      this.update(this.previousProps!!)
    }
    mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this))
    if (!isMounted) {
      isMounted = true
      invokeOnViewReady()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    debugLog("📸 onDetachedFromWindow")
    isMounted = false
    updateLifecycleState()
    mDisplayOrientationDetector.disable()
  }

  /** Invalidate all React Props and reconfigure the device */
  fun update(changedProps: ArrayList<String>) =
      previewView.post {
        this.previousProps = changedProps
        debugLog("📸 update($changedProps)")
        // TODO: Does this introduce too much overhead?
        //  I need to .post on the previewView because it might've not been initialized yet
        //  I need to use CoroutineScope.launch because of the suspend fun [configureSession]
        coroutineScope.launch {
          try {
            val shouldReconfigureSession =
                changedProps.containsAny(propsThatRequireSessionReconfiguration)
            val shouldReconfigureZoom = shouldReconfigureSession || changedProps.contains("zoom")
            val shouldReconfigureTorch = shouldReconfigureSession || changedProps.contains("torch")
            val shouldUpdateOrientation =
                shouldReconfigureSession || changedProps.contains("orientation")

            if (changedProps.contains("isActive")) {
              updateLifecycleState()
            }
            if (shouldReconfigureSession) {
              configureSession()
            }
            if (shouldReconfigureZoom) {
              val zoomClamped = max(min(zoom, maxZoom), minZoom)
              camera!!.cameraControl.setZoomRatio(zoomClamped)
            }
            if (shouldReconfigureTorch) {
              camera!!.cameraControl.enableTorch(torch == "on")
            }
            if (shouldUpdateOrientation) {
              updateOrientation()
            }
          } catch (e: Throwable) {
            Log.e(TAG, "update() threw: ${e.message}")
            invokeOnError(e)
          }
        }
      }

  /**
   * Configures the camera capture session. This should only be called when the camera device
   * changes.
   */
  @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
  private suspend fun configureSession() {
    try {
      debugLog("📸 configureSession")
      val startTime = System.currentTimeMillis()
      Log.i(TAG, "Configuring session...")
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
              PackageManager.PERMISSION_GRANTED
      ) {
        throw CameraPermissionError()
      }
      if (cameraId == null) {
        throw NoCameraDeviceError()
      }

      // Used to bind the lifecycle of cameras to the lifecycle owner
      val cameraProvider = ProcessCameraProvider.getInstance(reactContext).await()

      val cameraSelector = CameraSelector.Builder().byID(cameraId!!).build()

      val previewBuilder = Preview.Builder().setTargetRotation(inputRotation)

      val imageCaptureBuilder =
          ImageCapture.Builder()
              .setTargetRotation(outputRotation)
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)

      val videoRecorderBuilder = Recorder.Builder().setExecutor(cameraExecutor)

      // let CameraX automatically find best resolution for the target aspect ratio
      Log.i(
          TAG,
          "No custom format has been set, CameraX will automatically determine best configuration..."
      )
      //      val aspectRatio = aspectRatio(
      //        previewView.width, previewView.height,
      //      ) // flipped because it's in sensor orientation.
      previewBuilder.setTargetAspectRatio(aspectRatio)
      imageCaptureBuilder.setTargetAspectRatio(aspectRatio)

      // Unbind use cases before rebinding
      videoCapture = null
      imageCapture = null
      cameraProvider.unbindAll()

      // Bind use cases to camera
      val useCases = ArrayList<UseCase>()
      if (enableReadCode == false && video == true) {
        Log.i(TAG, "Adding VideoCapture use-case...")

        val videoRecorder = videoRecorderBuilder.build()
        videoCapture = VideoCapture.withOutput(videoRecorder)
        videoCapture!!.targetRotation = outputRotation
        useCases.add(videoCapture!!)
      }
      if (photo == true) {
        Log.i(TAG, "Adding ImageCapture use-case...")
        imageCapture = imageCaptureBuilder.build()
        useCases.add(imageCapture!!)
      }

      if (enableReadCode == true) {
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        val analyzer = QRCodeAnalyzer { barcodes ->
          if (barcodes.isNotEmpty()) {
            barcodes.forEach { invokeOnReadCode(it) }
          }
        }
        imageAnalyzer!!.setAnalyzer(cameraExecutor, analyzer)
        useCases.add(imageAnalyzer!!)
      }

      preview = previewBuilder.build()
      Log.i(TAG, "Attaching ${useCases.size} use-cases...")
      camera =
          cameraProvider.bindToLifecycle(this, cameraSelector, preview, *useCases.toTypedArray())
      preview!!.setSurfaceProvider(previewView.surfaceProvider)

      minZoom = camera!!.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
      maxZoom = camera!!.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

      val duration = System.currentTimeMillis() - startTime
      Log.i(TAG_PERF, "Session configured in $duration ms! Camera: ${camera!!}")
      invokeOnInitialized()
    } catch (exc: Throwable) {
      Log.e(TAG, "Failed to configure session: ${exc.message}")
      throw when (exc) {
        is CameraError -> exc
        is IllegalArgumentException -> {
          if (exc.message?.contains("too many use cases") == true) {
            ParallelVideoProcessingNotSupportedError(exc)
          } else {
            InvalidCameraDeviceError(exc)
          }
        }
        else -> UnknownCameraError(exc)
      }
    }
  }
}
