package png

import png.PngError.InvalidArguments

/** Resource limits applied before expensive PNG allocations. */
final case class DecoderOptions private (
    maximumFileBytes: Long,
    maximumWidth: Int,
    maximumHeight: Int,
    maximumPixels: Long,
    maximumInflatedBytes: Long
)

object DecoderOptions:
  val default: DecoderOptions = new DecoderOptions(
    maximumFileBytes = 256L * 1024 * 1024,
    maximumWidth = 100_000,
    maximumHeight = 100_000,
    maximumPixels = 100_000_000,
    maximumInflatedBytes = 512L * 1024 * 1024
  )

  def apply(
      maximumFileBytes: Long = default.maximumFileBytes,
      maximumWidth: Int = default.maximumWidth,
      maximumHeight: Int = default.maximumHeight,
      maximumPixels: Long = default.maximumPixels,
      maximumInflatedBytes: Long = default.maximumInflatedBytes
  ): Either[PngError, DecoderOptions] =
    val values = List(
      "maximum file bytes" -> maximumFileBytes,
      "maximum width" -> maximumWidth.toLong,
      "maximum height" -> maximumHeight.toLong,
      "maximum pixels" -> maximumPixels,
      "maximum inflated bytes" -> maximumInflatedBytes
    )
    values.collectFirst { case (name, value) if value <= 0 => name } match
      case Some(name) => Left(InvalidArguments(s"$name must be positive"))
      case None       =>
        Right(
          new DecoderOptions(
            maximumFileBytes,
            maximumWidth,
            maximumHeight,
            maximumPixels,
            maximumInflatedBytes
          )
        )
