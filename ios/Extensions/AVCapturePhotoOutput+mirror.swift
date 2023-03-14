

import AVFoundation

extension AVCapturePhotoOutput {
  func mirror() {
    connections.forEach { connection in
      if connection.isVideoMirroringSupported {
        connection.automaticallyAdjustsVideoMirroring = false
        connection.isVideoMirrored = true
      }
    }
  }
}
