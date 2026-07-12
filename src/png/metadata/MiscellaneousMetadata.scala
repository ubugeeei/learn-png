package png

import java.nio.charset.StandardCharsets
import png.PngError.InvalidImage

/** Color-type-dependent suggested background from bKGD. */
enum BackgroundColor derives CanEqual:
  case Grayscale(sample: Int)
  case Truecolor(red: Int, green: Int, blue: Int)
  case PaletteIndex(index: Int)

/** One suggested-palette entry normalized to sixteen-bit channels. */
final case class SuggestedPaletteEntry(
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int,
    frequency: Int
)

/** One named sPLT chunk. `sampleDepth` is exactly 8 or 16. */
final case class SuggestedPalette private (
    name: String,
    sampleDepth: Int,
    entries: Vector[SuggestedPaletteEntry]
)

object SuggestedPalette:
  def apply(
      name: String,
      sampleDepth: Int,
      entries: Vector[SuggestedPaletteEntry]
  ): Either[PngError, SuggestedPalette] =
    TextEntry(name, "").flatMap: _ =>
      val maximum = if sampleDepth == 8 then 0xff else 0xffff
      if sampleDepth != 8 && sampleDepth != 16 then Left(InvalidImage("sPLT sample depth must be 8 or 16"))
      else if entries.isEmpty then Left(InvalidImage("sPLT must contain at least one entry"))
      else if entries.exists(entry =>
          Vector(entry.red, entry.green, entry.blue, entry.alpha).exists(value =>
            value < 0 || value > maximum
          ) ||
            entry.frequency < 0 || entry.frequency > 0xffff
        )
      then Left(InvalidImage(s"sPLT entries exceed $sampleDepth-bit channel or frequency bounds"))
      else Right(new SuggestedPalette(name, sampleDepth, entries))

/** Latin-1 text that should be emitted in compressed zTXt form. */
final case class CompressedText private (entry: TextEntry)

object CompressedText:
  def apply(keyword: String, value: String): Either[PngError, CompressedText] =
    TextEntry(keyword, value).map(new CompressedText(_))

/** Remaining standardized static-image ancillary information. */
final case class MiscellaneousMetadata(
    background: Option[BackgroundColor],
    histogram: Option[Vector[Int]],
    suggestedPalettes: Vector[SuggestedPalette],
    compressedText: Vector[CompressedText]
)

object MiscellaneousMetadata:
  val empty: MiscellaneousMetadata = MiscellaneousMetadata(None, None, Vector.empty, Vector.empty)

  private[png] def decode(
      chunks: Vector[Chunk],
      header: Header,
      paletteEntries: Int
  ): Either[PngError, MiscellaneousMetadata] =
    for
      background <- single(chunks, ChunkType.bKGD, data => parseBackground(data, header, paletteEntries))
      histogram <- single(chunks, ChunkType.hIST, data => parseHistogram(data, header, paletteEntries))
      palettes <- sequence(
        chunks.filter(_.chunkType == ChunkType.sPLT).map(chunk => parsePalette(chunk.data))
      )
      _ <- Either.cond(
        palettes.map(_.name).distinct.length == palettes.length,
        (),
        InvalidImage("sPLT palette names must be unique")
      )
      text <- sequence(chunks.filter(_.chunkType == ChunkType.zTXt).map(chunk => parseText(chunk.data)))
    yield MiscellaneousMetadata(background, histogram, palettes, text)

  private[png] def chunks(
      metadata: MiscellaneousMetadata,
      header: Header,
      paletteEntries: Int
  ): Either[PngError, Vector[Chunk]] =
    for
      background <- metadata.background match
        case Some(value) =>
          encodeBackground(value, header, paletteEntries).flatMap(Chunk(ChunkType.bKGD, _)).map(Some(_))
        case None => Right(None)
      histogram <- metadata.histogram match
        case Some(values) =>
          encodeHistogram(values, header, paletteEntries).flatMap(Chunk(ChunkType.hIST, _)).map(Some(_))
        case None => Right(None)
      palettes <- sequence(metadata.suggestedPalettes.map(encodePalette))
      text <- sequence(metadata.compressedText.map(encodeText))
    yield background.toVector ++ histogram.toVector ++ palettes ++ text

  private def parseBackground(
      data: Array[Byte],
      header: Header,
      paletteEntries: Int
  ): Either[PngError, BackgroundColor] = header.colorType match
    case ColorType.Grayscale | ColorType.GrayscaleAlpha =>
      requireLength(data, 2, "bKGD").map(_ => BackgroundColor.Grayscale(read16(data, 0)))
    case ColorType.Truecolor | ColorType.TruecolorAlpha =>
      requireLength(data, 6, "bKGD").map(_ =>
        BackgroundColor.Truecolor(read16(data, 0), read16(data, 2), read16(data, 4))
      )
    case ColorType.Indexed =>
      requireLength(data, 1, "bKGD").flatMap: _ =>
        val index = data(0) & 0xff
        Either.cond(
          index < paletteEntries,
          BackgroundColor.PaletteIndex(index),
          InvalidImage("bKGD palette index is out of range")
        )

  private def encodeBackground(
      value: BackgroundColor,
      header: Header,
      paletteEntries: Int
  ): Either[PngError, Array[Byte]] = (header.colorType, value) match
    case (ColorType.Grayscale | ColorType.GrayscaleAlpha, BackgroundColor.Grayscale(sample)) =>
      sampleBytes(sample, header.bitDepth).map(value => unsigned16(value).toArray)
    case (ColorType.Truecolor | ColorType.TruecolorAlpha, BackgroundColor.Truecolor(red, green, blue)) =>
      sequence(Vector(red, green, blue).map(sample => sampleBytes(sample, header.bitDepth)))
        .map(_.flatMap(unsigned16).toArray)
    case (ColorType.Indexed, BackgroundColor.PaletteIndex(index)) if index >= 0 && index < paletteEntries =>
      Right(Array(index.toByte))
    case _ => Left(InvalidImage("bKGD representation does not match the encoded color type"))

  private def parseHistogram(
      data: Array[Byte],
      header: Header,
      paletteEntries: Int
  ): Either[PngError, Vector[Int]] =
    if header.colorType != ColorType.Indexed then
      Left(InvalidImage("hIST is permitted only for indexed color"))
    else if data.length != paletteEntries * 2 then
      Left(InvalidImage("hIST entry count must equal PLTE entries"))
    else Right(data.grouped(2).map(pair => read16(pair, 0)).toVector)

  private def encodeHistogram(
      values: Vector[Int],
      header: Header,
      paletteEntries: Int
  ): Either[PngError, Array[Byte]] =
    if header.colorType != ColorType.Indexed || values.length != paletteEntries then
      Left(InvalidImage("hIST requires one entry per indexed-color palette entry"))
    else if values.exists(value => value < 0 || value > 0xffff) then
      Left(InvalidImage("hIST values must fit uint16"))
    else Right(values.flatMap(unsigned16).toArray)

  private def parsePalette(data: Array[Byte]): Either[PngError, SuggestedPalette] =
    val separator = data.indexOf(0.toByte)
    if separator < 1 || separator + 2 > data.length then Left(InvalidImage("sPLT header is truncated"))
    else
      val name = new String(data.take(separator), StandardCharsets.ISO_8859_1)
      val depth = data(separator + 1) & 0xff
      val width = if depth == 8 then 6 else if depth == 16 then 10 else 0
      val payload = data.drop(separator + 2)
      if width == 0 || payload.isEmpty || payload.length % width != 0 then
        Left(InvalidImage("sPLT entries do not match sample depth"))
      else
        val entries = payload
          .grouped(width)
          .map: entry =>
            if depth == 8 then
              SuggestedPaletteEntry(
                entry(0) & 0xff,
                entry(1) & 0xff,
                entry(2) & 0xff,
                entry(3) & 0xff,
                read16(entry, 4)
              )
            else
              SuggestedPaletteEntry(
                read16(entry, 0),
                read16(entry, 2),
                read16(entry, 4),
                read16(entry, 6),
                read16(entry, 8)
              )
        SuggestedPalette(name, depth, entries.toVector)

  private def encodePalette(value: SuggestedPalette): Either[PngError, Chunk] =
    val entries = value.entries.flatMap: entry =>
      if value.sampleDepth == 8 then
        Vector(entry.red.toByte, entry.green.toByte, entry.blue.toByte, entry.alpha.toByte) ++ unsigned16(
          entry.frequency
        )
      else
        unsigned16(entry.red) ++ unsigned16(entry.green) ++ unsigned16(entry.blue) ++ unsigned16(
          entry.alpha
        ) ++ unsigned16(entry.frequency)
    Chunk(
      ChunkType.sPLT,
      value.name.getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte, value.sampleDepth.toByte) ++ entries
    )

  private def parseText(data: Array[Byte]): Either[PngError, CompressedText] =
    val separator = data.indexOf(0.toByte)
    if separator < 1 || separator + 2 > data.length || data(separator + 1) != 0 then
      Left(InvalidImage("zTXt requires keyword and compression method 0"))
    else
      Zlib
        .decompress(data.drop(separator + 2), 16 * 1024 * 1024)
        .flatMap(bytes =>
          CompressedText(
            new String(data.take(separator), StandardCharsets.ISO_8859_1),
            new String(bytes, StandardCharsets.ISO_8859_1)
          )
        )

  private def encodeText(value: CompressedText): Either[PngError, Chunk] =
    Zlib
      .compress(value.entry.value.getBytes(StandardCharsets.ISO_8859_1))
      .flatMap(bytes =>
        Chunk(
          ChunkType.zTXt,
          value.entry.keyword.getBytes(StandardCharsets.ISO_8859_1) ++ Array[Byte](0, 0) ++ bytes
        )
      )

  private def sampleBytes(value: Int, depth: Int): Either[PngError, Int] =
    Either.cond(
      value >= 0 && value <= ((1L << depth) - 1),
      value,
      InvalidImage(s"sample exceeds bit depth $depth")
    )
  private def unsigned16(value: Int): Vector[Byte] = Vector((value >>> 8).toByte, value.toByte)
  private def read16(bytes: Array[Byte], offset: Int): Int =
    ((bytes(offset) & 0xff) << 8) | (bytes(offset + 1) & 0xff)
  private def requireLength(data: Array[Byte], expected: Int, name: String): Either[PngError, Unit] =
    Either.cond(data.length == expected, (), InvalidImage(s"$name length must be $expected"))
  private def single[A](
      chunks: Vector[Chunk],
      kind: ChunkType,
      parse: Array[Byte] => Either[PngError, A]
  ): Either[PngError, Option[A]] = chunks.filter(_.chunkType == kind) match
    case Vector() => Right(None)
    case Vector(chunk) => parse(chunk.data).map(Some(_))
    case _ => Left(InvalidImage(s"${kind.name} may occur at most once"))
  private def sequence[A](values: Vector[Either[PngError, A]]): Either[PngError, Vector[A]] =
    values.foldLeft[Either[PngError, Vector[A]]](Right(Vector.empty))((result, value) =>
      for existing <- result; next <- value yield existing :+ next
    )
