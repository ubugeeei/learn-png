package png

/** A failure produced while validating, encoding, or decoding a PNG datastream.
  *
  * Public codec operations return errors as values, making malformed input an
  * ordinary and exhaustively matchable result rather than an exception-based
  * side channel.
  */
enum PngError derives CanEqual:
  case InvalidSignature(actual: Vector[Byte])
  case UnexpectedEnd(offset: Int, needed: Int, available: Int)
  case InvalidChunkType(reason: String)
  case InvalidChunkLength(chunkType: String, length: Long)
  case CrcMismatch(chunkType: String, expected: Long, actual: Long)
  case InvalidHeader(reason: String)
  case InvalidChunkOrder(reason: String)
  case UnsupportedFeature(reason: String)
  case InvalidImage(reason: String)
  case InvalidFilter(value: Int)
  case CompressionFailure(reason: String)
  case TrailingData(bytes: Int)

  def message: String = this match
    case InvalidSignature(_)                => "the PNG signature is invalid"
    case UnexpectedEnd(at, need, available) =>
      s"unexpected end at byte $at: need $need bytes, have $available"
    case InvalidChunkType(reason)         => s"invalid chunk type: $reason"
    case InvalidChunkLength(kind, length) =>
      s"invalid $kind chunk length: $length"
    case CrcMismatch(kind, expected, actual) =>
      f"CRC mismatch in $kind: expected 0x$expected%08x, computed 0x$actual%08x"
    case InvalidHeader(reason)      => s"invalid IHDR: $reason"
    case InvalidChunkOrder(reason)  => s"invalid chunk order: $reason"
    case UnsupportedFeature(reason) => s"unsupported PNG feature: $reason"
    case InvalidImage(reason)       => s"invalid image: $reason"
    case InvalidFilter(value)       => s"invalid filter method: $value"
    case CompressionFailure(reason) => s"zlib failure: $reason"
    case TrailingData(bytes)        => s"$bytes trailing bytes after IEND"
