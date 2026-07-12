package png

import munit.FunSuite

final class Codec16Suite extends FunSuite:
  private def image(width: Int, height: Int): Image16 =
    Image16(
      width,
      height,
      Vector.tabulate(width * height): index =>
        Rgba16(
          index * 1009 & 0xffff,
          index * 4001 & 0xffff,
          index * 7919 & 0xffff,
          0xffff - index
        ).toOption.get
    ).toOption.get

  test("RGBA16 round-trips every low byte without interlacing"):
    val original = image(11, 7)
    assertEquals(
      Codec16.encode(original, EncoderOptions.default).flatMap(Codec16.decode(_, DecoderOptions.default)),
      Right(original)
    )

  test("RGBA16 round-trips through Adam7 including empty small passes"):
    val options = EncoderOptions(interlaced = true).toOption.get
    List(1 -> 1, 3 -> 2, 9 -> 17).foreach: (width, height) =>
      val original = image(width, height)
      assertEquals(
        Codec16.encode(original, options).flatMap(Codec16.decode(_, DecoderOptions.default)),
        Right(original)
      )
