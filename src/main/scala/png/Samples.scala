package png

import png.PngError.InvalidImage

/** Conversion between packed PNG samples and the public RGBA raster. */
private[png] object Samples:
  def rgba8Row(pixels: Vector[Rgba]): Array[Byte] =
    pixels.toArray.flatMap(pixel =>
      Array(pixel.red, pixel.green, pixel.blue, pixel.alpha).map(_.toByte)
    )

  def decodeRow(
      bytes: Array[Byte],
      width: Int,
      header: Header,
      palette: Vector[Rgba],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba]] =
    val samples =
      unpack(bytes, width * header.colorType.channels, header.bitDepth)
    header.colorType match
      case ColorType.Grayscale =>
        val transparent =
          if transparency.length == 2 then Some(unsigned16(transparency, 0))
          else None
        Right(
          samples.map(value =>
            Rgba.unsafe(
              scale(value, header.bitDepth),
              scale(value, header.bitDepth),
              scale(value, header.bitDepth),
              if transparent.contains(value) then 0 else 255
            )
          )
        )
      case ColorType.Truecolor =>
        val transparent =
          if transparency.length == 6 then
            Some(
              (
                unsigned16(transparency, 0),
                unsigned16(transparency, 2),
                unsigned16(transparency, 4)
              )
            )
          else None
        Right(
          samples
            .grouped(3)
            .map { group =>
              val Vector(red, green, blue) = group: @unchecked
              Rgba.unsafe(
                scale(red, header.bitDepth),
                scale(green, header.bitDepth),
                scale(blue, header.bitDepth),
                if transparent.contains((red, green, blue)) then 0 else 255
              )
            }
            .toVector
        )
      case ColorType.Indexed =>
        samples.foldLeft[Either[PngError, Vector[Rgba]]](Right(Vector.empty)):
          case (result, index) =>
            for
              pixels <- result
              color <- palette
                .lift(index)
                .toRight(InvalidImage(s"palette index $index is out of range"))
            yield pixels :+ color
      case ColorType.GrayscaleAlpha =>
        Right(
          samples
            .grouped(2)
            .map { group =>
              val Vector(gray, alpha) = group: @unchecked
              val value = scale(gray, header.bitDepth)
              Rgba.unsafe(value, value, value, scale(alpha, header.bitDepth))
            }
            .toVector
        )
      case ColorType.TruecolorAlpha =>
        Right(
          samples
            .grouped(4)
            .map { group =>
              val Vector(red, green, blue, alpha) = group: @unchecked
              Rgba.unsafe(
                scale(red, header.bitDepth),
                scale(green, header.bitDepth),
                scale(blue, header.bitDepth),
                scale(alpha, header.bitDepth)
              )
            }
            .toVector
        )

  def palette(
      data: Array[Byte],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba]] =
    if data.isEmpty || data.length % 3 != 0 || data.length > 768 then
      Left(
        InvalidImage(
          s"PLTE length ${data.length} must be a non-zero multiple of 3 up to 768"
        )
      )
    else
      Right(
        data
          .grouped(3)
          .zipWithIndex
          .map { case (rgb, index) =>
            Rgba.unsafe(
              rgb(0) & 0xff,
              rgb(1) & 0xff,
              rgb(2) & 0xff,
              transparency.lift(index).fold(255)(_ & 0xff)
            )
          }
          .toVector
      )

  private def unpack(bytes: Array[Byte], count: Int, depth: Int): Vector[Int] =
    if depth == 8 then bytes.iterator.take(count).map(_ & 0xff).toVector
    else if depth == 16 then
      bytes.grouped(2).take(count).map(pair => unsigned16(pair, 0)).toVector
    else
      val mask = (1 << depth) - 1
      Vector.tabulate(count): index =>
        val bit = index * depth
        ((bytes(bit / 8) & 0xff) >>> (8 - depth - bit % 8)) & mask

  private def unsigned16(bytes: Array[Byte], offset: Int): Int =
    ((bytes(offset) & 0xff) << 8) | (bytes(offset + 1) & 0xff)

  private def scale(value: Int, depth: Int): Int =
    if depth == 8 then value
    else if depth == 16 then value >>> 8
    else value * 255 / ((1 << depth) - 1)
