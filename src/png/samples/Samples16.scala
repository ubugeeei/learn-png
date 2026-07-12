package png

import png.PngError.InvalidImage

/** Lossless conversion from packed PNG samples to the 16-bit public raster. */
private[png] object Samples16:
  def rgba16Row(pixels: Vector[Rgba16]): Array[Byte] =
    pixels.toArray.flatMap: pixel =>
      Array(pixel.red, pixel.green, pixel.blue, pixel.alpha).flatMap(unsigned16)

  def decodeRow(
      bytes: Array[Byte],
      width: Int,
      header: Header,
      palette: Vector[Rgba16],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba16]] =
    val samples = unpack(bytes, width * header.colorType.channels, header.bitDepth)
    header.colorType match
      case ColorType.Grayscale =>
        val transparent = if transparency.length == 2 then Some(read16(transparency, 0)) else None
        Right(samples.map: sample =>
          val value = scale(sample, header.bitDepth)
          Rgba16.unsafe(value, value, value, if transparent.contains(sample) then 0 else 0xffff))
      case ColorType.Truecolor =>
        val transparent =
          if transparency.length == 6 then
            Some((read16(transparency, 0), read16(transparency, 2), read16(transparency, 4)))
          else None
        Right(
          samples
            .grouped(3)
            .map: group =>
              val Vector(red, green, blue) = group: @unchecked
              Rgba16.unsafe(
                scale(red, header.bitDepth),
                scale(green, header.bitDepth),
                scale(blue, header.bitDepth),
                if transparent.contains((red, green, blue)) then 0 else 0xffff
              )
            .toVector
        )
      case ColorType.Indexed =>
        samples.foldLeft[Either[PngError, Vector[Rgba16]]](Right(Vector.empty)):
          case (result, index) =>
            for
              pixels <- result
              color <- palette.lift(index).toRight(InvalidImage(s"palette index $index is out of range"))
            yield pixels :+ color
      case ColorType.GrayscaleAlpha =>
        Right(
          samples
            .grouped(2)
            .map: group =>
              val Vector(gray, alpha) = group: @unchecked
              val value = scale(gray, header.bitDepth)
              Rgba16.unsafe(value, value, value, scale(alpha, header.bitDepth))
            .toVector
        )
      case ColorType.TruecolorAlpha =>
        Right(
          samples
            .grouped(4)
            .map: group =>
              val Vector(red, green, blue, alpha) = group: @unchecked
              Rgba16.unsafe(
                scale(red, header.bitDepth),
                scale(green, header.bitDepth),
                scale(blue, header.bitDepth),
                scale(alpha, header.bitDepth)
              )
            .toVector
        )

  def palette(data: Array[Byte], transparency: Array[Byte]): Either[PngError, Vector[Rgba16]] =
    if data.isEmpty || data.length % 3 != 0 || data.length > 768 then
      Left(InvalidImage(s"PLTE length ${data.length} must be a non-zero multiple of 3 up to 768"))
    else
      Right(
        data
          .grouped(3)
          .zipWithIndex
          .map: (rgb, index) =>
            Rgba16.unsafe(
              (rgb(0) & 0xff) * 257,
              (rgb(1) & 0xff) * 257,
              (rgb(2) & 0xff) * 257,
              transparency.lift(index).fold(0xffff)(byte => (byte & 0xff) * 257)
            )
          .toVector
      )

  private def unpack(bytes: Array[Byte], count: Int, depth: Int): Vector[Int] =
    if depth == 8 then bytes.iterator.take(count).map(_ & 0xff).toVector
    else if depth == 16 then bytes.grouped(2).take(count).map(pair => read16(pair, 0)).toVector
    else
      val mask = (1 << depth) - 1
      Vector.tabulate(count): index =>
        val bit = index * depth
        ((bytes(bit / 8) & 0xff) >>> (8 - depth - bit % 8)) & mask

  private def scale(value: Int, depth: Int): Int =
    if depth == 16 then value
    else if depth == 8 then value * 257
    else value * 0xffff / ((1 << depth) - 1)

  private def unsigned16(value: Int): Array[Byte] = Array((value >>> 8).toByte, value.toByte)
  private def read16(bytes: Array[Byte], offset: Int): Int =
    ((bytes(offset) & 0xff) << 8) | (bytes(offset + 1) & 0xff)
