package png

import png.Binary.*
import png.PngError.InvalidHeader

/** PNG color types and their permitted sample depths.
  *
  * The numeric values and valid combinations come from [[https://www.w3.org/TR/png-3/#11IHDR PNG §11.2.2]].
  */
enum ColorType(val code: Int, val channels: Int, val bitDepths: Set[Int]) derives CanEqual:
  case Grayscale extends ColorType(0, 1, Set(1, 2, 4, 8, 16))
  case Truecolor extends ColorType(2, 3, Set(8, 16))
  case Indexed extends ColorType(3, 1, Set(1, 2, 4, 8))
  case GrayscaleAlpha extends ColorType(4, 2, Set(8, 16))
  case TruecolorAlpha extends ColorType(6, 4, Set(8, 16))

object ColorType:
  def fromCode(code: Int): Either[PngError, ColorType] = ColorType.values
    .find(_.code == code)
    .toRight(InvalidHeader(s"unknown color type $code"))

/** The semantic content of the mandatory 13-byte IHDR chunk. */
final case class Header private (
    width: Int,
    height: Int,
    bitDepth: Int,
    colorType: ColorType,
    interlaced: Boolean
):
  def bitsPerPixel: Int = bitDepth * colorType.channels
  def bytesPerPixelForFiltering: Int = math.max(1, (bitsPerPixel + 7) / 8)
  def scanlineBytes: Int = ((width.toLong * bitsPerPixel + 7) / 8).toInt

  private[png] def bytes: Array[Byte] =
    width.toLong.uint32Bytes ++ height.toLong.uint32Bytes ++
      Array(
        bitDepth.toByte,
        colorType.code.toByte,
        0.toByte,
        0.toByte,
        (
          if interlaced then 1
          else 0
        ).toByte
      )

object Header:
  def apply(
      width: Int,
      height: Int,
      bitDepth: Int,
      colorType: ColorType,
      interlaced: Boolean = false
  ): Either[PngError, Header] =
    if width <= 0 || height <= 0 then Left(InvalidHeader("width and height must be positive"))
    else if !colorType.bitDepths(bitDepth) then
      Left(InvalidHeader(s"bit depth $bitDepth is invalid for color type ${colorType.code}"))
    else if width.toLong * bitDepth * colorType.channels > Int.MaxValue.toLong * 8 then
      Left(InvalidHeader("scanline is too large"))
    else Right(new Header(width, height, bitDepth, colorType, interlaced))

  private[png] def parse(data: Array[Byte]): Either[PngError, Header] =
    if data.length != 13 then Left(InvalidHeader(s"length must be 13, found ${data.length}"))
    else
      val cursor = Binary.Cursor(data)
      for
        width <- cursor.uint32
        height <- cursor.uint32
        depth <- cursor.uint8
        colorCode <- cursor.uint8
        color <- ColorType.fromCode(colorCode)
        compression <- cursor.uint8
        filter <- cursor.uint8
        interlace <- cursor.uint8
        _ <- Either.cond(width <= Int.MaxValue, (), InvalidHeader("width exceeds JVM limits"))
        _ <- Either.cond(height <= Int.MaxValue, (), InvalidHeader("height exceeds JVM limits"))
        _ <- Either.cond(compression == 0, (), InvalidHeader(s"compression method $compression"))
        _ <- Either.cond(filter == 0, (), InvalidHeader(s"filter method $filter"))
        _ <- Either.cond(interlace == 0 || interlace == 1, (), InvalidHeader(s"interlace method $interlace"))
        result <- apply(width.toInt, height.toInt, depth, color, interlace == 1)
      yield result
