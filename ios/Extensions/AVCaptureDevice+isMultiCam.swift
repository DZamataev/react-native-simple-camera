

import AVFoundation

extension AVCaptureDevice {
  /**
   Returns true if the device is a virtual multi-cam, false otherwise.
   */
  var isMultiCam: Bool {
    if #available(iOS 13.0, *) {
      return self.isVirtualDevice
    } else {
      return false
    }
  }
}
