package com.simplecamera.utils

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.util.SizeF
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

// 35mm is 135 film format, a standard in which focal lengths are usually measured
val Size35mm = Size(36, 24)
val Size.bigger: Int
  get() = max(this.width, this.height)
val Size.smaller: Int
  get() = min(this.width, this.height)

val SizeF.bigger: Float
  get() = max(this.width, this.height)
val SizeF.smaller: Float
  get() = min(this.width, this.height)


/**
 * Convert a given array of focal lengths to the corresponding TypeScript union type name.
 *
 * Possible values for single cameras:
 * * `"wide-angle-camera"`
 * * `"ultra-wide-angle-camera"`
 * * `"telephoto-camera"`
 *
 * Sources for the focal length categories:
 * * [Telephoto Lens (wikipedia)](https://en.wikipedia.org/wiki/Telephoto_lens)
 * * [Normal Lens (wikipedia)](https://en.wikipedia.org/wiki/Normal_lens)
 * * [Wide-Angle Lens (wikipedia)](https://en.wikipedia.org/wiki/Wide-angle_lens)
 * * [Ultra-Wide-Angle Lens (wikipedia)](https://en.wikipedia.org/wiki/Ultra_wide_angle_lens)
 */
fun CameraCharacteristics.getDeviceTypes(): ReadableArray {
  // TODO: Check if getDeviceType() works correctly, even for logical multi-cameras
  val focalLengths = this.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!
  val sensorSize = this.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!

  // To get valid focal length standards we have to upscale to the 35mm measurement (film standard)
  val cropFactor = Size35mm.bigger / sensorSize.bigger

  val deviceTypes = Arguments.createArray()

  val containsTelephoto = focalLengths.any { l -> (l * cropFactor) > 35 } // TODO: Telephoto lenses are > 85mm, but we don't have anything between that range..
  // val containsNormalLens = focalLengths.any { l -> (l * cropFactor) > 35 && (l * cropFactor) <= 55 }
  val containsWideAngle = focalLengths.any { l -> (l * cropFactor) >= 24 && (l * cropFactor) <= 35 }
  val containsUltraWideAngle = focalLengths.any { l -> (l * cropFactor) < 24 }

  if (containsTelephoto)
    deviceTypes.pushString("telephoto-camera")
  if (containsWideAngle)
    deviceTypes.pushString("wide-angle-camera")
  if (containsUltraWideAngle)
    deviceTypes.pushString("ultra-wide-angle-camera")

  return deviceTypes
}
