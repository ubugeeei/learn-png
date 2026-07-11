package png

import munit.FunSuite

final class PngSuite extends FunSuite:
  private def image(width: Int, height: Int): Image =
    Image(
      width,
      height,
      Vector.tabulate(width * height) { index =>
        Rgba(
          index * 37 % 256,
          index * 73 % 256,
          index * 109 % 256,
          index * 19 % 256
        ).toOption.get
      }
    ).toOption.get

  test("signature is the normative eight-byte sequence"):
    assertEquals(
      Png.Signature,
      Vector(137, 80, 78, 71, 13, 10, 26, 10).map(_.toByte)
    )

  test("RGBA images round-trip across varied dimensions"):
    for (width, height) <- List(1 -> 1, 2 -> 7, 19 -> 3, 64 -> 64) do
      val original = image(width, height)
      assertEquals(Png.encode(original).flatMap(Png.decode), Right(original))

  test("encoded output is readable by the JDK ImageIO implementation"):
    val original = image(7, 5)
    val bytes = Png.encode(original).toOption.get
    val decoded =
      javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
    assertEquals(decoded.getWidth, 7)
    assertEquals(decoded.getHeight, 5)
    assertEquals(
      decoded.getRGB(3, 2),
      (original(3, 2).alpha << 24) | (original(3, 2).red << 16) | (original(
        3,
        2
      ).green << 8) | original(3, 2).blue
    )

  test("decoder rejects signatures that are close but not exact"):
    val bytes = Png.encode(image(1, 1)).toOption.get
    bytes(0) = 0
    assert(
      Png.decode(bytes).left.exists(_.isInstanceOf[PngError.InvalidSignature])
    )

  test("decoder rejects trailing data"):
    val bytes = Png.encode(image(1, 1)).toOption.get ++ Array[Byte](1)
    assertEquals(
      Png.decode(bytes).left.toOption,
      Some(PngError.TrailingData(1))
    )

  test("decoder rejects decompression bombs at the expected scanline boundary"):
    val bytes = Png.encode(image(1, 1)).toOption.get
    val idatStart = 8 + 25
    bytes(idatStart + 8) = 0
    assert(Png.decode(bytes).isLeft)
