import type { CameraDevice } from '../CameraDevice';

/**
 * Compares two devices by the following criteria:
 * * `wide-angle-camera`s are ranked higher than others
 * * Devices with more physical cameras are ranked higher than ones with less. (e.g. "Triple Camera" > "Wide-Angle Camera")
 *
 * > Note that this makes the `sort()` function descending, so the first element (`[0]`) is the "best" device.
 *
 * @example
 * ```ts
 * const devices = camera.devices.sort(sortDevices)
 * const bestDevice = devices[0]
 * ```
 * @method
 */
export const sortDevices = (
  left: CameraDevice,
  right: CameraDevice
): number => {
  let leftPoints = 0;
  let rightPoints = 0;

  const leftHasWideAngle = left.devices.includes('wide-angle-camera');
  const rightHasWideAngle = right.devices.includes('wide-angle-camera');
  if (leftHasWideAngle) leftPoints += 2;
  if (rightHasWideAngle) rightPoints += 2;

  // telephoto cameras often have very poor quality.
  const leftHasTelephoto = left.devices.includes('telephoto-camera');
  const rightHasTelephoto = right.devices.includes('telephoto-camera');
  if (leftHasTelephoto) leftPoints -= 2;
  if (rightHasTelephoto) rightPoints -= 2;

  if (left.devices.length > right.devices.length) leftPoints += 1;
  if (right.devices.length > left.devices.length) rightPoints += 1;

  return rightPoints - leftPoints;
};
