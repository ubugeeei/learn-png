package png

import java.io.ByteArrayOutputStream
import png.PngError.*

/** Lossless 16-bit RGBA delivery and encoding engine. */
private[png] object Codec16:
  def decode(input: Array[Byte], options: DecoderOptions): Either[PngError, Image16] =
    for
      validated <- Codec.validated(input, options)
      (chunks, header) = validated
      transparency = chunks.find(_.chunkType == ChunkType.tRNS).fold(Array.emptyByteArray)(_.data)
      palette <- chunks.find(_.chunkType == ChunkType.PLTE) match
        case Some(chunk) => Samples16.palette(chunk.data, transparency)
        case None if header.colorType == ColorType.Indexed =>
          Left(InvalidChunkOrder("indexed image requires PLTE"))
        case None => Right(Vector.empty)
      compressed = chunks.filter(_.chunkType == ChunkType.IDAT).flatMap(_.data).toArray
      expectedLong =
        if header.interlaced then Adam7.decompressedSize(header)
        else (header.scanlineBytes.toLong + 1) * header.height
      _ <- Codec.within("inflated bytes", expectedLong, options.maximumInflatedBytes)
      _ <- Either.cond(
        expectedLong <= Int.MaxValue,
        (),
        InvalidImage("decompressed image exceeds JVM limits")
      )
      inflated <- Zlib.decompress(compressed, expectedLong.toInt)
      _ <- Either.cond(
        inflated.length == expectedLong,
        (),
        InvalidImage(s"expected $expectedLong decompressed bytes, found ${inflated.length}")
      )
      pixels <-
        if header.interlaced then decodeInterlaced(inflated, header, palette, transparency)
        else
          decodeRows(
            Binary.Cursor(inflated),
            header.width,
            header.height,
            header.scanlineBytes,
            header,
            palette,
            transparency
          )
      image <- Image16(header.width, header.height, pixels)
    yield image

  def encode(image: Image16, options: EncoderOptions): Either[PngError, Array[Byte]] =
    val header =
      Header(image.width, image.height, 16, ColorType.TruecolorAlpha, options.interlaced).toOption.get
    val filtered =
      if options.interlaced then interlacedRows(image, header)
      else filterRows(image.rows.map(Samples16.rgba16Row), header.bytesPerPixelForFiltering)
    for
      compressed <- Zlib.compress(filtered, options.compressionLevel)
      ihdr <- Chunk(ChunkType.IHDR, header.bytes)
      idats <- sequence(
        compressed.grouped(options.maximumIdatPayload).toVector.map(payload => Chunk(ChunkType.IDAT, payload))
      )
      iend <- Chunk(ChunkType.IEND, Array.emptyByteArray)
    yield Codec.Signature.toArray ++ ihdr.bytes ++ idats.flatMap(_.bytes) ++ iend.bytes

  private def decodeInterlaced(
      data: Array[Byte],
      header: Header,
      palette: Vector[Rgba16],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba16]] =
    val cursor = Binary.Cursor(data)
    val initial = Vector.fill[Option[Rgba16]](header.width * header.height)(None)
    Adam7.passes
      .foldLeft[Either[PngError, Vector[Option[Rgba16]]]](Right(initial)):
        case (result, pass) if pass.isEmpty(header.width, header.height) => result
        case (result, pass) =>
          val width = pass.width(header.width)
          for
            raster <- result
            decoded <- decodeRows(
              cursor,
              width,
              pass.height(header.height),
              Adam7.scanlineBytes(width, header.bitsPerPixel),
              header,
              palette,
              transparency
            )
            coordinates = pass.coordinates(header.width, header.height).toVector
          yield coordinates
            .zip(decoded)
            .foldLeft(raster):
              case (pixels, ((x, y), pixel)) => pixels.updated(y * header.width + x, Some(pixel))
      .flatMap: raster =>
        raster.foldLeft[Either[PngError, Vector[Rgba16]]](Right(Vector.empty)):
          case (result, Some(pixel)) => result.map(_ :+ pixel)
          case (_, None) => Left(InvalidImage("Adam7 passes did not cover every pixel"))

  private def decodeRows(
      cursor: Binary.Cursor,
      width: Int,
      height: Int,
      rowBytes: Int,
      header: Header,
      palette: Vector[Rgba16],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba16]] =
    (0 until height)
      .foldLeft[Either[PngError, (Vector[Rgba16], Array[Byte])]](
        Right(Vector.empty -> Array.emptyByteArray)
      ):
        case (result, _) =>
          for
            state <- result
            (pixels, previous) = state
            filterCode <- cursor.uint8
            filter <- Filter.fromCode(filterCode)
            encoded <- cursor.take(rowBytes)
            row = filter.decode(encoded, previous, header.bytesPerPixelForFiltering)
            decoded <- Samples16.decodeRow(row, width, header, palette, transparency)
          yield pixels ++ decoded -> row
      .map(_._1)

  private def interlacedRows(image: Image16, header: Header): Array[Byte] =
    Adam7.passes
      .filterNot(_.isEmpty(image.width, image.height))
      .flatMap: pass =>
        val rows = (0 until pass.height(image.height)).map: passY =>
          val y = pass.yStart + passY * pass.yStep
          Samples16.rgba16Row(
            (0 until pass
              .width(image.width)).map(passX => image(pass.xStart + passX * pass.xStep, y)).toVector
          )
        filterRows(rows.toVector, header.bytesPerPixelForFiltering)
      .toArray

  private def filterRows(rows: Vector[Array[Byte]], bytesPerPixel: Int): Array[Byte] =
    val output = ByteArrayOutputStream()
    var previous = Array.emptyByteArray
    rows.foreach: row =>
      val (filter, encoded) = Filter.choose(row, previous, bytesPerPixel)
      output.write(filter.code)
      output.write(encoded)
      previous = row
    output.toByteArray

  private def sequence[A](values: Vector[Either[PngError, A]]): Either[PngError, Vector[A]] =
    values.foldLeft[Either[PngError, Vector[A]]](Right(Vector.empty))((result, value) =>
      for existing <- result; next <- value yield existing :+ next
    )
