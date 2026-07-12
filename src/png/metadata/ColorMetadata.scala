package png

import java.nio.charset.StandardCharsets
import png.Binary.*
import png.PngError.InvalidImage

/** One xy chromaticity pair, encoded by PNG as unsigned fixed-point values with divisor 100000. */
final case class Chromaticity private (x: Double, y: Double)

object Chromaticity:
  def apply(x: Double, y: Double): Either[PngError, Chromaticity] =
    if !x.isFinite || !y.isFinite || x < 0 || y < 0 || x > 0.8 || y > 0.8 then
      Left(InvalidImage("chromaticity coordinates must be finite values in 0..0.8"))
    else Right(new Chromaticity(x, y))

/** Primary chromaticities and white point from cHRM. */
final case class Chromaticities(
    white: Chromaticity,
    red: Chromaticity,
    green: Chromaticity,
    blue: Chromaticity
)

/** Embedded ICC profile from iCCP, with immutable profile bytes. */
final class IccProfile private (val name: String, private val payload: Array[Byte]):
  def bytes: Array[Byte] = payload.clone()
  override def equals(other: Any): Boolean = other match
    case that: IccProfile => name == that.name && java.util.Arrays.equals(payload, that.payload)
    case _ => false
  override def hashCode(): Int = 31 * name.hashCode + java.util.Arrays.hashCode(payload)

object IccProfile:
  def apply(name: String, bytes: Array[Byte]): Either[PngError, IccProfile] =
    TextEntry(name, "").flatMap: _ =>
      Either.cond(bytes.nonEmpty, new IccProfile(name, bytes.clone()), InvalidImage("ICC profile is empty"))

/** Significant source bits for the channels of one PNG color type. */
final case class SignificantBits private (channels: Vector[Int])

object SignificantBits:
  def apply(channels: Vector[Int], header: Header): Either[PngError, SignificantBits] =
    val expected = header.colorType match
      case ColorType.Grayscale => 1
      case ColorType.Truecolor => 3
      case ColorType.Indexed => 3
      case ColorType.GrayscaleAlpha => 2
      case ColorType.TruecolorAlpha => 4
    val maximum = if header.colorType == ColorType.Indexed then 8 else header.bitDepth
    if channels.length != expected then
      Left(InvalidImage(s"sBIT requires $expected channels for color type ${header.colorType.code}"))
    else if channels.exists(value => value < 1 || value > maximum) then
      Left(InvalidImage(s"sBIT values must be in 1..$maximum"))
    else Right(new SignificantBits(channels))

/** Four coding-independent video signal identifiers carried by cICP. */
final case class CodingIndependentCodePoints(
    colorPrimaries: Int,
    transferFunction: Int,
    matrixCoefficients: Int,
    fullRange: Boolean
)

/** HDR mastering display volume values carried by mDCV. */
final case class MasteringDisplayVolume(
    red: (Int, Int),
    green: (Int, Int),
    blue: (Int, Int),
    white: (Int, Int),
    maximumLuminance: Long,
    minimumLuminance: Long
)

/** Maximum content and frame-average light levels from cLLI. */
final case class ContentLightLevel(maximumContentLight: Long, maximumFrameAverageLight: Long)

/** Typed PNG color-space metadata from the Third Edition color information chunks. */
final case class ColorMetadata(
    chromaticities: Option[Chromaticities],
    iccProfile: Option[IccProfile],
    significantBits: Option[SignificantBits],
    coding: Option[CodingIndependentCodePoints],
    masteringDisplay: Option[MasteringDisplayVolume],
    contentLight: Option[ContentLightLevel]
)

object ColorMetadata:
  val empty: ColorMetadata = ColorMetadata(None, None, None, None, None, None)

  private[png] def decode(chunks: Vector[Chunk], header: Header): Either[PngError, ColorMetadata] =
    for
      chromaticities <- single(chunks, ChunkType.cHRM, parseChromaticities)
      profile <- single(chunks, ChunkType.iCCP, parseProfile)
      bits <- single(chunks, ChunkType.sBIT, data => SignificantBits(data.map(_ & 0xff).toVector, header))
      coding <- single(chunks, ChunkType.cICP, parseCoding)
      mastering <- single(chunks, ChunkType.mDCV, parseMastering)
      light <- single(chunks, ChunkType.cLLI, parseLight)
    yield ColorMetadata(chromaticities, profile, bits, coding, mastering, light)

  private[png] def chunks(metadata: ColorMetadata): Either[PngError, Vector[Chunk]] =
    sequence(
      Vector(
        metadata.chromaticities.map(value => Chunk(ChunkType.cHRM, encodeChromaticities(value))),
        metadata.iccProfile.map(encodeProfile),
        metadata.significantBits.map(value => Chunk(ChunkType.sBIT, value.channels.map(_.toByte).toArray)),
        metadata.coding.map(value =>
          Chunk(
            ChunkType.cICP,
            Array(
              value.colorPrimaries.toByte,
              value.transferFunction.toByte,
              value.matrixCoefficients.toByte,
              (if value.fullRange then 1 else 0).toByte
            )
          )
        ),
        metadata.masteringDisplay.map(value => Chunk(ChunkType.mDCV, encodeMastering(value))),
        metadata.contentLight.map(value =>
          Chunk(
            ChunkType.cLLI,
            value.maximumContentLight.uint32Bytes ++ value.maximumFrameAverageLight.uint32Bytes
          )
        )
      ).flatten
    )

  private def parseChromaticities(data: Array[Byte]): Either[PngError, Chromaticities] =
    if data.length != 32 then Left(InvalidImage("cHRM length must be 32"))
    else
      val cursor = Binary.Cursor(data)
      def point: Either[PngError, Chromaticity] = for
        x <- cursor.uint32; y <- cursor.uint32; value <- Chromaticity(x / 100000.0, y / 100000.0)
      yield value
      for white <- point; red <- point; green <- point; blue <- point
      yield Chromaticities(white, red, green, blue)

  private def encodeChromaticities(value: Chromaticities): Array[Byte] =
    Vector(value.white, value.red, value.green, value.blue)
      .flatMap(point => math.round(point.x * 100000).uint32Bytes ++ math.round(point.y * 100000).uint32Bytes)
      .toArray

  private def parseProfile(data: Array[Byte]): Either[PngError, IccProfile] =
    val separator = data.indexOf(0.toByte)
    if separator < 1 || separator + 2 > data.length || data(separator + 1) != 0 then
      Left(InvalidImage("iCCP requires a profile name and compression method 0"))
    else
      Zlib
        .decompress(data.drop(separator + 2), 64 * 1024 * 1024)
        .flatMap(bytes => IccProfile(new String(data.take(separator), StandardCharsets.ISO_8859_1), bytes))

  private def encodeProfile(value: IccProfile): Either[PngError, Chunk] =
    Zlib
      .compress(value.bytes)
      .flatMap(bytes =>
        Chunk(ChunkType.iCCP, value.name.getBytes(StandardCharsets.ISO_8859_1) ++ Array[Byte](0, 0) ++ bytes)
      )

  private def parseCoding(data: Array[Byte]): Either[PngError, CodingIndependentCodePoints] =
    if data.length != 4 || (data(3) & 0xff) > 1 then
      Left(InvalidImage("cICP requires four bytes and a boolean range flag"))
    else Right(CodingIndependentCodePoints(data(0) & 0xff, data(1) & 0xff, data(2) & 0xff, data(3) == 1))

  private def parseMastering(data: Array[Byte]): Either[PngError, MasteringDisplayVolume] =
    if data.length != 24 then Left(InvalidImage("mDCV length must be 24"))
    else
      val cursor = Binary.Cursor(data)
      def coordinate: Either[PngError, Int] =
        cursor.take(2).map(bytes => ((bytes(0) & 0xff) << 8) | (bytes(1) & 0xff))
      def point: Either[PngError, (Int, Int)] = for x <- coordinate; y <- coordinate yield x -> y
      for
        red <- point; green <- point; blue <- point; white <- point; max <- cursor.uint32;
        min <- cursor.uint32
      yield MasteringDisplayVolume(red, green, blue, white, max, min)

  private def encodeMastering(value: MasteringDisplayVolume): Array[Byte] =
    def point(pair: (Int, Int)): Array[Byte] =
      Array((pair._1 >>> 8).toByte, pair._1.toByte, (pair._2 >>> 8).toByte, pair._2.toByte)
    point(value.red) ++ point(value.green) ++ point(value.blue) ++ point(
      value.white
    ) ++ value.maximumLuminance.uint32Bytes ++ value.minimumLuminance.uint32Bytes

  private def parseLight(data: Array[Byte]): Either[PngError, ContentLightLevel] =
    if data.length != 8 then Left(InvalidImage("cLLI length must be 8"))
    else
      for max <- Binary.Cursor(data).uint32; average <- Binary.Cursor(data.drop(4)).uint32
      yield ContentLightLevel(max, average)

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
