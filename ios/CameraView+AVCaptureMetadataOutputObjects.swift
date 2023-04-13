//
//  CameraView+AVCaptureMetadataOutputObjects.swift
//  react-native-simple-camera
//
//  Created by Denis Zamataev on 10.04.23.
//
import Foundation
import AVFoundation

// Swift code
extension CameraView: AVCaptureMetadataOutputObjectsDelegate {

    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        
        for metadataObject in metadataObjects {
            if let machineReadableCode = metadataObject as? AVMetadataMachineReadableCodeObject, isSupportedBarcodeType(machineReadableCode.type.rawValue) {
                
                let code = self.previewView?.videoPreviewLayer.transformedMetadataObject(for: metadataObject) as? AVMetadataMachineReadableCodeObject
                let stringValue = code?.stringValue
                if stringValue != self.codeStringValue {
                    invokeOnReadCode(code: stringValue ?? "")
                    self.codeStringValue = stringValue
                }
            }
        }
    }

    func isSupportedBarcodeType(_ currentType: String) -> Bool {
        let supportedBarcodeTypes = [
            AVMetadataObject.ObjectType.qr,
//            AVMetadataObject.ObjectType.upce,
//            AVMetadataObject.ObjectType.code39,
//            AVMetadataObject.ObjectType.code39Mod43,
//            AVMetadataObject.ObjectType.ean13,
//            AVMetadataObject.ObjectType.ean8,
//            AVMetadataObject.ObjectType.code93,
//            AVMetadataObject.ObjectType.code128,
//            AVMetadataObject.ObjectType.pdf417,
//            AVMetadataObject.ObjectType.aztec,
//            AVMetadataObject.ObjectType.dataMatrix,
//            AVMetadataObject.ObjectType.interleaved2of5
        ]
        
        return supportedBarcodeTypes.contains(where: { $0.rawValue == currentType })
    }
}
