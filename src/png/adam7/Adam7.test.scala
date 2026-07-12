package png

import munit.FunSuite

final class Adam7Suite extends FunSuite:
  test("passes cover every coordinate exactly once"):
    for width <- 1 to 17; height <- 1 to 17 do
      val coordinates = Adam7.passes.flatMap(_.coordinates(width, height))
      assertEquals(coordinates.distinct.size, width * height)
      assertEquals(
        coordinates.toSet,
        (for y <- 0 until height; x <- 0 until width yield x -> y).toSet
      )

  test("small images correctly skip empty passes"):
    assertEquals(
      Adam7.passes.map(pass => pass.width(1) -> pass.height(1)),
      Vector(1 -> 1, 0 -> 1, 1 -> 0, 0 -> 1, 1 -> 0, 0 -> 1, 1 -> 0)
    )

  test("decompressed size includes one filter byte per non-empty pass row"):
    val header =
      Header(8, 8, 8, ColorType.TruecolorAlpha, interlaced = true).toOption.get
    val manual = Adam7.passes
      .filterNot(_.isEmpty(8, 8))
      .map: pass =>
        (Adam7.scanlineBytes(pass.width(8), 32) + 1) * pass.height(8)
    assertEquals(Adam7.decompressedSize(header), manual.sum.toLong)
