import { sortDevices } from '../utils/FormatFilter';
import { CameraModule } from '../Camera';
import {
  CameraDevice,
  LogicalCameraDeviceType,
  parsePhysicalDeviceTypes,
  PhysicalCameraDeviceType,
} from '../CameraDevice';
import type { CameraPosition } from 'src/PhotoFile';

export type CameraDevices = {
  [key in CameraPosition]: CameraDevice | undefined;
};

let _cachedCameraDevices: CameraDevices | undefined;
export async function getAvailableCameras(params?: {
  deviceType?: PhysicalCameraDeviceType | LogicalCameraDeviceType;
}): Promise<CameraDevices> {
  if (_cachedCameraDevices == null) {
    let devices = await CameraModule.getAvailableCameraDevices();
    devices = devices.sort(sortDevices);
    if (params?.deviceType != null) {
      devices = devices.filter((d) => {
        const parsedType = parsePhysicalDeviceTypes(d.devices);
        return parsedType === params.deviceType;
      });
    }
    _cachedCameraDevices = {
      back: devices.find((d) => d.position === 'back'),
      front: devices.find((d) => d.position === 'front'),
    };
  }

  return { ..._cachedCameraDevices };
}
