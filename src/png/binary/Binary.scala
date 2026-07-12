package png

import png.PngError.UnexpectedEnd

/** Big-endian binary primitives used by the PNG chunk format.
  *
  * PNG integers are unsigned and stored most-significant byte first, as
  * specified in
  * [[https://www.w3.org/TR/png-3/#7Integers-and-byte-order PNG §7.1]]. `Long`
  * represents `uint32` because the JVM's `Int` is signed.
  */
private[png] object Binary:
  extension (value: Long)
    def uint32Bytes: Array[Byte] =
      Array(24, 16, 8, 0).map(shift => (value >>> shift).toByte)

  extension (value: Int)
    def uint16Bytes: Array[Byte] = Array((value >>> 8).toByte, value.toByte)

  final class Cursor private (
      private val input: Array[Byte],
      private var position: Int
  ):
    def offset: Int = position
    def remaining: Int = input.length - position

    def take(count: Int): Either[PngError, Array[Byte]] =
      if count < 0 || remaining < count then
        Left(UnexpectedEnd(position, count, remaining))
      else
        val result =
          java.util.Arrays.copyOfRange(input, position, position + count)
        position += count
        Right(result)

    def uint8: Either[PngError, Int] = take(1).map(_(0) & 0xff)

    def uint32: Either[PngError, Long] =
      take(4).map: bytes =>
        bytes.foldLeft(0L)((result, byte) => (result << 8) | (byte & 0xffL))

  object Cursor:
    def apply(input: Array[Byte]): Cursor = new Cursor(input.clone(), 0)
