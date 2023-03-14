package com.simplecamera.utils

import android.hardware.camera2.CameraCharacteristics

fun parseLensFacing(lensFacing: Int?): String? {
  return when (lensFacing) {
    CameraCharacteristics.LENS_FACING_BACK -> "back"
    CameraCharacteristics.LENS_FACING_FRONT -> "front"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
    else -> null
  }
}
