package png

import munit.FunSuite

final class HeaderSuite extends FunSuite:
  private val legalFormats = ColorType.values.toVector
    .flatMap: colorType =>
      colorType.bitDepths.toVector.sorted.map(bitDepth => colorType -> bitDepth)

  test("valid color type and bit depth combinations round-trip") {
    legalFormats.foreach { case (colorType, bitDepth) =>
      val header = Header(31, 17, bitDepth, colorType, interlaced = true).toOption.get
      assertEquals(
        obtained = Header.parse(header.bytes),
        expected = Right(header),
        clue = s"color type ${colorType.code}, depth $bitDepth"
      )
    }
  }

  test("invalid color type and bit depth combinations are rejected"):
    assert(Header(1, 1, 4, ColorType.Truecolor).isLeft)
    assert(Header(1, 1, 16, ColorType.Indexed).isLeft)

  test("IHDR methods must be known"):
    val valid = Header(1, 1, 8, ColorType.TruecolorAlpha).toOption.get.bytes
    for index <- 10 to 12 do
      val corrupt = valid.clone()
      corrupt(index) = 2
      assert(Header.parse(corrupt).isLeft)
