
import AVFoundation

private var delegatesReferences: [NSObject] = []

// MARK: - PhotoCaptureDelegate

func imageOrientation(from deviceOrientation: UIInterfaceOrientation) -> Float? {
    switch deviceOrientation {
    case .portrait:
        return nil
    case .landscapeLeft:
        return .pi / 2
    case .landscapeRight:
        return .pi / -2
    case .portraitUpsideDown:
        return .pi
    case .unknown:
        return nil
    @unknown default:
        return nil
    }
}

func normalizedImage(image: UIImage, deviceOrientation: UIInterfaceOrientation) -> UIImage? {
    if deviceOrientation == .portrait {
        return image
    } else {
        guard let radians = imageOrientation(from: deviceOrientation) else {
            return image
        }
        
        var newSize = CGRect(
            origin: CGPoint.zero,
            size: image.size
        ).applying(CGAffineTransform(rotationAngle: CGFloat(radians))).size
        // Trim off the extremely small float value to prevent core graphics from rounding it up
        newSize.width = floor(newSize.width)
        newSize.height = floor(newSize.height)
        
        UIGraphicsBeginImageContextWithOptions(newSize, false, image.scale)
        let context = UIGraphicsGetCurrentContext()!
        
        // Move origin to middle
        context.translateBy(x: newSize.width/2, y: newSize.height/2)
        // Rotate around middle
        context.rotate(by: CGFloat(radians))
        // Draw the image at its center
        image.draw(in: CGRect(
            x: -image.size.width/2,
            y: -image.size.height/2,
            width: image.size.width,
            height: image.size.height
        ))
        
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return newImage
    }
}

class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let promise: Promise
    private let deviceOrientation: UIInterfaceOrientation
    private let fixOrientation: Bool
    
    required init(
        promise: Promise,
        deviceOrientation: UIInterfaceOrientation,
        fixOrientation: Bool
    ) {
        self.deviceOrientation = deviceOrientation
        self.promise = promise
        self.fixOrientation = fixOrientation
        super.init()
        delegatesReferences.append(self)
    }
    
    func photoOutput(
        _: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        defer {
            delegatesReferences.removeAll(where: { $0 == self })
        }
        if let error = error as NSError? {
            promise.reject(error: .capture(.unknown(message: error.description)), cause: error)
            return
        }
        
        let error = ErrorPointer(nilLiteral: ())
        guard let tempFilePath = RCTTempFilePath("jpeg", error)
        else {
            promise.reject(error: .capture(.createTempFileError), cause: error?.pointee)
            return
        }
        let url = URL(string: "file://\(tempFilePath)")!
        
        
        guard var data = photo.fileDataRepresentation() else {
            promise.reject(error: .capture(.fileError))
            return
        }
        
        if fixOrientation, let orientation = imageOrientation(from: deviceOrientation) {
            debugPrint("imageOrientation orientation: \(orientation)")
            let img = normalizedImage(
                image: UIImage(data: data)!,
                deviceOrientation: deviceOrientation
            )!
            data = img.pngData()!
        }
        
        
        do {
            try data.write(to: url)
            let exif = photo.metadata["{Exif}"] as? [String: Any]
            let width = exif?["PixelXDimension"]
            let height = exif?["PixelYDimension"]
            
            promise.resolve([
                "path": tempFilePath,
                "width": width as Any,
                "height": height as Any,
                "isRawPhoto": photo.isRawPhoto,
                "metadata": photo.metadata,
                "thumbnail": photo.embeddedThumbnailPhotoFormat as Any,
            ])
        } catch {
            promise.reject(error: .capture(.fileError), cause: error as NSError)
        }
    }
    
    func photoOutput(
        _: AVCapturePhotoOutput,
        didFinishCaptureFor _: AVCaptureResolvedPhotoSettings,
        error: Error?
    ) {
        defer { delegatesReferences.removeAll(where: { $0 == self }) }
        if let error = error as NSError? {
            promise.reject(error: .capture(.unknown(message: error.description)), cause: error)
            return
        }
    }
}
