package png

import java.io.ByteArrayOutputStream
import java.util.zip.{ Deflater, DeflaterOutputStream, Inflater }
import scala.util.{ Try, Using }
import png.PngError.CompressionFailure

/** The zlib transport required for IDAT by [[https://www.w3.org/TR/png-3/#10Compression PNG §10]].
  */
private[png] object Zlib:
  def compress(input: Array[Byte], level: Int = Deflater.DEFAULT_COMPRESSION): Either[PngError, Array[Byte]] =
    Try:
      val output = ByteArrayOutputStream()
      Using.resource(DeflaterOutputStream(output, Deflater(level)))(_.write(input))
      output.toByteArray
    .toEither.left
      .map(error => CompressionFailure(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)))

  /** Inflate exactly one complete zlib stream and reject dictionaries, truncation, and trailing bytes. */
  def decompress(input: Array[Byte], maximumBytes: Int): Either[PngError, Array[Byte]] = Try:
    val inflater = Inflater()
    val output = ByteArrayOutputStream()
    val buffer = new Array[Byte](32 * 1024)
    try
      inflater.setInput(input)
      while !inflater.finished() && !inflater.needsDictionary() && !inflater.needsInput() do
        val count = inflater.inflate(buffer)
        if count == 0 && !inflater.finished() then
          throw IllegalArgumentException("zlib stream made no progress")
        if output.size().toLong + count > maximumBytes then
          throw IllegalArgumentException(s"decompressed data exceeds $maximumBytes bytes")
        output.write(buffer, 0, count)
      if inflater.needsDictionary() then throw IllegalArgumentException("zlib dictionary is not permitted")
      if !inflater.finished() then throw IllegalArgumentException("truncated zlib stream")
      if inflater.getRemaining != 0 then
        throw IllegalArgumentException(s"${inflater.getRemaining} trailing zlib bytes")
      output.toByteArray
    finally inflater.end()
  .toEither.left.map(error =>
    CompressionFailure(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
  )
