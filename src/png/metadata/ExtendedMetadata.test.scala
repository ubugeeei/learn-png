package png

import java.time.LocalDateTime
import munit.FunSuite

final class ExtendedMetadataSuite extends FunSuite:
  test("international text, timestamp, and Exif round-trip through chunks"):
    val text = InternationalText("Title", "ja", "題名", "透明な画像").toOption.get
    val time = LocalDateTime.of(2026, 7, 12, 23, 59, 58)
    val exif = ExifProfile(Array[Byte](73, 73, 42, 0)).toOption.get
    val metadata = ExtendedMetadata(Vector(text), Some(time), Some(exif))
    val chunks = ExtendedMetadata.chunks(metadata).toOption.get
    assertEquals(ExtendedMetadata.decode(chunks), Right(metadata))

  test("invalid calendar dates and duplicate singleton chunks are rejected"):
    val invalidTime = Chunk(ChunkType.tIME, Array[Byte](7, -22, 2, 30, 0, 0, 0)).toOption.get
    assert(ExtendedMetadata.decode(Vector(invalidTime)).isLeft)
    val exif = Chunk(ChunkType.eXIf, Array[Byte](1)).toOption.get
    assert(ExtendedMetadata.decode(Vector(exif, exif)).isLeft)
