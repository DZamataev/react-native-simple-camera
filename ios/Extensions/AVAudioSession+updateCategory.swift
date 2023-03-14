

import AVFoundation
import Foundation

extension AVAudioSession {
  /**
   Calls [setCategory] if the given category or options are not equal to the currently set category and options.
   */
  func updateCategory(_ category: AVAudioSession.Category, options: AVAudioSession.CategoryOptions = []) throws {
    if self.category != category || categoryOptions.rawValue != options.rawValue {
      ReactLogger.log(level: .info,
                      message: "Changing AVAudioSession category from \(self.category.rawValue) -> \(category.rawValue)")
      try setCategory(category, options: options)
    }
  }
}
