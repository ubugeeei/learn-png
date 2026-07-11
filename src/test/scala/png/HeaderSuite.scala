package png

import munit.FunSuite

final class HeaderSuite extends FunSuite:
  test("valid color type and bit depth combinations round-trip"):
    for color <- ColorType.values; depth <- color.bitDepths do
      val header = Header(31, 17, depth, color, interlaced = true).toOption.get
      assertEquals(Header.parse(header.bytes), Right(header))

  test("invalid color type and bit depth combinations are rejected"):
    assert(Header(1, 1, 4, ColorType.Truecolor).isLeft)
    assert(Header(1, 1, 16, ColorType.Indexed).isLeft)

  test("IHDR methods must be known"):
    val valid = Header(1, 1, 8, ColorType.TruecolorAlpha).toOption.get.bytes
    for index <- 10 to 12 do
      val corrupt = valid.clone()
      corrupt(index) = 2
      assert(Header.parse(corrupt).isLeft)
