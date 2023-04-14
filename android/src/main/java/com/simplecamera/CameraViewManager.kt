package com.simplecamera

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.simplecamera.utils.aspectRatioByName
import com.simplecamera.utils.debugLog

@Suppress("unused")
class CameraViewManager(reactContext: ReactApplicationContext) : ViewGroupManager<CameraView>() {

  public override fun createViewInstance(context: ThemedReactContext): CameraView {
    return CameraView(context)
  }

  override fun onAfterUpdateTransaction(view: CameraView) {
    super.onAfterUpdateTransaction(view)
    debugLog("📸 onAfterUpdateTransaction")
    val changedProps = cameraViewTransactions[view] ?: ArrayList()
    view.update(changedProps)
    cameraViewTransactions.remove(view)
  }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any>? {
    return MapBuilder.builder<String, Any>()
      .put("cameraViewReady", MapBuilder.of("registrationName", "onViewReady"))
      .put("cameraInitialized", MapBuilder.of("registrationName", "onInitialized"))
      .put("cameraError", MapBuilder.of("registrationName", "onError"))
      .put("cameraReadCode", MapBuilder.of("registrationName", "onReadCode"))
      .build()
  }

  override fun getName(): String {
    return TAG
  }

  @ReactProp(name = "cameraId")
  fun setCameraId(view: CameraView, cameraId: String) {
    if (view.cameraId != cameraId)
      addChangedPropToTransaction(view, "cameraId")
    view.cameraId = cameraId
  }

  @ReactProp(name = "cameraAspectRatio")
  fun setAspectRatio(view: CameraView, aspectRatio: String) {
    val ratio = aspectRatioByName(aspectRatio)
    if (view.aspectRatio != ratio)
      addChangedPropToTransaction(view, "aspectRatio")
    view.aspectRatio = ratio
  }

  @ReactProp(name = "photo")
  fun setPhoto(view: CameraView, photo: Boolean?) {
    if (view.photo != photo)
      addChangedPropToTransaction(view, "photo")
    view.photo = photo
  }

  @ReactProp(name = "video")
  fun setVideo(view: CameraView, video: Boolean?) {
    if (view.video != video)
      addChangedPropToTransaction(view, "video")
    view.video = video
  }

  @ReactProp(name = "audio")
  fun setAudio(view: CameraView, audio: Boolean?) {
    if (view.audio != audio)
      addChangedPropToTransaction(view, "audio")
    view.audio = audio
  }

  @ReactProp(name = "isActive")
  fun setIsActive(view: CameraView, isActive: Boolean) {
    if (view.isActive != isActive)
      addChangedPropToTransaction(view, "isActive")
    view.isActive = isActive
  }

  @ReactProp(name = "torch")
  fun setTorch(view: CameraView, torch: String) {
    if (view.torch != torch)
      addChangedPropToTransaction(view, "torch")
    view.torch = torch
  }

  @ReactProp(name = "zoom")
  fun setZoom(view: CameraView, zoom: Double) {
    val zoomFloat = zoom.toFloat()
    if (view.zoom != zoomFloat)
      addChangedPropToTransaction(view, "zoom")
    view.zoom = zoomFloat
  }

  @ReactProp(name = "enableZoomGesture")
  fun setEnableZoomGesture(view: CameraView, enableZoomGesture: Boolean) {
    if (view.enableZoomGesture != enableZoomGesture)
      addChangedPropToTransaction(view, "enableZoomGesture")
    view.enableZoomGesture = enableZoomGesture
  }

  @ReactProp(name = "enableReadCode")
  fun setEnableReadCode(view: CameraView, enableReadCode: Boolean) {
    if (view.enableReadCode != enableReadCode)
        addChangedPropToTransaction(view, "enableReadCode")
    view.enableReadCode = enableReadCode
  }


  @ReactProp(name = "orientation")
  fun setOrientation(view: CameraView, orientation: String) {
    if (view.orientation != orientation)
      addChangedPropToTransaction(view, "orientation")
    view.orientation = orientation
  }


  override fun onDropViewInstance(view: CameraView) {
    super.onDropViewInstance(view)
    debugLog("📸 onDropViewInstance")
  }

  companion object {
    const val TAG = "CameraView"

    val cameraViewTransactions: HashMap<CameraView, ArrayList<String>> = HashMap()

    private fun addChangedPropToTransaction(view: CameraView, changedProp: String) {
      debugLog("📸 addChangedPropToTransaction: $changedProp")
      if (cameraViewTransactions[view] == null) {
        cameraViewTransactions[view] = ArrayList()
      }
      cameraViewTransactions[view]!!.add(changedProp)
    }
  }
}
