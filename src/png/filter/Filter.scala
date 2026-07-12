package png

import png.PngError.InvalidFilter

/** The five adaptive scanline filters from
  * [[https://www.w3.org/TR/png-3/#9Filters PNG §9]].
  *
  * Arithmetic is modulo 256. `bytesPerPixel` is the specification's `bpp`: at
  * least one byte, rounded up for sub-byte samples.
  */
private[png] enum Filter(val code: Int):
  case None extends Filter(0)
  case Sub extends Filter(1)
  case Up extends Filter(2)
  case Average extends Filter(3)
  case Paeth extends Filter(4)

  def encode(
      row: Array[Byte],
      previous: Array[Byte],
      bytesPerPixel: Int
  ): Array[Byte] =
    Array.tabulate(row.length): index =>
      val left =
        if index >= bytesPerPixel then row(index - bytesPerPixel) & 0xff else 0
      val up = if previous.nonEmpty then previous(index) & 0xff else 0
      val upperLeft =
        if previous.nonEmpty && index >= bytesPerPixel then
          previous(index - bytesPerPixel) & 0xff
        else 0
      val predictor = this match
        case None    => 0
        case Sub     => left
        case Up      => up
        case Average => (left + up) / 2
        case Paeth   => Filter.paeth(left, up, upperLeft)
      ((row(index) & 0xff) - predictor).toByte

  def decode(
      filtered: Array[Byte],
      previous: Array[Byte],
      bytesPerPixel: Int
  ): Array[Byte] =
    val row = new Array[Byte](filtered.length)
    for index <- filtered.indices do
      val left =
        if index >= bytesPerPixel then row(index - bytesPerPixel) & 0xff else 0
      val up = if previous.nonEmpty then previous(index) & 0xff else 0
      val upperLeft =
        if previous.nonEmpty && index >= bytesPerPixel then
          previous(index - bytesPerPixel) & 0xff
        else 0
      val predictor = this match
        case None    => 0
        case Sub     => left
        case Up      => up
        case Average => (left + up) / 2
        case Paeth   => Filter.paeth(left, up, upperLeft)
      row(index) = ((filtered(index) & 0xff) + predictor).toByte
    row

object Filter:
  def fromCode(code: Int): Either[PngError, Filter] =
    Filter.values.find(_.code == code).toRight(InvalidFilter(code))

  def choose(
      row: Array[Byte],
      previous: Array[Byte],
      bytesPerPixel: Int
  ): (Filter, Array[Byte]) =
    Filter.values
      .map(filter => filter -> filter.encode(row, previous, bytesPerPixel))
      .minBy((_, bytes) => bytes.iterator.map(byte => math.abs(byte.toInt)).sum)

  private def paeth(left: Int, up: Int, upperLeft: Int): Int =
    val estimate = left + up - upperLeft
    val leftDistance = math.abs(estimate - left)
    val upDistance = math.abs(estimate - up)
    val diagonalDistance = math.abs(estimate - upperLeft)
    if leftDistance <= upDistance && leftDistance <= diagonalDistance then left
    else if upDistance <= diagonalDistance then up
    else upperLeft
