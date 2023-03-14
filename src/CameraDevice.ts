import type { CameraPosition } from 'src/PhotoFile';

/**
 * Indentifiers for a physical camera (one that actually exists on the back/front of the device)
 *
 * * `"ultra-wide-angle-camera"`: A built-in camera with a shorter focal length than that of a wide-angle camera. (focal length between below 24mm)
 * * `"wide-angle-camera"`: A built-in wide-angle camera. (focal length between 24mm and 35mm)
 * * `"telephoto-camera"`: A built-in camera device with a longer focal length than a wide-angle camera. (focal length between above 85mm)
 */
export type PhysicalCameraDeviceType =
  | 'ultra-wide-angle-camera'
  | 'wide-angle-camera'
  | 'telephoto-camera';

/**
 * Indentifiers for a logical camera (Combinations of multiple physical cameras to create a single logical camera).
 *
 * * `"dual-camera"`: A combination of wide-angle and telephoto cameras that creates a capture device.
 * * `"dual-wide-camera"`: A device that consists of two cameras of fixed focal length, one ultrawide angle and one wide angle.
 * * `"triple-camera"`: A device that consists of three cameras of fixed focal length, one ultrawide angle, one wide angle, and one telephoto.
 */
export type LogicalCameraDeviceType =
  | 'dual-camera'
  | 'dual-wide-camera'
  | 'triple-camera';

/**
 * Parses an array of physical device types into a single {@linkcode PhysicalCameraDeviceType} or {@linkcode LogicalCameraDeviceType}, depending what matches.
 * @method
 */
export const parsePhysicalDeviceTypes = (
  physicalDeviceTypes: PhysicalCameraDeviceType[]
): PhysicalCameraDeviceType | LogicalCameraDeviceType => {
  if (physicalDeviceTypes.length === 1) {
    // @ts-expect-error for very obvious reasons
    return physicalDeviceTypes[0];
  }

  const hasWide = physicalDeviceTypes.includes('wide-angle-camera');
  const hasUltra = physicalDeviceTypes.includes('ultra-wide-angle-camera');
  const hasTele = physicalDeviceTypes.includes('telephoto-camera');

  if (hasTele && hasWide && hasUltra) return 'triple-camera';
  if (hasWide && hasUltra) return 'dual-wide-camera';
  if (hasWide && hasTele) return 'dual-camera';

  throw new Error(
    `Invalid physical device type combination! ${physicalDeviceTypes.join(
      ' + '
    )}`
  );
};

/**
 * Indicates a format's autofocus system.
 *
 * * `"none"`: Indicates that autofocus is not available
 * * `"contrast-detection"`: Indicates that autofocus is achieved by contrast detection. Contrast detection performs a focus scan to find the optimal position
 * * `"phase-detection"`: Indicates that autofocus is achieved by phase detection. Phase detection has the ability to achieve focus in many cases without a focus scan. Phase detection autofocus is typically less visually intrusive than contrast detection autofocus
 */
export type AutoFocusSystem = 'contrast-detection' | 'phase-detection' | 'none';

/**
 * Indicates a format's supported video stabilization mode
 *
 * * `"off"`: Indicates that video should not be stabilized
 * * `"standard"`: Indicates that video should be stabilized using the standard video stabilization algorithm introduced with iOS 5.0. Standard video stabilization has a reduced field of view. Enabling video stabilization may introduce additional latency into the video capture pipeline
 * * `"cinematic"`: Indicates that video should be stabilized using the cinematic stabilization algorithm for more dramatic results. Cinematic video stabilization has a reduced field of view compared to standard video stabilization. Enabling cinematic video stabilization introduces much more latency into the video capture pipeline than standard video stabilization and consumes significantly more system memory. Use narrow or identical min and max frame durations in conjunction with this mode
 * * `"cinematic-extended"`: Indicates that the video should be stabilized using the extended cinematic stabilization algorithm. Enabling extended cinematic stabilization introduces longer latency into the video capture pipeline compared to the AVCaptureVideoStabilizationModeCinematic and consumes more memory, but yields improved stability. It is recommended to use identical or similar min and max frame durations in conjunction with this mode (iOS 13.0+)
 * * `"auto"`: Indicates that the most appropriate video stabilization mode for the device and format should be chosen automatically
 */
export type VideoStabilizationMode =
  | 'off'
  | 'standard'
  | 'cinematic'
  | 'cinematic-extended'
  | 'auto';

/**
 * Represents a camera device discovered by the {@linkcode Camera.getAvailableCameraDevices | Camera.getAvailableCameraDevices()} function
 */
export interface CameraDevice {
  /**
   * The native ID of the camera device instance.
   */
  id: string;
  /**
   * The physical devices this `CameraDevice` contains.
   *
   * * If this camera device is a **logical camera** (combination of multiple physical cameras), there are multiple cameras in this array.
   * * If this camera device is a **physical camera**, there is only a single element in this array.
   *
   * You can check if the camera is a logical multi-camera by using the `isMultiCam` property.
   */
  devices: PhysicalCameraDeviceType[];
  /**
   * Specifies the physical position of this camera. (back or front)
   */
  position: CameraPosition;
  /**
   * A friendly localized name describing the camera.
   */
  name: string;
  /**
   * Specifies whether this camera supports enabling flash for photo capture.
   */
  hasFlash: boolean;
  /**
   * Specifies whether this camera supports continuously enabling the flash to act like a torch (flash with video capture)
   */
  hasTorch: boolean;
  /**
   * A property indicating whether the device is a virtual multi-camera consisting of multiple combined physical cameras.
   *
   * Examples:
   * * The Dual Camera, which supports seamlessly switching between a wide and telephoto camera while zooming and generating depth data from the disparities between the different points of view of the physical cameras.
   * * The TrueDepth Camera, which generates depth data from disparities between a YUV camera and an Infrared camera pointed in the same direction.
   */
  isMultiCam: boolean;
  /**
   * Minimum available zoom factor (e.g. `1`)
   */
  minZoom: number;
  /**
   * Maximum available zoom factor (e.g. `128`)
   */
  maxZoom: number;
  /**
   * The zoom factor where the camera is "neutral".
   *
   * * For single-physical cameras this property is always `1.0`.
   * * For multi cameras this property is a value between `minZoom` and `maxZoom`, where the camera is in _wide-angle_ mode and hasn't switched to the _ultra-wide-angle_ ("fish-eye") or telephoto camera yet.
   *
   * Use this value as an initial value for the zoom property if you implement custom zoom. (e.g. reanimated shared value should be initially set to this value)
   * @example
   * const device = ...
   *
   * const zoom = useSharedValue(device.neutralZoom) // <-- initial value so it doesn't start at ultra-wide
   * const cameraProps = useAnimatedProps(() => ({
   *   zoom: zoom.value
   * }))
   */
  neutralZoom: number;
  /**
   * Whether this camera device supports using Video Recordings (`video={true}`) and Frame Processors (`frameProcessor={...}`) at the same time. See ["The `supportsParallelVideoProcessing` prop"](https://mrousavy.github.io/react-native-vision-camera/docs/guides/devices#the-supportsparallelvideoprocessing-prop) for more information.
   *
   * If this property is `false`, you can only enable `video` or add a `frameProcessor`, but not both.
   *
   * * On iOS this value is always `true`.
   * * On newer Android devices this value is always `true`.
   * * On older Android devices this value is `false` if the Camera's hardware level is `LEGACY` or `LIMITED`, `true` otherwise. (See [`INFO_SUPPORTED_HARDWARE_LEVEL`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL) or [the tables at "Regular capture"](https://developer.android.com/reference/android/hardware/camera2/CameraDevice#regular-capture))
   */
  supportsParallelVideoProcessing: boolean;
  /**
   * Whether this camera device supports low light boost.
   */
  supportsLowLightBoost: boolean;
  /**
   * Whether this camera supports taking photos with depth data.
   *
   * **! Work in Progress !**
   */
  supportsDepthCapture: boolean;
  /**
   * Whether this camera supports taking photos in RAW format
   *
   * **! Work in Progress !**
   */
  supportsRawCapture: boolean;
  /**
   * Specifies whether this device supports focusing ({@linkcode Camera.focus | Camera.focus(...)})
   */
  supportsFocus: boolean;
}
