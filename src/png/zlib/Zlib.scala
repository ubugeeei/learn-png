package png

import java.io.ByteArrayOutputStream
import java.util.zip.{ Deflater, DeflaterOutputStream, Inflater, InflaterOutputStream }
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

  def decompress(input: Array[Byte], maximumBytes: Int): Either[PngError, Array[Byte]] = Try:
    val output = BoundedOutputStream(maximumBytes)
    Using.resource(InflaterOutputStream(output, Inflater()))(_.write(input))
    output.bytes
  .toEither.left
    .map(error => CompressionFailure(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)))

  final private class BoundedOutputStream(limit: Int) extends java.io.OutputStream:
    private val output = ByteArrayOutputStream()
    override def write(value: Int): Unit =
      ensureCapacity(1)
      output.write(value)
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit =
      ensureCapacity(length)
      output.write(bytes, offset, length)
    def bytes: Array[Byte] = output.toByteArray
    private def ensureCapacity(additional: Int): Unit =
      if output.size().toLong + additional > limit then
        throw IllegalArgumentException(s"decompressed data exceeds $limit bytes")
