package png

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import scala.util.Try
import png.PngError.InvalidImage

/** UTF-8 textual information represented by an iTXt chunk. */
final case class InternationalText private (
    keyword: String,
    languageTag: String,
    translatedKeyword: String,
    value: String
)

object InternationalText:
  def apply(
      keyword: String,
      languageTag: String,
      translatedKeyword: String,
      value: String
  ): Either[PngError, InternationalText] =
    TextEntry(keyword, "").flatMap: _ =>
      if !languageTag.forall(character => character <= 0x7f && character != '\u0000') then
        Left(InvalidImage("iTXt language tag must be NUL-free ASCII"))
      else if translatedKeyword.contains('\u0000') || value.contains('\u0000') then
        Left(InvalidImage("iTXt translated keyword and value must be NUL-free"))
      else Right(new InternationalText(keyword, languageTag, translatedKeyword, value))

/** Opaque Exif bytes from an eXIf chunk, defensively copied at both boundaries. */
final class ExifProfile private (private val payload: Array[Byte]):
  def bytes: Array[Byte] = payload.clone()
  override def equals(other: Any): Boolean = other match
    case that: ExifProfile => java.util.Arrays.equals(payload, that.payload)
    case _ => false
  override def hashCode(): Int = java.util.Arrays.hashCode(payload)

object ExifProfile:
  def apply(bytes: Array[Byte]): Either[PngError, ExifProfile] =
    Either.cond(bytes.nonEmpty, new ExifProfile(bytes.clone()), InvalidImage("eXIf must not be empty"))

/** Metadata whose wire formats need UTF-8, compression, calendar, or opaque profile handling. */
final case class ExtendedMetadata(
    internationalText: Vector[InternationalText],
    modificationTime: Option[LocalDateTime],
    exif: Option[ExifProfile]
)

object ExtendedMetadata:
  val empty: ExtendedMetadata = ExtendedMetadata(Vector.empty, None, None)

  private[png] def decode(chunks: Vector[Chunk]): Either[PngError, ExtendedMetadata] =
    for
      text <- sequence(chunks.filter(_.chunkType == ChunkType.iTXt).map(chunk => parseText(chunk.data)))
      time <- single(chunks, ChunkType.tIME, parseTime)
      exif <- single(chunks, ChunkType.eXIf, ExifProfile.apply)
    yield ExtendedMetadata(text, time, exif)

  private[png] def chunks(metadata: ExtendedMetadata): Either[PngError, Vector[Chunk]] =
    val values = metadata.internationalText.map(encodeText) ++
      metadata.modificationTime.map(value => Chunk(ChunkType.tIME, encodeTime(value))) ++
      metadata.exif.map(value => Chunk(ChunkType.eXIf, value.bytes))
    sequence(values)

  private def parseText(data: Array[Byte]): Either[PngError, InternationalText] =
    val keywordEnd = data.indexOf(0.toByte)
    if keywordEnd < 1 || keywordEnd + 3 > data.length then Left(InvalidImage("iTXt header is truncated"))
    else
      val compressed = data(keywordEnd + 1) & 0xff
      val method = data(keywordEnd + 2) & 0xff
      val languageEnd = data.indexOf(0.toByte, keywordEnd + 3)
      val translatedEnd = if languageEnd < 0 then -1 else data.indexOf(0.toByte, languageEnd + 1)
      if compressed > 1 || method != 0 || languageEnd < 0 || translatedEnd < 0 then
        Left(InvalidImage("invalid iTXt compression fields or separators"))
      else
        val content = data.drop(translatedEnd + 1)
        val decoded = if compressed == 1 then Zlib.decompress(content, 16 * 1024 * 1024) else Right(content)
        decoded.flatMap(bytes =>
          InternationalText(
            new String(data.take(keywordEnd), StandardCharsets.ISO_8859_1),
            new String(data.slice(keywordEnd + 3, languageEnd), StandardCharsets.US_ASCII),
            new String(data.slice(languageEnd + 1, translatedEnd), StandardCharsets.UTF_8),
            new String(bytes, StandardCharsets.UTF_8)
          )
        )

  private def encodeText(value: InternationalText): Either[PngError, Chunk] =
    Chunk(
      ChunkType.iTXt,
      value.keyword.getBytes(StandardCharsets.ISO_8859_1) ++ Array[Byte](0, 0, 0) ++
        value.languageTag.getBytes(StandardCharsets.US_ASCII) ++ Array(0.toByte) ++
        value.translatedKeyword.getBytes(StandardCharsets.UTF_8) ++ Array(0.toByte) ++
        value.value.getBytes(StandardCharsets.UTF_8)
    )

  private def parseTime(data: Array[Byte]): Either[PngError, LocalDateTime] =
    if data.length != 7 then Left(InvalidImage("tIME length must be 7"))
    else
      val year = ((data(0) & 0xff) << 8) | (data(1) & 0xff)
      Try(
        LocalDateTime.of(
          year,
          data(2) & 0xff,
          data(3) & 0xff,
          data(4) & 0xff,
          data(5) & 0xff,
          data(6) & 0xff
        )
      ).toEither.left.map(_ => InvalidImage("tIME contains an invalid calendar date"))

  private def encodeTime(value: LocalDateTime): Array[Byte] =
    Array(
      (value.getYear >>> 8).toByte,
      value.getYear.toByte,
      value.getMonthValue.toByte,
      value.getDayOfMonth.toByte,
      value.getHour.toByte,
      value.getMinute.toByte,
      value.getSecond.toByte
    )

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
