package png

import munit.FunSuite
import png.PngError.*

final class ChunkSuite extends FunSuite:
  test(
    "chunk type bits expose ancillary, private, and safe-to-copy properties"
  ):
    val kind = ChunkType.fromString("vpAg").toOption.get
    assert(kind.isAncillary)
    assert(kind.isPrivate)
    assert(kind.isSafeToCopy)

  test("reserved chunk type bit must be zero"):
    assert(ChunkType.fromString("IHdR").isLeft)

  test("type must contain four ASCII letters"):
    assert(ChunkType.fromString("IDAT!").isLeft)
    assert(ChunkType.fromString("1DAT").isLeft)

  test("IEND matches the specification's complete byte sequence"):
    val chunk = Chunk(ChunkType.IEND, Array.emptyByteArray).toOption.get
    val expected =
      Vector(0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130).map(_.toByte)
    assertEquals(chunk.bytes.toVector, expected)

  test("chunk payload is immutable at both boundaries"):
    val source = Array[Byte](1, 2)
    val chunk = Chunk(ChunkType.IDAT, source).toOption.get
    source(0) = 9
    val exposed = chunk.data
    exposed(1) = 9
    assertEquals(chunk.data.toVector, Vector[Byte](1, 2))

  test("parser rejects a corrupted CRC"):
    val bytes = Chunk(ChunkType.IEND, Array.emptyByteArray).toOption.get.bytes
    bytes(bytes.length - 1) = 0
    assert(
      Chunk.parse(Binary.Cursor(bytes)).left.exists(_.isInstanceOf[CrcMismatch])
    )

  test("chunk serialization and parsing round-trip"):
    val original =
      Chunk(ChunkType.IDAT, Array.tabulate[Byte](256)(_.toByte)).toOption.get
    assertEquals(Chunk.parse(Binary.Cursor(original.bytes)), Right(original))
