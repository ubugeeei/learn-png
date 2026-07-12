package png

import munit.FunSuite

final class Image16Suite extends FunSuite:
  test("16-bit channel bounds and raster size are validated"):
    assert(Rgba16(-1, 0, 0).isLeft)
    assert(Rgba16(0x10000, 0, 0).isLeft)
    val pixel = Rgba16(0x1234, 0xabcd, 0xffff, 0x0102).toOption.get
    assert(Image16(2, 1, Vector(pixel)).isLeft)
    assert(Image16(1, 1, Vector(pixel)).isRight)

  test("8-bit reduction uses the high byte without rounding surprises"):
    val pixel = Rgba16(0x1234, 0xabcd, 0xffff, 0x0102).toOption.get
    assertEquals(pixel.toRgba8, Rgba(0x12, 0xab, 0xff, 0x01).toOption.get)
