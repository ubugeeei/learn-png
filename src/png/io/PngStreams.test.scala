package png

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import munit.FunSuite

final class PngStreamsSuite extends FunSuite:
  private val image = Image(1, 1, Vector(Rgba(1, 2, 3, 4).toOption.get)).toOption.get

  test("stream API round-trips without closing caller streams"):
    val output = ByteArrayOutputStream()
    assertEquals(Png.write(output, image), Right(()))
    output.write(0)
    val pngBytes = output.toByteArray.dropRight(1)
    val input = ByteArrayInputStream(pngBytes)
    assertEquals(Png.read(input), Right(image))
    assertEquals(input.read(), -1)

  test("stream reads stop as soon as the configured limit is crossed"):
    val input = ByteArrayInputStream(Array.fill[Byte](100)(0))
    val options = DecoderOptions(maximumFileBytes = 10).toOption.get
    assert(Png.read(input, options).left.exists(_.isInstanceOf[PngError.ResourceLimit]))
