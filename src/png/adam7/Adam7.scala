package png

/** Geometry for PNG's seven-pass Adam7 interlace.
  *
  * Coordinates come from [[https://www.w3.org/TR/png-3/#8Interlace PNG §8.2]].
  * Each pass is a miniature image whose filter history starts from an empty
  * previous row.
  */
private[png] object Adam7:
  final case class Pass(
      number: Int,
      xStart: Int,
      yStart: Int,
      xStep: Int,
      yStep: Int
  ):
    def width(imageWidth: Int): Int = extent(imageWidth, xStart, xStep)
    def height(imageHeight: Int): Int = extent(imageHeight, yStart, yStep)
    def isEmpty(imageWidth: Int, imageHeight: Int): Boolean =
      width(imageWidth) == 0 || height(imageHeight) == 0

    def coordinates(imageWidth: Int, imageHeight: Int): Iterator[(Int, Int)] =
      for
        y <- Iterator.iterate(yStart)(_ + yStep).takeWhile(_ < imageHeight)
        x <- Iterator.iterate(xStart)(_ + xStep).takeWhile(_ < imageWidth)
      yield x -> y

  val passes: Vector[Pass] = Vector(
    Pass(1, 0, 0, 8, 8),
    Pass(2, 4, 0, 8, 8),
    Pass(3, 0, 4, 4, 8),
    Pass(4, 2, 0, 4, 4),
    Pass(5, 0, 2, 2, 4),
    Pass(6, 1, 0, 2, 2),
    Pass(7, 0, 1, 1, 2)
  )

  def decompressedSize(header: Header): Long =
    passes.iterator
      .filterNot(_.isEmpty(header.width, header.height))
      .map: pass =>
        val rowBytes =
          scanlineBytes(pass.width(header.width), header.bitsPerPixel)
        (rowBytes.toLong + 1) * pass.height(header.height)
      .sum

  def scanlineBytes(width: Int, bitsPerPixel: Int): Int =
    ((width.toLong * bitsPerPixel + 7) / 8).toInt

  private def extent(size: Int, start: Int, step: Int): Int =
    if size <= start then 0 else (size - start + step - 1) / step
