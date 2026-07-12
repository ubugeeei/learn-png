package png

import png.PngError.InvalidImage

/** An eight-bit red/green/blue/alpha pixel.
  *
  * Channels are represented by `Int` for ergonomic arithmetic, but construction enforces the closed interval
  * 0–255. PNG's sample representation is defined in
  * [[https://www.w3.org/TR/png-3/#4Concepts.FormatPixels PNG §4.1.1]].
  */
final case class Rgba private (red: Int, green: Int, blue: Int, alpha: Int)

object Rgba:
  def apply(red: Int, green: Int, blue: Int, alpha: Int = 255): Either[PngError, Rgba] =
    val channels = List("red" -> red, "green" -> green, "blue" -> blue, "alpha" -> alpha)
    channels.collectFirst {
      case (name, value) if value < 0 || value > 255 =>
        name -> value
    } match
      case Some((name, value)) =>
        Left(InvalidImage(s"$name channel $value is outside 0..255"))
      case None =>
        Right(new Rgba(red, green, blue, alpha))

  private[png] def unsafe(red: Int, green: Int, blue: Int, alpha: Int = 255): Rgba =
    new Rgba(red, green, blue, alpha)

/** A non-empty, immutable rectangular raster in row-major order.
  *
  * `Image` deliberately exposes a single normalized RGBA representation. File-level color types are an
  * encoding concern, while callers always receive predictable pixels.
  */
final class Image private (val width: Int, val height: Int, private val raster: Vector[Rgba]):
  def pixels: Vector[Rgba] = raster

  def apply(x: Int, y: Int): Rgba =
    if x < 0 || x >= width || y < 0 || y >= height then
      throw IndexOutOfBoundsException(s"pixel ($x,$y) outside ${width}x$height")
    raster(y * width + x)

  def rows: Vector[Vector[Rgba]] = raster.grouped(width).map(_.toVector).toVector

  override def equals(other: Any): Boolean =
    other match
      case that: Image =>
        width == that.width && height == that.height && raster == that.raster
      case _ =>
        false

  override def hashCode(): Int = (width, height, raster).hashCode
  override def toString: String = s"Image(${width}x$height)"

object Image:
  def apply(width: Int, height: Int, pixels: IterableOnce[Rgba]): Either[PngError, Image] =
    val values = pixels.iterator.toVector
    val expected = width.toLong * height.toLong
    if width <= 0 || height <= 0 then Left(InvalidImage("width and height must be positive"))
    else if expected > Int.MaxValue then Left(InvalidImage("pixel count exceeds JVM limits"))
    else if values.length != expected then
      Left(InvalidImage(s"expected $expected pixels, received ${values.length}"))
    else Right(new Image(width, height, values))
