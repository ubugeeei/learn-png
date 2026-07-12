package png

import java.io.ByteArrayOutputStream
import java.nio.file.{ Files, Path }
import scala.util.Try
import png.PngError.*

/** Dependency-free PNG encoder and decoder.
  *
  * Encoding produces 8-bit RGBA PNGs with optional Adam7 interlacing. Decoding accepts every standard color
  * type and bit depth, including Adam7, palettes, and `tRNS`. The chunk sequence is checked against
  * [[https://www.w3.org/TR/png-3/#5ChunkOrdering PNG §5.6]].
  */
private[png] object Codec:
  val Signature: Vector[Byte] = Vector(137, 80, 78, 71, 13, 10, 26, 10).map(_.toByte)

  /** Read and decode a complete PNG file. */
  def read(path: Path): Either[PngError, Image] = read(path, DecoderOptions.default)

  def read(path: Path, options: DecoderOptions): Either[PngError, Image] =
    for
      size <- Try(Files.size(path)).toEither.left
        .map(error => IoFailure(s"inspect $path", errorMessage(error)))
      _ <- within("file bytes", size, options.maximumFileBytes)
      bytes <- Try(Files.readAllBytes(path)).toEither.left
        .map(error => IoFailure(s"read $path", errorMessage(error)))
      image <- decode(bytes, options)
    yield image

  /** Encode an image and write it to a file. */
  def write(
      path: Path,
      image: Image,
      options: EncoderOptions = EncoderOptions.default
  ): Either[PngError, Path] = encode(image, options).flatMap(bytes => SafeFiles.write(path, bytes))

  def encode(image: Image): Either[PngError, Array[Byte]] = encode(image, EncoderOptions.default)

  /** Encode pixels and portable ancillary metadata into one datastream. */
  def encodeDocument(document: PngDocument, options: EncoderOptions): Either[PngError, Array[Byte]] =
    for
      imageBytes <- encode(document.image, options)
      metadataChunks <- PngMetadata.chunks(document.metadata)
      extendedChunks <- ExtendedMetadata.chunks(document.extendedMetadata)
      colorChunks <- ColorMetadata.chunks(document.colorMetadata)
    yield
      val afterHeader = Signature.length + 12 + 13
      imageBytes.take(afterHeader) ++ (metadataChunks ++ extendedChunks ++ colorChunks).flatMap(_.bytes) ++
        imageBytes.drop(afterHeader)

  def encode(image: Image, options: EncoderOptions): Either[PngError, Array[Byte]] =
    val header =
      Header(image.width, image.height, 8, ColorType.TruecolorAlpha, options.interlaced).toOption.get
    val filtered =
      if options.interlaced then interlacedScanlines(image, header)
      else ordinaryScanlines(image, header)
    for
      compressed <- Zlib.compress(filtered, options.compressionLevel)
      ihdr <- Chunk(ChunkType.IHDR, header.bytes)
      idats <-
        compressed
          .grouped(options.maximumIdatPayload)
          .toVector
          .foldLeft[Either[PngError, Vector[Chunk]]](Right(Vector.empty)):
            case (result, payload) =>
              for
                chunks <- result;
                chunk <- Chunk(ChunkType.IDAT, payload)
              yield chunks :+ chunk
      iend <- Chunk(ChunkType.IEND, Array.emptyByteArray)
    yield Signature.toArray ++ ihdr.bytes ++ idats.flatMap(_.bytes).toArray ++ iend.bytes

  private def ordinaryScanlines(image: Image, header: Header): Array[Byte] = filteredRows(
    image.rows.map(Samples.rgba8Row),
    header.bytesPerPixelForFiltering
  )

  private def interlacedScanlines(image: Image, header: Header): Array[Byte] =
    Adam7.passes
      .filterNot(_.isEmpty(image.width, image.height))
      .flatMap: pass =>
        val rows = (0 until pass.height(image.height)).map: passY =>
          val y = pass.yStart + passY * pass.yStep
          val pixels = (0 until pass.width(image.width)).map: passX =>
            image(pass.xStart + passX * pass.xStep, y)
          Samples.rgba8Row(pixels.toVector)
        filteredRows(rows.toVector, header.bytesPerPixelForFiltering)
      .toArray

  private def filteredRows(rows: Vector[Array[Byte]], bytesPerPixel: Int): Array[Byte] =
    val output = ByteArrayOutputStream()
    var previous = Array.emptyByteArray
    rows.foreach: row =>
      val (filter, bytes) = Filter.choose(row, previous, bytesPerPixel)
      output.write(filter.code)
      output.write(bytes)
      previous = row
    output.toByteArray

  def decode(input: Array[Byte]): Either[PngError, Image] = decode(input, DecoderOptions.default)

  /** Decode pixels and recognized portable ancillary metadata. */
  def decodeDocument(input: Array[Byte], options: DecoderOptions): Either[PngError, PngDocument] =
    for
      image <- decode(input, options)
      cursor = Binary.Cursor(input)
      _ <- cursor.take(Signature.length)
      chunks <- parseChunks(cursor, options)
      metadata <- PngMetadata.decode(chunks)
      extended <- ExtendedMetadata.decode(chunks)
      header <- Header.parse(chunks.head.data)
      color <- ColorMetadata.decode(chunks, header)
    yield PngDocument(image, metadata, extended, color)

  def decode(input: Array[Byte], options: DecoderOptions): Either[PngError, Image] =
    val cursor = Binary.Cursor(input)
    for
      _ <- within("file bytes", input.length, options.maximumFileBytes)
      signature <- cursor.take(Signature.length)
      _ <- Either.cond(signature.toVector == Signature, (), InvalidSignature(signature.toVector))
      chunks <- parseChunks(cursor, options)
      image <- decodeChunks(chunks, options)
      _ <- Either.cond(cursor.remaining == 0, (), TrailingData(cursor.remaining))
    yield image

  private def parseChunks(
      cursor: Binary.Cursor,
      options: DecoderOptions
  ): Either[PngError, Vector[Chunk]] =
    def loop(chunks: Vector[Chunk]): Either[PngError, Vector[Chunk]] =
      if chunks.length >= options.maximumChunks then
        Left(ResourceLimit("chunk count", chunks.length + 1, options.maximumChunks))
      else if cursor.remaining == 0 then Left(InvalidChunkOrder("missing IEND"))
      else
        Chunk
          .parse(cursor)
          .flatMap: chunk =>
            within("chunk bytes", chunk.length, options.maximumChunkBytes).flatMap: _ =>
              val next = chunks :+ chunk
              if chunk.chunkType == ChunkType.IEND then Right(next) else loop(next)
    loop(Vector.empty)

  /** Parse and structurally validate a datastream for specialized raster decoders. */
  private[png] def validated(
      input: Array[Byte],
      options: DecoderOptions
  ): Either[PngError, (Vector[Chunk], Header)] =
    val cursor = Binary.Cursor(input)
    for
      _ <- within("file bytes", input.length, options.maximumFileBytes)
      signature <- cursor.take(Signature.length)
      _ <- Either.cond(signature.toVector == Signature, (), InvalidSignature(signature.toVector))
      chunks <- parseChunks(cursor, options)
      _ <- Either.cond(cursor.remaining == 0, (), TrailingData(cursor.remaining))
      _ <- validateOrder(chunks)
      header <- Header.parse(chunks.head.data)
      _ <- validateAncillary(chunks, header)
      _ <- within("width", header.width, options.maximumWidth)
      _ <- within("height", header.height, options.maximumHeight)
      _ <- within("pixels", header.width.toLong * header.height, options.maximumPixels)
    yield chunks -> header

  private def decodeChunks(chunks: Vector[Chunk], options: DecoderOptions): Either[PngError, Image] =
    for
      _ <- validateOrder(chunks)
      header <- Header.parse(chunks.head.data)
      _ <- validateAncillary(chunks, header)
      _ <- within("width", header.width, options.maximumWidth)
      _ <- within("height", header.height, options.maximumHeight)
      _ <- within("pixels", header.width.toLong * header.height, options.maximumPixels)
      transparency = chunks.find(_.chunkType == ChunkType.tRNS).fold(Array.emptyByteArray)(_.data)
      palette <-
        chunks.find(_.chunkType == ChunkType.PLTE) match
          case Some(chunk) =>
            Samples.palette(chunk.data, transparency)
          case None if header.colorType == ColorType.Indexed =>
            Left(InvalidChunkOrder("indexed image requires PLTE"))
          case None =>
            Right(Vector.empty)
      compressed = chunks.filter(_.chunkType == ChunkType.IDAT).flatMap(_.data).toArray
      expectedLong =
        if header.interlaced then Adam7.decompressedSize(header)
        else (header.scanlineBytes.toLong + 1) * header.height
      _ <- Either.cond(
        expectedLong <= Int.MaxValue,
        (),
        InvalidImage("decompressed image exceeds JVM limits")
      )
      _ <- within("inflated bytes", expectedLong, options.maximumInflatedBytes)
      expected = expectedLong.toInt
      inflated <- Zlib.decompress(compressed, expected)
      _ <- Either.cond(
        inflated.length == expected,
        (),
        InvalidImage(s"expected $expected decompressed bytes, found ${inflated.length}")
      )
      pixels <-
        if header.interlaced then decodeInterlaced(inflated, header, palette, transparency)
        else decodeScanlines(inflated, header, palette, transparency)
      image <- Image(header.width, header.height, pixels)
    yield image

  private def decodeScanlines(
      data: Array[Byte],
      header: Header,
      palette: Vector[Rgba],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba]] =
    val cursor = Binary.Cursor(data)
    (0 until header.height)
      .foldLeft[Either[PngError, (Vector[Rgba], Array[Byte])]](Right(Vector.empty -> Array.emptyByteArray)):
        case (result, _) =>
          for
            state <- result
            (pixels, previous) = state
            filterCode <- cursor.uint8
            filter <- Filter.fromCode(filterCode)
            encoded <- cursor.take(header.scanlineBytes)
            row = filter.decode(encoded, previous, header.bytesPerPixelForFiltering)
            decoded <- Samples.decodeRow(row, header.width, header, palette, transparency)
          yield (pixels ++ decoded, row)
      .map(_._1)

  private def decodeInterlaced(
      data: Array[Byte],
      header: Header,
      palette: Vector[Rgba],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba]] =
    val cursor = Binary.Cursor(data)
    val initial = Vector.fill[Option[Rgba]](header.width * header.height)(None)
    Adam7.passes
      .foldLeft[Either[PngError, Vector[Option[Rgba]]]](Right(initial)):
        case (result, pass) if pass.isEmpty(header.width, header.height) =>
          result
        case (result, pass) =>
          val passWidth = pass.width(header.width)
          val passHeight = pass.height(header.height)
          val rowBytes = Adam7.scanlineBytes(passWidth, header.bitsPerPixel)
          for
            raster <- result
            decoded <- decodePass(cursor, passWidth, passHeight, rowBytes, header, palette, transparency)
            coordinates = pass.coordinates(header.width, header.height).toVector
            placed =
              coordinates
                .zip(decoded)
                .foldLeft(raster):
                  case (pixels, ((x, y), pixel)) =>
                    pixels.updated(y * header.width + x, Some(pixel))
          yield placed
      .flatMap: raster =>
        raster.foldLeft[Either[PngError, Vector[Rgba]]](Right(Vector.empty)):
          case (result, Some(pixel)) =>
            result.map(_ :+ pixel)
          case (_, None) =>
            Left(InvalidImage("Adam7 passes did not cover every pixel"))

  private def decodePass(
      cursor: Binary.Cursor,
      width: Int,
      height: Int,
      rowBytes: Int,
      header: Header,
      palette: Vector[Rgba],
      transparency: Array[Byte]
  ): Either[PngError, Vector[Rgba]] = (0 until height)
    .foldLeft[Either[PngError, (Vector[Rgba], Array[Byte])]](Right(Vector.empty -> Array.emptyByteArray)):
      case (result, _) =>
        for
          state <- result
          (pixels, previous) = state
          filterCode <- cursor.uint8
          filter <- Filter.fromCode(filterCode)
          encoded <- cursor.take(rowBytes)
          row = filter.decode(encoded, previous, header.bytesPerPixelForFiltering)
          decoded <- Samples.decodeRow(row, width, header, palette, transparency)
        yield (pixels ++ decoded, row)
    .map(_._1)

  private def validateOrder(chunks: Vector[Chunk]): Either[PngError, Unit] =
    val names = chunks.map(_.chunkType)
    val idatIndices = names.zipWithIndex
      .collect { case (ChunkType.IDAT, index) =>
        index
      }
    val unknownCritical = chunks.find(chunk =>
      !chunk.chunkType.isAncillary &&
        !Set(ChunkType.IHDR, ChunkType.PLTE, ChunkType.IDAT, ChunkType.IEND)(chunk.chunkType)
    )
    if names.headOption != Some(ChunkType.IHDR) then Left(InvalidChunkOrder("IHDR must be first"))
    else if names.count(_ == ChunkType.IHDR) != 1 then Left(InvalidChunkOrder("exactly one IHDR is required"))
    else if names.lastOption != Some(ChunkType.IEND) || names.count(_ == ChunkType.IEND) != 1 then
      Left(InvalidChunkOrder("IEND must occur exactly once and last"))
    else if chunks.last.length != 0 then Left(InvalidChunkLength("IEND", chunks.last.length))
    else if idatIndices.isEmpty then Left(InvalidChunkOrder("at least one IDAT is required"))
    else if idatIndices != (idatIndices.head to idatIndices.last).toVector then
      Left(InvalidChunkOrder("IDAT chunks must be consecutive"))
    else if names.count(_ == ChunkType.PLTE) > 1 then Left(InvalidChunkOrder("PLTE may occur at most once"))
    else if names.indexOf(ChunkType.PLTE) > idatIndices.head then
      Left(InvalidChunkOrder("PLTE must precede IDAT"))
    else
      unknownCritical.fold[Either[PngError, Unit]](Right(()))(chunk =>
        Left(UnsupportedFeature(s"critical chunk ${chunk.chunkType.name}"))
      )

  /** Validate color-type-specific ancillary constraints from PNG chapter 11. */
  private def validateAncillary(chunks: Vector[Chunk], header: Header): Either[PngError, Unit] =
    val names = chunks.map(_.chunkType)
    val idat = names.indexOf(ChunkType.IDAT)
    val palette = names.indexOf(ChunkType.PLTE)
    val transparency = chunks.filter(_.chunkType == ChunkType.tRNS)
    val singleton = Vector(ChunkType.tRNS, ChunkType.gAMA, ChunkType.sRGB, ChunkType.pHYs)

    def occursBefore(kind: ChunkType, boundary: Int): Boolean =
      val index = names.indexOf(kind)
      index < 0 || index < boundary

    val colorBoundary = if palette >= 0 then palette else idat
    if singleton.exists(kind => names.count(_ == kind) > 1) then
      Left(InvalidChunkOrder("tRNS, gAMA, sRGB, and pHYs may occur at most once"))
    else if !occursBefore(ChunkType.gAMA, colorBoundary) || !occursBefore(ChunkType.sRGB, colorBoundary)
    then Left(InvalidChunkOrder("gAMA and sRGB must precede PLTE and IDAT"))
    else if !occursBefore(ChunkType.pHYs, idat) || !occursBefore(ChunkType.tRNS, idat) then
      Left(InvalidChunkOrder("pHYs and tRNS must precede IDAT"))
    else if transparency.nonEmpty &&
      Set(ColorType.GrayscaleAlpha, ColorType.TruecolorAlpha)(header.colorType)
    then Left(InvalidImage(s"tRNS is forbidden for color type ${header.colorType.code}"))
    else
      transparency.headOption match
        case Some(chunk) if header.colorType == ColorType.Grayscale && chunk.length != 2 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case Some(chunk) if header.colorType == ColorType.Truecolor && chunk.length != 6 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case Some(chunk) if header.colorType == ColorType.Indexed && chunk.length == 0 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case _ => Right(())

  private def errorMessage(error: Throwable): String = Option(error.getMessage).getOrElse(
    error.getClass.getSimpleName
  )

  private[png] def within(resource: String, actual: Long, maximum: Long): Either[PngError, Unit] =
    Either.cond(
      actual <= maximum,
      (),
      ResourceLimit(resource, actual, maximum)
    )
