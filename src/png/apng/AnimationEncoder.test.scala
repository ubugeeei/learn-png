package png

import munit.FunSuite

final class AnimationEncoderSuite extends FunSuite:
  private val red = Rgba16(0xffff, 0, 0).toOption.get
  private val blue = Rgba16(0, 0, 0xffff).toOption.get
  private val clear = Rgba16(0, 0, 0, 0).toOption.get

  test("encoded frame rectangles round-trip through APNG composition"):
    val source = animation(maximumSecondFrameWidth = 1)
    val decoded = Png.encodeAnimation(source).flatMap(Png.decodeAnimation).toOption.get

    assertEquals(decoded.canvasWidth, 2)
    assertEquals(decoded.canvasHeight, 1)
    assertEquals(decoded.plays, 3L)
    assert(decoded.fallbackIsFirstFrame)
    assertEquals(decoded.frames.map(_.image.pixels), Vector(Vector(red, clear), Vector(red, blue)))

  test("one-byte compressed payloads retain continuous fcTL/fdAT sequence numbers"):
    val options = EncoderOptions(maximumIdatPayload = 1).toOption.get
    val result =
      Png.encodeAnimation(animation(maximumSecondFrameWidth = 1), options).flatMap(Png.decodeAnimation)

    assert(result.isRight)

  test("the first source frame must be the complete static fallback canvas"):
    val partial = frame(Image16(1, 1, Vector(red)).toOption.get)

    assert(AnimationSource(2, 1, 0, Vector(partial)).isLeft)

  test("source rectangles may not extend beyond the canvas"):
    val full = frame(Image16(2, 1, Vector(red, clear)).toOption.get)
    val outside = frame(Image16(1, 1, Vector(blue)).toOption.get, xOffset = 2)

    assert(AnimationSource(2, 1, 0, Vector(full, outside)).isLeft)

  test("the first frame may not request PREVIOUS disposal"):
    val image = Image16(1, 1, Vector(red)).toOption.get
    val previous = frame(image, dispose = DisposeOperation.Previous)

    assert(AnimationSource(1, 1, 0, Vector(previous)).isLeft)

  private def animation(maximumSecondFrameWidth: Int): AnimationSource =
    val first = frame(Image16(2, 1, Vector(red, clear)).toOption.get)
    val second = frame(
      Image16(maximumSecondFrameWidth, 1, Vector.fill(maximumSecondFrameWidth)(blue)).toOption.get,
      xOffset = 1
    )
    AnimationSource(2, 1, 3, Vector(first, second)).toOption.get

  private def frame(
      image: Image16,
      xOffset: Int = 0,
      dispose: DisposeOperation = DisposeOperation.None
  ): AnimationSourceFrame =
    AnimationSourceFrame(image, xOffset = xOffset, dispose = dispose).toOption.get
