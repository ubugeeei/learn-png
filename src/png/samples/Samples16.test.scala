package png

import munit.FunSuite

final class Samples16Suite extends FunSuite:
  test("RGBA16 row serialization preserves low bytes"):
    val pixel = Rgba16(0x1234, 0xabcd, 0x0102, 0xfedc).toOption.get
    assertEquals(
      Samples16.rgba16Row(Vector(pixel)).toVector,
      Vector(0x12, 0x34, 0xab, 0xcd, 0x01, 0x02, 0xfe, 0xdc).map(_.toByte)
    )

  test("16-bit truecolor alpha decoding preserves every sample bit"):
    val header = Header(1, 1, 16, ColorType.TruecolorAlpha).toOption.get
    val bytes = Vector(0x12, 0x34, 0xab, 0xcd, 0x01, 0x02, 0xfe, 0xdc).map(_.toByte).toArray
    assertEquals(
      Samples16.decodeRow(bytes, 1, header, Vector.empty, Array.emptyByteArray),
      Right(Vector(Rgba16(0x1234, 0xabcd, 0x0102, 0xfedc).toOption.get))
    )
