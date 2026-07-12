package png

import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class Codec16Suite extends FunSuite with ScalaCheckSuite:
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

  property("generated RGBA16 rasters round-trip with and without Adam7"):
    val dimensions = for
      width <- Gen.choose(1, 24)
      height <- Gen.choose(1, 24)
    yield width -> height
    val channels = Gen.listOfN(24 * 24 * 4, Gen.choose(0, 0xffff))

    forAll(dimensions, Gen.oneOf(true, false), channels): (size, interlaced, values) =>
      val (width, height) = size
      val pixels = values
        .take(width * height * 4)
        .grouped(4)
        .map(group => Rgba16(group(0), group(1), group(2), group(3)).toOption.get)
      val original = Image16(width, height, pixels).toOption.get
      val options = EncoderOptions(interlaced = interlaced, maximumIdatPayload = 17).toOption.get

      Codec16.encode(original, options).flatMap(Codec16.decode(_, DecoderOptions.default)) == Right(original)
