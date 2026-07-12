package png

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import scala.util.Try
import png.PngError.{IoFailure, ResourceLimit}

/** Bounded stream adapters shared by public PNG operations. */
private[png] object PngStreams:
  private val BufferSize = 32 * 1024

  def read(
      input: InputStream,
      options: DecoderOptions
  ): Either[PngError, Array[Byte]] =
    Try:
      val output = ByteArrayOutputStream()
      val buffer = new Array[Byte](BufferSize)
      var total = 0L
      var count = input.read(buffer)
      while count != -1 do
        total += count
        if total > options.maximumFileBytes then
          throw LimitExceeded(total, options.maximumFileBytes)
        output.write(buffer, 0, count)
        count = input.read(buffer)
      output.toByteArray
    .toEither.left.map:
      case LimitExceeded(actual, maximum) =>
        ResourceLimit("file bytes", actual, maximum)
      case error => IoFailure("read PNG stream", message(error))

  def write(output: OutputStream, bytes: Array[Byte]): Either[PngError, Unit] =
    Try:
      output.write(bytes)
      output.flush()
    .toEither.left.map(error => IoFailure("write PNG stream", message(error)))

  private final case class LimitExceeded(actual: Long, maximum: Long)
      extends RuntimeException

  private def message(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
