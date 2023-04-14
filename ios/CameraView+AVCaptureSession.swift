
import AVFoundation
import Foundation

/**
 Extension for CameraView that sets up the AVCaptureSession, Device and Format.
 */
extension CameraView {
    // pragma MARK: Configure Capture Session
    
    /**
     Configures the Capture Session.
     */
    final func configureCaptureSession() {
        ReactLogger.log(level: .info, message: "Configuring Session...")
        isReady = false
        
#if targetEnvironment(simulator)
        invokeOnError(.device(.notAvailableOnSimulator))
        return
#endif
        
        guard cameraId != nil else {
            invokeOnError(.device(.noDevice))
            return
        }
        let cameraId = self.cameraId! as String
        
        ReactLogger.log(level: .info, message: "Initializing Camera with device \(cameraId)...")
        captureSession.beginConfiguration()
        defer {
            captureSession.commitConfiguration()
        }
        
        if let aspectRatio = cameraAspectRatio {
            if aspectRatio == "4_3" {
                if captureSession.canSetSessionPreset(.photo) {
                    captureSession.sessionPreset = .photo
                }
            } else {
                captureSession.sessionPreset = defaultPreset
            }
        }
        
        // pragma MARK: Capture Session Inputs
        // Video Input
        do {
            if let videoDeviceInput = videoDeviceInput {
                captureSession.removeInput(videoDeviceInput)
                self.videoDeviceInput = nil
            }
            ReactLogger.log(level: .info, message: "Adding Video input...")
            guard let videoDevice = AVCaptureDevice(uniqueID: cameraId) else {
                invokeOnError(.device(.invalid))
                return
            }
            videoDeviceInput = try AVCaptureDeviceInput(device: videoDevice)
            guard captureSession.canAddInput(videoDeviceInput!) else {
                invokeOnError(.parameter(.unsupportedInput(inputDescriptor: "video-input")))
                return
            }
            captureSession.addInput(videoDeviceInput!)
            
            // Initialize a AVCaptureMetadataOutput object and set it as the output device to the capture session.
            let captureMetadataOutput = AVCaptureMetadataOutput()
            captureSession.addOutput(captureMetadataOutput)

            // Set delegate and use the default dispatch queue to execute the call back
            captureMetadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            captureMetadataOutput.metadataObjectTypes = [AVMetadataObject.ObjectType.qr]

        } catch {
            invokeOnError(.device(.invalid))
            return
        }
        
        // pragma MARK: Capture Session Outputs
        
        // Photo Output
        if let photoOutput = photoOutput {
            captureSession.removeOutput(photoOutput)
            self.photoOutput = nil
        }
        if photo?.boolValue == true {
            ReactLogger.log(level: .info, message: "Adding Photo output...")
            photoOutput = AVCapturePhotoOutput()
            
            guard captureSession.canAddOutput(photoOutput!) else {
                invokeOnError(.parameter(.unsupportedOutput(outputDescriptor: "photo-output")))
                return
            }
            captureSession.addOutput(photoOutput!)
            if videoDeviceInput!.device.position == .front {
                photoOutput!.mirror()
            }
        }
        
        // Video Output
        if let videoOutput = videoOutput {
            captureSession.removeOutput(videoOutput)
            self.videoOutput = nil
        }
        if video?.boolValue == true {
            ReactLogger.log(level: .info, message: "Adding Video Data output...")
            videoOutput = AVCaptureVideoDataOutput()
            guard captureSession.canAddOutput(videoOutput!) else {
                invokeOnError(.parameter(.unsupportedOutput(outputDescriptor: "video-output")))
                return
            }
            videoOutput!.setSampleBufferDelegate(self, queue: videoQueue)
            videoOutput!.alwaysDiscardsLateVideoFrames = false
            captureSession.addOutput(videoOutput!)
        }
        
        onOrientationChanged()
        
        invokeOnInitialized()
        isReady = true
        ReactLogger.log(level: .info, message: "Session successfully configured!")
    }
    
    // pragma MARK: Configure Format
    
    /**
     Configures the Video Device to find the best matching Format.
     */
    final func configureFormat() {
        ReactLogger.log(level: .info, message: "Configuring Format...")
        guard let filter = format else {
            // Format Filter was null. Ignore it.
            return
        }
        guard let device = videoDeviceInput?.device else {
            invokeOnError(.session(.cameraNotReady))
            return
        }
        
        if device.activeFormat.matchesFilter(filter) {
            ReactLogger.log(level: .info, message: "Active format already matches filter.")
            return
        }
        
        // get matching format
        let matchingFormats = device.formats.filter { $0.matchesFilter(filter) }.sorted { $0.isBetterThan($1) }
        guard let format = matchingFormats.first else {
            invokeOnError(.format(.invalidFormat))
            return
        }
        
        do {
            try device.lockForConfiguration()
            device.activeFormat = format
            device.unlockForConfiguration()
            ReactLogger.log(level: .info, message: "Format successfully configured!")
        } catch let error as NSError {
            invokeOnError(.device(.configureError), cause: error)
            return
        }
    }
    
    // pragma MARK: Notifications/Interruptions
    
    @objc
    func sessionRuntimeError(notification: Notification) {
        ReactLogger.log(level: .error, message: "Unexpected Camera Runtime Error occured!")
        guard let error = notification.userInfo?[AVCaptureSessionErrorKey] as? AVError else {
            return
        }
        
        invokeOnError(.unknown(message: error._nsError.description), cause: error._nsError)
        
        if isActive {
            // restart capture session after an error occured
            cameraQueue.async {
                self.captureSession.startRunning()
            }
        }
    }
}
