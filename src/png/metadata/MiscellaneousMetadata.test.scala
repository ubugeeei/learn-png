package png

import munit.FunSuite

final class MiscellaneousMetadataSuite extends FunSuite:
  test("RGBA background, suggested palettes, and compressed text round-trip"):
    val header = Header(1, 1, 8, ColorType.TruecolorAlpha).toOption.get
    val palette = SuggestedPalette(
      "Preview",
      16,
      Vector(SuggestedPaletteEntry(0x1234, 0xabcd, 0xffff, 0x8000, 42))
    ).toOption.get
    val metadata = MiscellaneousMetadata(
      Some(BackgroundColor.Truecolor(12, 34, 56)),
      None,
      Vector(palette),
      Vector(CompressedText("Comment", "repeated repeated repeated").toOption.get)
    )
    val chunks = MiscellaneousMetadata.chunks(metadata, header, 0).toOption.get
    assertEquals(MiscellaneousMetadata.decode(chunks, header, 0), Right(metadata))

  test("indexed background and histogram validate against PLTE size"):
    val header = Header(1, 1, 2, ColorType.Indexed).toOption.get
    val metadata = MiscellaneousMetadata(
      Some(BackgroundColor.PaletteIndex(2)),
      Some(Vector(10, 20, 30)),
      Vector.empty,
      Vector.empty
    )
    val chunks = MiscellaneousMetadata.chunks(metadata, header, paletteEntries = 3).toOption.get
    assertEquals(MiscellaneousMetadata.decode(chunks, header, 3), Right(metadata))
    assert(MiscellaneousMetadata.decode(chunks, header, 2).isLeft)

  test("duplicate suggested-palette names are rejected"):
    val header = Header(1, 1, 8, ColorType.Truecolor).toOption.get
    val palette = SuggestedPalette(
      "Same",
      8,
      Vector(SuggestedPaletteEntry(1, 2, 3, 4, 5))
    ).toOption.get
    val chunk = MiscellaneousMetadata
      .chunks(MiscellaneousMetadata(None, None, Vector(palette), Vector.empty), header, 0)
      .toOption
      .get
      .head
    assert(MiscellaneousMetadata.decode(Vector(chunk, chunk), header, 0).isLeft)
