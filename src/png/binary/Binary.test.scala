package png

import munit.FunSuite
import png.Binary.*
import png.PngError.UnexpectedEnd

final class BinarySuite extends FunSuite:
  test("uint32 is emitted in network byte order"):
    assertEquals(0x89abcdefL.uint32Bytes.toVector, Vector(0x89, 0xab, 0xcd, 0xef).map(_.toByte))

  test("cursor reads unsigned 32-bit values"):
    val cursor = Binary.Cursor(Array(0xff, 0xff, 0xff, 0xff).map(_.toByte))
    assertEquals(cursor.uint32, Right(0xffffffffL))
    assertEquals(cursor.offset, 4)

  test("cursor reports the exact truncated region without advancing"):
    val cursor = Binary.Cursor(Array[Byte](1, 2))
    assertEquals(cursor.take(3), Left(UnexpectedEnd(0, 3, 2)))
    assertEquals(cursor.offset, 0)

  test("cursor owns its input"):
    val bytes = Array[Byte](1)
    val cursor = Binary.Cursor(bytes)
    bytes(0) = 9
    assertEquals(cursor.uint8, Right(1))
