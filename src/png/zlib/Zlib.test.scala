package png

import munit.FunSuite

final class ZlibSuite extends FunSuite:
  private val payloads = Vector(
    "empty" -> Array.emptyByteArray,
    "small text" -> "portable network graphics".getBytes,
    "all byte values" -> Array.tabulate[Byte](256)(_.toByte),
    "compressible block" -> Array.fill[Byte](64 * 1024)(42)
  )

  test("complete zlib streams round-trip"):
    payloads.foreach { case (name, payload) =>
      val compressed = Zlib.compress(payload).toOption.get
      assertEquals(
        obtained = Zlib.decompress(compressed, payload.length).map(_.toVector),
        expected = Right(payload.toVector),
        clue = name
      )
    }

  test("truncated and concatenated zlib streams are rejected"):
    val compressed = Zlib.compress("payload".getBytes).toOption.get
    assert(Zlib.decompress(compressed.dropRight(1), 1024).isLeft)
    assert(Zlib.decompress(compressed ++ compressed, 1024).isLeft)

  test("inflation stops at the configured output boundary"):
    val compressed = Zlib.compress(Array.fill[Byte](10_000)(0)).toOption.get
    assert(Zlib.decompress(compressed, maximumBytes = 9_999).isLeft)
