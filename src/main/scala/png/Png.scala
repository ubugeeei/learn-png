package png

import java.io.ByteArrayOutputStream
import png.PngError.*

/** Dependency-free PNG encoder and decoder.
  *
  * Encoding produces non-interlaced 8-bit RGBA PNGs. Decoding accepts every
  * standard color type and bit depth in non-interlaced PNGs, including palettes
  * and `tRNS`. The chunk sequence is checked against
  * [[https://www.w3.org/TR/png-3/#5ChunkOrdering PNG §5.6]].
  */
object Png:
  val Signature: Vector[Byte] =
    Vector(137, 80, 78, 71, 13, 10, 26, 10).map(_.toByte)

  def encode(image: Image): Either[PngError, Array[Byte]] =
    val header = Header(
      image.width,
      image.height,
      8,
      ColorType.TruecolorAlpha
    ).toOption.get
    val filtered = ByteArrayOutputStream()
    var previous = Array.emptyByteArray
    image.rows.foreach: pixels =>
      val row = Samples.rgba8Row(pixels)
      val (filter, bytes) =
        Filter.choose(row, previous, header.bytesPerPixelForFiltering)
      filtered.write(filter.code)
      filtered.write(bytes)
      previous = row
    for
      compressed <- Zlib.compress(filtered.toByteArray)
      ihdr <- Chunk(ChunkType.IHDR, header.bytes)
      idat <- Chunk(ChunkType.IDAT, compressed)
      iend <- Chunk(ChunkType.IEND, Array.emptyByteArray)
    yield Signature.toArray ++ ihdr.bytes ++ idat.bytes ++ iend.bytes

  def decode(input: Array[Byte]): Either[PngError, Image] =
    val cursor = Binary.Cursor(input)
    for
      signature <- cursor.take(Signature.length)
      _ <- Either.cond(
        signature.toVector == Signature,
        (),
        InvalidSignature(signature.toVector)
      )
      chunks <- parseChunks(cursor)
      image <- decodeChunks(chunks)
      _ <- Either.cond(
        cursor.remaining == 0,
        (),
        TrailingData(cursor.remaining)
      )
    yield image

  private def parseChunks(
      cursor: Binary.Cursor
  ): Either[PngError, Vector[Chunk]] =
    def loop(chunks: Vector[Chunk]): Either[PngError, Vector[Chunk]] =
      if cursor.remaining == 0 then Left(InvalidChunkOrder("missing IEND"))
      else
        Chunk
          .parse(cursor)
          .flatMap: chunk =>
            val next = chunks :+ chunk
            if chunk.chunkType == ChunkType.IEND then Right(next)
            else loop(next)
    loop(Vector.empty)

  private def decodeChunks(chunks: Vector[Chunk]): Either[PngError, Image] =
    for
      _ <- validateOrder(chunks)
      header <- Header.parse(chunks.head.data)
      _ <- Either.cond(
        !header.interlaced,
        (),
        UnsupportedFeature("Adam7 interlacing")
      )
      transparency = chunks
        .find(_.chunkType == ChunkType.tRNS)
        .fold(Array.emptyByteArray)(_.data)
      palette <- chunks.find(_.chunkType == ChunkType.PLTE) match
        case Some(chunk) => Samples.palette(chunk.data, transparency)
        case None if header.colorType == ColorType.Indexed =>
          Left(InvalidChunkOrder("indexed image requires PLTE"))
        case None => Right(Vector.empty)
      compressed = chunks
        .filter(_.chunkType == ChunkType.IDAT)
        .flatMap(_.data)
        .toArray
      expectedLong = (header.scanlineBytes.toLong + 1) * header.height
      _ <- Either.cond(
        expectedLong <= Int.MaxValue,
        (),
        InvalidImage("decompressed image exceeds JVM limits")
      )
      expected = expectedLong.toInt
      inflated <- Zlib.decompress(compressed, expected)
      _ <- Either.cond(
        inflated.length == expected,
        (),
        InvalidImage(
          s"expected $expected decompressed bytes, found ${inflated.length}"
        )
      )
      pixels <- decodeScanlines(inflated, header, palette, transparency)
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
      .foldLeft[Either[PngError, (Vector[Rgba], Array[Byte])]](
        Right(Vector.empty -> Array.emptyByteArray)
      ):
        case (result, _) =>
          for
            state <- result
            (pixels, previous) = state
            filterCode <- cursor.uint8
            filter <- Filter.fromCode(filterCode)
            encoded <- cursor.take(header.scanlineBytes)
            row = filter.decode(
              encoded,
              previous,
              header.bytesPerPixelForFiltering
            )
            decoded <- Samples.decodeRow(
              row,
              header.width,
              header,
              palette,
              transparency
            )
          yield (pixels ++ decoded, row)
      .map(_._1)

  private def validateOrder(chunks: Vector[Chunk]): Either[PngError, Unit] =
    val names = chunks.map(_.chunkType)
    val idatIndices = names.zipWithIndex.collect {
      case (ChunkType.IDAT, index) => index
    }
    val unknownCritical = chunks.find(chunk =>
      !chunk.chunkType.isAncillary && !Set(
        ChunkType.IHDR,
        ChunkType.PLTE,
        ChunkType.IDAT,
        ChunkType.IEND
      )(chunk.chunkType)
    )
    if names.headOption != Some(ChunkType.IHDR) then
      Left(InvalidChunkOrder("IHDR must be first"))
    else if names.count(_ == ChunkType.IHDR) != 1 then
      Left(InvalidChunkOrder("exactly one IHDR is required"))
    else if names.lastOption != Some(ChunkType.IEND) || names.count(
        _ == ChunkType.IEND
      ) != 1
    then Left(InvalidChunkOrder("IEND must occur exactly once and last"))
    else if chunks.last.length != 0 then
      Left(InvalidChunkLength("IEND", chunks.last.length))
    else if idatIndices.isEmpty then
      Left(InvalidChunkOrder("at least one IDAT is required"))
    else if idatIndices != (idatIndices.head to idatIndices.last).toVector then
      Left(InvalidChunkOrder("IDAT chunks must be consecutive"))
    else if names.count(_ == ChunkType.PLTE) > 1 then
      Left(InvalidChunkOrder("PLTE may occur at most once"))
    else if names.indexOf(ChunkType.PLTE) > idatIndices.head then
      Left(InvalidChunkOrder("PLTE must precede IDAT"))
    else
      unknownCritical.fold[Either[PngError, Unit]](Right(()))(chunk =>
        Left(UnsupportedFeature(s"critical chunk ${chunk.chunkType.name}"))
      )
