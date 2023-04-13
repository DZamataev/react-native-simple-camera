import AVFoundation
import Foundation
import UIKit


private let propsThatRequireReconfiguration = ["cameraId", "photo", "video", "cameraAspectRatio"]

// MARK: - CameraView
public final class CameraView: UIView {
    // pragma MARK: React Properties
    
    // pragma MARK: Exported Properties
    // props that require reconfiguring
    @objc var cameraId: NSString?
    @objc var cameraAspectRatio: NSString?
    // use cases
    @objc var photo: NSNumber? // nullable bool
    @objc var video: NSNumber? // nullable bool
    @objc var audio: NSNumber? // nullable bool
    // props that require format reconfiguring
    @objc var format: NSDictionary?
    @objc var orientation: NSString?
    // other props
    @objc var isActive = false
    @objc var torch = "off"
    @objc var zoom: NSNumber = 1.0 // in "factor"
    @objc var videoStabilizationMode: NSString?
    @objc var enableReadCode = false
    // events
    @objc var onInitialized: RCTDirectEventBlock?
    @objc var onError: RCTDirectEventBlock?
    @objc var onViewReady: RCTDirectEventBlock?
    @objc var onReadCode: RCTDirectEventBlock?
    // zoom
    @objc var enableZoomGesture = false {
        didSet {
            if enableZoomGesture {
                addPinchGestureRecognizer()
            } else {
                removePinchGestureRecognizer()
            }
        }
    }
    
    // pragma MARK: Internal Properties
    internal var isMounted = false
    internal var isReady = false
    // Capture Session
    internal let captureSession = AVCaptureSession()
    internal let defaultPreset:AVCaptureSession.Preset
    internal let audioCaptureSession = AVCaptureSession()
    // Inputs
    internal var videoDeviceInput: AVCaptureDeviceInput?
    internal var audioDeviceInput: AVCaptureDeviceInput?
    internal var photoOutput: AVCapturePhotoOutput?
    internal var videoOutput: AVCaptureVideoDataOutput?
    internal var audioOutput: AVCaptureAudioDataOutput?
    // CameraView+RecordView
    internal var isRecording = false
    internal var recordingSession: RecordingSession?
    // CameraView+TakePhoto
    internal var photoCaptureDelegates: [PhotoCaptureDelegate] = []
    // CameraView+Zoom
    internal var pinchGestureRecognizer: UIPinchGestureRecognizer?
    internal var pinchScaleOffset: CGFloat = 1.0
    
    internal let cameraQueue = CameraQueues.cameraQueue
    internal let videoQueue = CameraQueues.videoQueue
    internal let audioQueue = CameraQueues.audioQueue
    internal let sensorOrientationChecker = RNSensorOrientationChecker()
    
    internal var previewView: PreviewView?
    
    internal var codeStringValue: String?
    
    /// Returns whether the AVCaptureSession is currently running (reflected by isActive)
    var isRunning: Bool {
        return captureSession.isRunning
    }
    
    // pragma MARK: Setup
    override public init(frame: CGRect) {
        defaultPreset = captureSession.sessionPreset
        super.init(frame: frame)
        
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(sessionRuntimeError),
                                               name: .AVCaptureSessionRuntimeError,
                                               object: captureSession)
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(sessionRuntimeError),
                                               name: .AVCaptureSessionRuntimeError,
                                               object: audioCaptureSession)
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(audioSessionInterrupted),
                                               name: AVAudioSession.interruptionNotification,
                                               object: AVAudioSession.sharedInstance)
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(onOrientationChanged),
                                               name: UIDevice.orientationDidChangeNotification,
                                               object: nil)
        
        setupPreviewView()
    }
    
    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) is not implemented.")
    }
    
    deinit {
        debugPrint("CameraView.deinit")
        NotificationCenter.default.removeObserver(self,
                                                  name: .AVCaptureSessionRuntimeError,
                                                  object: captureSession)
        NotificationCenter.default.removeObserver(self,
                                                  name: .AVCaptureSessionRuntimeError,
                                                  object: audioCaptureSession)
        NotificationCenter.default.removeObserver(self,
                                                  name: AVAudioSession.interruptionNotification,
                                                  object: AVAudioSession.sharedInstance)
        NotificationCenter.default.removeObserver(self,
                                                  name: UIDevice.orientationDidChangeNotification,
                                                  object: nil)
        sensorOrientationChecker.stop()
    }
    
    override public func willMove(toSuperview newSuperview: UIView?) {
        super.willMove(toSuperview: newSuperview)
        if newSuperview != nil {
            sensorOrientationChecker.start()
            if !isMounted {
                isMounted = true
                guard let onViewReady = onViewReady else {
                    return
                }
                onViewReady(nil)
            }
        } else {
            sensorOrientationChecker.stop()
        }
    }
    
    override public func layoutSubviews() {
        if let previewView = previewView {
            previewView.frame = frame
            previewView.bounds = bounds
        }
    }
    
    func setupPreviewView() {
        // Normal iOS PreviewView is lighter and more performant (YUV Format, GPU only)
        if previewView is PreviewView { return }
        previewView?.removeFromSuperview()
        previewView = PreviewView(frame: frame, session: captureSession)
        addSubview(previewView!)
    }
    
    // pragma MARK: Props updating
    override public final func didSetProps(_ changedProps: [String]!) {
        ReactLogger.log(level: .info, message: "Updating \(changedProps.count) prop(s)...")
        let shouldReconfigure = changedProps.contains { propsThatRequireReconfiguration.contains($0) }
        let shouldReconfigureFormat = shouldReconfigure || changedProps.contains("format")
        let shouldReconfigureAudioSession = changedProps.contains("audio")
        
        let willReconfigure = shouldReconfigure || shouldReconfigureFormat
        
        let shouldCheckActive = willReconfigure || changedProps.contains("isActive") || captureSession.isRunning != isActive
        let shouldUpdateTorch = willReconfigure || changedProps.contains("torch") || shouldCheckActive
        let shouldUpdateZoom = willReconfigure || changedProps.contains("zoom") || shouldCheckActive
        let shouldUpdateVideoStabilization = willReconfigure || changedProps.contains("videoStabilizationMode")
        let shouldUpdateOrientation = willReconfigure || changedProps.contains("orientation")
        
        if shouldReconfigure ||
            shouldReconfigureAudioSession ||
            shouldCheckActive ||
            shouldUpdateTorch ||
            shouldUpdateZoom ||
            shouldReconfigureFormat ||
            shouldUpdateVideoStabilization ||
            shouldUpdateOrientation {
            // Video Configuration
            cameraQueue.async {
                if shouldReconfigure {
                    self.configureCaptureSession()
                }
                if shouldReconfigureFormat {
                    self.configureFormat()
                }
                if shouldUpdateVideoStabilization, let videoStabilizationMode = self.videoStabilizationMode as String? {
                    self.captureSession.setVideoStabilizationMode(videoStabilizationMode)
                }
                
                if shouldUpdateZoom {
                    let zoomClamped = max(min(CGFloat(self.zoom.doubleValue), self.maxAvailableZoom), self.minAvailableZoom)
                    self.zoom(factor: zoomClamped, animated: false)
                    self.pinchScaleOffset = zoomClamped
                }
                
                if shouldCheckActive && self.captureSession.isRunning != self.isActive {
                    if self.isActive {
                        ReactLogger.log(level: .info, message: "Starting Session...")
                        self.captureSession.startRunning()
                        ReactLogger.log(level: .info, message: "Started Session!")
                    } else {
                        ReactLogger.log(level: .info, message: "Stopping Session...")
                        self.captureSession.stopRunning()
                        ReactLogger.log(level: .info, message: "Stopped Session!")
                    }
                }
                
                if shouldUpdateOrientation {
                    self.updateOrientation()
                }
                
                // This is a wack workaround, but if I immediately set torch mode after `startRunning()`, the session isn't quite ready yet and will ignore torch.
                if shouldUpdateTorch {
                    self.cameraQueue.asyncAfter(deadline: .now() + 0.1) {
                        self.setTorchMode(self.torch)
                    }
                }
            }
            
            // Audio Configuration
            if shouldReconfigureAudioSession {
                audioQueue.async {
                    self.configureAudioSession()
                }
            }
        }
    }
    
    internal final func setTorchMode(_ torchMode: String) {
        guard let device = videoDeviceInput?.device else {
            invokeOnError(.session(.cameraNotReady))
            return
        }
        guard var torchMode = AVCaptureDevice.TorchMode(withString: torchMode) else {
            invokeOnError(.parameter(.invalid(unionName: "TorchMode", receivedValue: torch)))
            return
        }
        if !captureSession.isRunning {
            torchMode = .off
        }
        if device.torchMode == torchMode {
            // no need to run the whole lock/unlock bs
            return
        }
        if !device.hasTorch || !device.isTorchAvailable {
            if torchMode == .off {
                // ignore it, when it's off and not supported, it's off.
                return
            } else {
                // torch mode is .auto or .on, but no torch is available.
                invokeOnError(.device(.torchUnavailable))
                return
            }
        }
        do {
            try device.lockForConfiguration()
            device.torchMode = torchMode
            if torchMode == .on {
                try device.setTorchModeOn(level: 1.0)
            }
            device.unlockForConfiguration()
        } catch let error as NSError {
            invokeOnError(.device(.configureError), cause: error)
            return
        }
    }
    
    @objc
    func onOrientationChanged() {
        updateOrientation()
    }
    
    // pragma MARK: Event Invokers
    internal final func invokeOnError(_ error: CameraError, cause: NSError? = nil) {
        ReactLogger.log(level: .error, message: "Invoking onError(): \(error.message)")
        guard let onError = onError else { return }
        
        var causeDictionary: [String: Any]?
        if let cause = cause {
            causeDictionary = [
                "code": cause.code,
                "domain": cause.domain,
                "message": cause.description,
                "details": cause.userInfo,
            ]
        }
        onError([
            "code": error.code,
            "message": error.message,
            "cause": causeDictionary ?? NSNull(),
        ])
    }
    
    internal final func invokeOnInitialized() {
        ReactLogger.log(level: .info, message: "Camera initialized!")
        guard let onInitialized = onInitialized else { return }
        onInitialized([String: Any]())
    }
    
    internal final func invokeOnReadCode(code: String) {
        ReactLogger.log(level: .error, message: "Invoking onReadCode(): \(code)")
        guard let onReadCode = onReadCode else { return }
        onReadCode(["code": code])
    }
}
