package png

import munit.FunSuite

final class PngMetadataSuite extends FunSuite:
  test("metadata chunks encode and parse losslessly"):
    val density = PixelDensity(3780, 3780, unitIsMetre = true).toOption.get
    val author = TextEntry("Author", "Ada Lovelace").toOption.get
    val metadata =
      PngMetadata(Some(0.45455), Some(RenderingIntent.Perceptual), Some(density), Vector(author)).toOption.get
    val chunks = PngMetadata.chunks(metadata).toOption.get
    assertEquals(PngMetadata.decode(chunks), Right(metadata))

  test("text keywords enforce PNG spacing and Latin-1 rules"):
    assert(TextEntry("", "value").isLeft)
    assert(TextEntry(" bad", "value").isLeft)
    assert(TextEntry("two  spaces", "value").isLeft)
    assert(TextEntry("Emoji", "🙂").isLeft)

  test("invalid singleton lengths and duplicates are rejected"):
    val bad = Chunk(ChunkType.gAMA, Array[Byte](1)).toOption.get
    assert(PngMetadata.decode(Vector(bad)).isLeft)
    val valid = Chunk(ChunkType.sRGB, Array[Byte](0)).toOption.get
    assert(PngMetadata.decode(Vector(valid, valid)).isLeft)
