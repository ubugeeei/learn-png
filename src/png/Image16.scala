package png

import png.PngError.InvalidImage

/** A lossless 16-bit red/green/blue/alpha pixel.
  *
  * Every channel is represented as an unsigned value in `0..65535`. Eight-bit PNG samples expand by
  * multiplication with 257; sixteen-bit PNG samples retain every source bit.
  */
final case class Rgba16 private (red: Int, green: Int, blue: Int, alpha: Int)

object Rgba16:
  val Opaque: Int = 0xffff

  def apply(red: Int, green: Int, blue: Int, alpha: Int = Opaque): Either[PngError, Rgba16] =
    val channels = Vector("red" -> red, "green" -> green, "blue" -> blue, "alpha" -> alpha)
    channels.collectFirst { case (name, value) if value < 0 || value > 0xffff => name -> value } match
      case Some((name, value)) => Left(InvalidImage(s"$name channel $value is outside 0..65535"))
      case None => Right(new Rgba16(red, green, blue, alpha))

  private[png] def unsafe(red: Int, green: Int, blue: Int, alpha: Int = Opaque): Rgba16 =
    new Rgba16(red, green, blue, alpha)

  extension (pixel: Rgba16)
    /** Reduce to RGBA8 using PNG's recommended high-byte reduction for 16-to-8 conversion. */
    def toRgba8: Rgba = Rgba.unsafe(pixel.red >>> 8, pixel.green >>> 8, pixel.blue >>> 8, pixel.alpha >>> 8)

/** Immutable row-major 16-bit raster for lossless PNG sample delivery. */
final class Image16 private (val width: Int, val height: Int, private val raster: Vector[Rgba16]):
  def pixels: Vector[Rgba16] = raster
  def apply(x: Int, y: Int): Rgba16 =
    if x < 0 || x >= width || y < 0 || y >= height then
      throw IndexOutOfBoundsException(s"pixel ($x,$y) outside ${width}x$height")
    raster(y * width + x)
  def rows: Vector[Vector[Rgba16]] = raster.grouped(width).map(_.toVector).toVector
  def toImage8: Image = Image(width, height, raster.map(_.toRgba8)).toOption.get
  override def equals(other: Any): Boolean = other match
    case that: Image16 => width == that.width && height == that.height && raster == that.raster
    case _ => false
  override def hashCode(): Int = (width, height, raster).hashCode
  override def toString: String = s"Image16(${width}x$height)"

object Image16:
  def apply(width: Int, height: Int, pixels: IterableOnce[Rgba16]): Either[PngError, Image16] =
    val values = pixels.iterator.toVector
    val expected = width.toLong * height
    if width <= 0 || height <= 0 then Left(InvalidImage("width and height must be positive"))
    else if expected > Int.MaxValue then Left(InvalidImage("pixel count exceeds JVM limits"))
    else if values.length != expected then
      Left(InvalidImage(s"expected $expected pixels, received ${values.length}"))
    else Right(new Image16(width, height, values))
