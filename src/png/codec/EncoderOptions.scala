package png

import java.util.zip.Deflater
import png.PngError.InvalidImage

/** Tunable choices that do not change decoded pixels.
  *
  * @param interlaced
  *   emit the seven Adam7 passes described by
  *   [[https://www.w3.org/TR/png-3/#8Interlace PNG §8]]
  * @param compressionLevel
  *   zlib level from 0 (stored) through 9 (maximum search)
  * @param maximumIdatPayload
  *   largest physical IDAT payload; one zlib stream may span many chunks
  */
final case class EncoderOptions private (
    interlaced: Boolean,
    compressionLevel: Int,
    maximumIdatPayload: Int
)

object EncoderOptions:
  val default: EncoderOptions = new EncoderOptions(
    interlaced = false,
    compressionLevel = Deflater.DEFAULT_COMPRESSION,
    maximumIdatPayload = 1024 * 1024
  )

  def apply(
      interlaced: Boolean = false,
      compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
      maximumIdatPayload: Int = 1024 * 1024
  ): Either[PngError, EncoderOptions] =
    if compressionLevel < Deflater.DEFAULT_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION
    then
      Left(
        InvalidImage(s"compression level $compressionLevel is outside -1..9")
      )
    else if maximumIdatPayload <= 0 then
      Left(InvalidImage("maximum IDAT payload must be positive"))
    else
      Right(
        new EncoderOptions(interlaced, compressionLevel, maximumIdatPayload)
      )
