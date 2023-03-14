
import AVFoundation

extension AVFrameRateRange {
  /**
   * Returns true if this [AVFrameRateRange] contains the given [fps]
   */
  func includes(fps: Double) -> Bool {
    return fps >= minFrameRate && fps <= maxFrameRate
  }
}
