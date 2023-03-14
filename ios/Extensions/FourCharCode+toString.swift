

extension FourCharCode {
  func toString() -> String {
    var s = String(UnicodeScalar((self >> 24) & 255)!)
    s.append(String(UnicodeScalar((self >> 16) & 255)!))
    s.append(String(UnicodeScalar((self >> 8) & 255)!))
    s.append(String(UnicodeScalar(self & 255)!))
    return (s)
  }
}
