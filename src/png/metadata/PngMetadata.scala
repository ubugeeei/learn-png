package png

import java.nio.charset.StandardCharsets
import png.Binary.*
import png.PngError.InvalidImage

/** Rendering intent carried by the sRGB chunk from [[https://www.w3.org/TR/png-3/#11sRGB PNG §11.3.3.5]].
  */
enum RenderingIntent(val code: Int) derives CanEqual:
  case Perceptual extends RenderingIntent(0)
  case RelativeColorimetric extends RenderingIntent(1)
  case Saturation extends RenderingIntent(2)
  case AbsoluteColorimetric extends RenderingIntent(3)

object RenderingIntent:
  private[png] def fromCode(code: Int): Either[PngError, RenderingIntent] = values
    .find(_.code == code)
    .toRight(InvalidImage(s"invalid sRGB rendering intent $code"))

/** Physical pixel density from [[https://www.w3.org/TR/png-3/#11pHYs PNG §11.3.5.3]]. */
final case class PixelDensity private (x: Long, y: Long, unitIsMetre: Boolean)

object PixelDensity:
  def apply(x: Long, y: Long, unitIsMetre: Boolean): Either[PngError, PixelDensity] =
    if x < 0 || x > 0xffffffffL || y < 0 || y > 0xffffffffL then
      Left(InvalidImage("pixel density must fit unsigned 32-bit integers"))
    else Right(new PixelDensity(x, y, unitIsMetre))

/** A Latin-1 keyword/value pair represented by a tEXt chunk. */
final case class TextEntry private (keyword: String, value: String)

object TextEntry:
  def apply(keyword: String, value: String): Either[PngError, TextEntry] =
    if keyword.isEmpty || keyword.length > 79 then Left(InvalidImage("text keyword length must be 1..79"))
    else if keyword.head == ' ' || keyword.last == ' ' || keyword.contains("  ") then
      Left(InvalidImage("text keyword may not have leading, trailing, or consecutive spaces"))
    else if !latin1(keyword) || !latin1(value) || keyword.contains('\u0000') || value.contains('\u0000') then
      Left(InvalidImage("tEXt keyword and value must be NUL-free Latin-1"))
    else Right(new TextEntry(keyword, value))

  private def latin1(value: String): Boolean = value.forall(_ <= 0xff)

/** An unknown ancillary chunk whose safe-to-copy bit permits preservation. */
final class PreservedChunk private[png] (val chunkType: ChunkType, private val payload: Array[Byte]):
  def data: Array[Byte] = payload.clone()
  override def equals(other: Any): Boolean =
    other match
      case that: PreservedChunk =>
        chunkType == that.chunkType && java.util.Arrays.equals(payload, that.payload)
      case _ =>
        false
  override def hashCode(): Int = 31 * chunkType.hashCode + java.util.Arrays.hashCode(payload)

/** Metadata supported by the document API.
  *
  * Gamma is the encoded image gamma, constrained to a positive value representable by gAMA's integer
  * `gamma * 100000`. Unknown safe-to-copy chunks are retained for loss-aware transcoding.
  */
final case class PngMetadata private (
    gamma: Option[Double],
    renderingIntent: Option[RenderingIntent],
    pixelDensity: Option[PixelDensity],
    text: Vector[TextEntry],
    preserved: Vector[PreservedChunk]
)

object PngMetadata:
  val empty: PngMetadata = new PngMetadata(None, None, None, Vector.empty, Vector.empty)

  def apply(
      gamma: Option[Double] = None,
      renderingIntent: Option[RenderingIntent] = None,
      pixelDensity: Option[PixelDensity] = None,
      text: Vector[TextEntry] = Vector.empty
  ): Either[PngError, PngMetadata] =
    gamma match
      case Some(value) if !value.isFinite || value <= 0 || value * 100000 > 0xffffffffL =>
        Left(InvalidImage("gamma must be positive, finite, and representable by gAMA"))
      case _ =>
        Right(new PngMetadata(gamma, renderingIntent, pixelDensity, text, Vector.empty))

  private[png] def decode(chunks: Vector[Chunk]): Either[PngError, PngMetadata] =
    for
      gamma <- optionalSingle(chunks, ChunkType.gAMA, parseGamma)
      intent <- optionalSingle(chunks, ChunkType.sRGB, parseIntent)
      density <- optionalSingle(chunks, ChunkType.pHYs, parseDensity)
      text <- sequence(chunks.filter(_.chunkType == ChunkType.tEXt).map(chunk => parseText(chunk.data)))
      preserved = chunks.collect:
        case chunk if isUnknownSafe(chunk.chunkType) =>
          new PreservedChunk(chunk.chunkType, chunk.data)
    yield new PngMetadata(gamma, intent, density, text, preserved)

  private[png] def chunks(metadata: PngMetadata): Either[PngError, Vector[Chunk]] =
    val values =
      Vector(
        metadata.gamma.map(value => Chunk(ChunkType.gAMA, math.round(value * 100000).uint32Bytes)),
        metadata.renderingIntent.map(value => Chunk(ChunkType.sRGB, Array(value.code.toByte))),
        metadata.pixelDensity
          .map(value =>
            Chunk(
              ChunkType.pHYs,
              value.x.uint32Bytes ++ value.y.uint32Bytes ++
                Array(
                  (
                    if value.unitIsMetre then 1
                    else 0
                  ).toByte
                )
            )
          )
      ).flatten ++
        metadata.text
          .map: entry =>
            Chunk(
              ChunkType.tEXt,
              entry.keyword.getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte) ++
                entry.value.getBytes(StandardCharsets.ISO_8859_1)
            )
    val known = sequence(values)
    known.flatMap(chunks =>
      sequence(metadata.preserved.map(value => Chunk(value.chunkType, value.data))).map(chunks ++ _)
    )

  private def parseGamma(data: Array[Byte]): Either[PngError, Double] =
    if data.length != 4 then Left(InvalidImage("gAMA length must be 4"))
    else
      Binary
        .Cursor(data)
        .uint32
        .flatMap(value =>
          Either.cond(value != 0, value / 100000.0, InvalidImage("gAMA value must be non-zero"))
        )

  private def parseIntent(data: Array[Byte]): Either[PngError, RenderingIntent] =
    if data.length != 1 then Left(InvalidImage("sRGB length must be 1"))
    else RenderingIntent.fromCode(data(0) & 0xff)

  private def parseDensity(data: Array[Byte]): Either[PngError, PixelDensity] =
    if data.length != 9 then Left(InvalidImage("pHYs length must be 9"))
    else
      val cursor = Binary.Cursor(data)
      for
        x <- cursor.uint32;
        y <- cursor.uint32;
        unit <- cursor.uint8;
        _ <- Either.cond(unit <= 1, (), InvalidImage(s"invalid pHYs unit $unit"));
        result <- PixelDensity(x, y, unit == 1)
      yield result

  private def parseText(data: Array[Byte]): Either[PngError, TextEntry] =
    val separator = data.indexOf(0.toByte)
    if separator < 1 then Left(InvalidImage("tEXt must contain a non-empty keyword and separator"))
    else
      TextEntry(
        new String(data.take(separator), StandardCharsets.ISO_8859_1),
        new String(data.drop(separator + 1), StandardCharsets.ISO_8859_1)
      )

  private def optionalSingle[A](
      chunks: Vector[Chunk],
      kind: ChunkType,
      parse: Array[Byte] => Either[PngError, A]
  ): Either[PngError, Option[A]] =
    chunks.filter(_.chunkType == kind) match
      case Vector() =>
        Right(None)
      case Vector(chunk) =>
        parse(chunk.data).map(Some(_))
      case _ =>
        Left(InvalidImage(s"${kind.name} may occur at most once"))

  private def sequence[A](values: Vector[Either[PngError, A]]): Either[PngError, Vector[A]] =
    values.foldLeft[Either[PngError, Vector[A]]](Right(Vector.empty))((result, value) =>
      for
        existing <- result;
        next <- value
      yield existing :+ next
    )

  private val Known = Set(
    ChunkType.IHDR,
    ChunkType.PLTE,
    ChunkType.IDAT,
    ChunkType.IEND,
    ChunkType.tRNS,
    ChunkType.gAMA,
    ChunkType.sRGB,
    ChunkType.pHYs,
    ChunkType.tEXt,
    ChunkType.zTXt,
    ChunkType.iTXt,
    ChunkType.eXIf,
    ChunkType.tIME
  )
  private def isUnknownSafe(kind: ChunkType): Boolean = kind.isAncillary && kind.isSafeToCopy && !Known(kind)
