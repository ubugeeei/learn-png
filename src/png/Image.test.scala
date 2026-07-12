package png

import munit.FunSuite

final class ImageSuite extends FunSuite:
  test("RGBA validates every channel"):
    assert(Rgba(-1, 0, 0).isLeft)
    assert(Rgba(0, 256, 0).isLeft)
    assertEquals(Rgba(1, 2, 3).toOption.get.alpha, 255)

  test("image dimensions and pixel count agree"):
    val pixel = Rgba(1, 2, 3).toOption.get
    assert(Image(0, 1, Vector.empty).isLeft)
    assert(Image(2, 2, Vector.fill(3)(pixel)).isLeft)
    assertEquals(Image(2, 2, Vector.fill(4)(pixel)).toOption.get(1, 1), pixel)

  test("rows preserve row-major order"):
    val pixels = (0 until 6).map(value => Rgba(value, 0, 0).toOption.get)
    val image = Image(3, 2, pixels).toOption.get
    assertEquals(image.rows.map(_.map(_.red)), Vector(Vector(0, 1, 2), Vector(3, 4, 5)))
