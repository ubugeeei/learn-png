package png

import munit.FunSuite

final class ColorMetadataSuite extends FunSuite:
  private val header = Header(1, 1, 8, ColorType.TruecolorAlpha).toOption.get

  test("all Third Edition color metadata chunks round-trip"):
    val point = Chromaticity(0.3127, 0.3290).toOption.get
    val metadata = ColorMetadata(
      chromaticities = Some(Chromaticities(point, point, point, point)),
      iccProfile = Some(IccProfile("Display profile", Array[Byte](1, 2, 3, 4)).toOption.get),
      significantBits = Some(SignificantBits(Vector(8, 8, 8, 8), header).toOption.get),
      coding = Some(CodingIndependentCodePoints(1, 13, 0, fullRange = true)),
      masteringDisplay =
        Some(MasteringDisplayVolume((100, 200), (300, 400), (500, 600), (700, 800), 1000, 1)),
      contentLight = Some(ContentLightLevel(1000, 400))
    )
    val chunks = ColorMetadata.chunks(metadata).toOption.get
    assertEquals(ColorMetadata.decode(chunks, header), Right(metadata))

  test("sBIT length and values depend on color type and depth"):
    val grayscale = Header(1, 1, 4, ColorType.Grayscale).toOption.get
    assert(SignificantBits(Vector(4), grayscale).isRight)
    assert(SignificantBits(Vector(5), grayscale).isLeft)
    assert(SignificantBits(Vector(4, 4), grayscale).isLeft)

  test("malformed fixed-size HDR chunks are rejected declaratively"):
    val cases = Vector(
      ChunkType.cHRM -> 31,
      ChunkType.cICP -> 3,
      ChunkType.mDCV -> 23,
      ChunkType.cLLI -> 7
    )
    cases.foreach { case (kind, length) =>
      val chunk = Chunk(kind, Array.fill[Byte](length)(0)).toOption.get
      assert(ColorMetadata.decode(Vector(chunk), header).isLeft, clues(kind.name, length))
    }
