package png

import java.time.LocalDateTime
import munit.FunSuite

final class PngSuite extends FunSuite:
  private def image(width: Int, height: Int): Image =
    Image(
      width,
      height,
      Vector.tabulate(width * height) { index =>
        Rgba(index * 37 % 256, index * 73 % 256, index * 109 % 256, index * 19 % 256).toOption.get
      }
    ).toOption.get

  test("signature is the normative eight-byte sequence"):
    assertEquals(Png.Signature, Vector(137, 80, 78, 71, 13, 10, 26, 10).map(_.toByte))

  test("RGBA images round-trip across varied dimensions"):
    for (width, height) <- List(1 -> 1, 2 -> 7, 19 -> 3, 64 -> 64) do
      val original = image(width, height)
      assertEquals(Png.encode(original).flatMap(Png.decode), Right(original))

  test("Adam7 images round-trip across dimensions with empty passes"):
    val options = EncoderOptions(interlaced = true).toOption.get
    for (width, height) <- List(1 -> 1, 2 -> 7, 8 -> 8, 17 -> 9) do
      val original = image(width, height)
      assertEquals(Png.encode(original, options).flatMap(Png.decode), Right(original))

  test("one zlib stream can be split into many consecutive IDAT chunks"):
    val options = EncoderOptions(maximumIdatPayload = 1).toOption.get
    val original = image(8, 8)
    val bytes = Png.encode(original, options).toOption.get
    assertEquals(Png.decode(bytes), Right(original))
    assert(bytes.sliding(4).count(_.sameElements("IDAT".getBytes)) > 1)

  test("document API preserves supported metadata"):
    val metadata =
      PngMetadata(
        gamma = Some(0.45455),
        renderingIntent = Some(RenderingIntent.Perceptual),
        pixelDensity = Some(PixelDensity(3780, 3780, unitIsMetre = true).toOption.get),
        text = Vector(TextEntry("Title", "Round trip").toOption.get)
      ).toOption.get
    val document = PngDocument(image(3, 2), metadata)
    assertEquals(Png.encode(document).flatMap(Png.decodeDocument), Right(document))

  test("document API preserves international text, Exif, and modification time"):
    val extended = ExtendedMetadata(
      internationalText = Vector(
        InternationalText("Title", "ja", "題名", "PNG の実装").toOption.get
      ),
      modificationTime = Some(LocalDateTime.of(2026, 7, 12, 12, 30, 0)),
      exif = Some(ExifProfile(Array[Byte](73, 73, 42, 0)).toOption.get)
    )
    val document = PngDocument(image(2, 2), extendedMetadata = extended)
    assertEquals(Png.encode(document).flatMap(Png.decodeDocument), Right(document))

  test("document API preserves color management metadata"):
    val header = Header(1, 1, 8, ColorType.TruecolorAlpha).toOption.get
    val point = Chromaticity(0.3127, 0.3290).toOption.get
    val color = ColorMetadata(
      Some(Chromaticities(point, point, point, point)),
      Some(IccProfile("Profile", Array[Byte](1, 2, 3)).toOption.get),
      Some(SignificantBits(Vector(8, 8, 8, 8), header).toOption.get),
      Some(CodingIndependentCodePoints(1, 13, 0, true)),
      Some(MasteringDisplayVolume((1, 2), (3, 4), (5, 6), (7, 8), 1000, 1)),
      Some(ContentLightLevel(1000, 400))
    )
    val document = PngDocument(image(1, 1), colorMetadata = color)
    assertEquals(Png.encode(document).flatMap(Png.decodeDocument), Right(document))

  test("encoded output is readable by the JDK ImageIO implementation"):
    val original = image(7, 5)
    val bytes = Png.encode(original).toOption.get
    val decoded = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
    assertEquals(decoded.getWidth, 7)
    assertEquals(decoded.getHeight, 5)
    assertEquals(
      decoded.getRGB(3, 2),
      (original(3, 2).alpha << 24) |
        (original(3, 2).red << 16) |
        (original(3, 2).green << 8) | original(3, 2).blue
    )

  test("decoder rejects signatures that are close but not exact"):
    val bytes = Png.encode(image(1, 1)).toOption.get
    bytes(0) = 0
    assert(Png.decode(bytes).left.exists(_.isInstanceOf[PngError.InvalidSignature]))

  test("decoder rejects trailing data"):
    val bytes = Png.encode(image(1, 1)).toOption.get ++ Array[Byte](1)
    assertEquals(Png.decode(bytes).left.toOption, Some(PngError.TrailingData(1)))

  test("callers can enforce file and image resource limits"):
    val original = image(8, 4)
    val bytes = Png.encode(original).toOption.get
    val fileLimit = DecoderOptions(maximumFileBytes = bytes.length - 1).toOption.get
    assert(Png.decode(bytes, fileLimit).left.exists(_.isInstanceOf[PngError.ResourceLimit]))
    val pixelLimit = DecoderOptions(maximumPixels = 31).toOption.get
    assertEquals(Png.decode(bytes, pixelLimit).left.toOption, Some(PngError.ResourceLimit("pixels", 32, 31)))

  test("decoder rejects decompression bombs at the expected scanline boundary"):
    val bytes = Png.encode(image(1, 1)).toOption.get
    val idatStart = 8 + 25
    bytes(idatStart + 8) = 0
    assert(Png.decode(bytes).isLeft)
