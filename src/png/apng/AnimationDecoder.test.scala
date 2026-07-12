package png

import munit.FunSuite
import png.Binary.*

final class AnimationDecoderSuite extends FunSuite:
  private val red = Rgba16(0xffff, 0, 0, 0xffff).toOption.get
  private val blue = Rgba16(0, 0, 0xffff, 0xffff).toOption.get
  private val clear = Rgba16(0, 0, 0, 0).toOption.get

  test("APNG sequence groups IDAT/fdAT and composites SOURCE frames"):
    val first = Image16(2, 1, Vector(red, clear)).toOption.get
    val second = Image16(1, 1, Vector(blue)).toOption.get
    val bytes = animationBytes(first, second)
    val animation = Png.decodeAnimation(bytes).toOption.get

    assertEquals(animation.frames.length, 2)
    assert(animation.fallbackIsFirstFrame)
    assertEquals(animation.frames(0).image.pixels, Vector(red, clear))
    assertEquals(animation.frames(1).image.pixels, Vector(red, blue))
    assertEquals(animation.plays, 0L)

  test("out-of-order APNG sequence numbers are rejected"):
    val first = Image16(2, 1, Vector(red, clear)).toOption.get
    val second = Image16(1, 1, Vector(blue)).toOption.get
    val bytes = animationBytes(first, second)
    val sequencePattern = 2L.uint32Bytes
    val index = bytes.sliding(4).indexWhere(_.sameElements(sequencePattern))
    bytes(index + 3) = 9
    assert(Png.decodeAnimation(bytes).isLeft)

  test("OVER blending and BACKGROUND/PREVIOUS disposal update the next canvas"):
    val translucentBlue = Rgba16(0, 0, 0xffff, 0x8000).toOption.get
    val green = Rgba16(0, 0xffff, 0, 0xffff).toOption.get
    val oneRed = Image16(1, 1, Vector(red)).toOption.get
    val oneBlue = Image16(1, 1, Vector(translucentBlue)).toOption.get
    val oneGreen = Image16(1, 1, Vector(green)).toOption.get
    val sourceNone = control(0, DisposeOperation.None, BlendOperation.Source)
    val overBackground = control(1, DisposeOperation.Background, BlendOperation.Over)
    val sourceNone2 = control(2, DisposeOperation.None, BlendOperation.Source)
    val frames = AnimationDecoder
      .compose(Vector(sourceNone -> oneRed, overBackground -> oneBlue, sourceNone2 -> oneGreen), 1, 1)
      .toOption
      .get
    val blended = frames(1).image(0, 0)
    assertEquals(blended.alpha, 0xffff)
    assert(math.abs(blended.red - 0x7fff) <= 1)
    assert(math.abs(blended.blue - 0x8000) <= 1)
    assertEquals(frames(2).image(0, 0), green)

    val previous = control(1, DisposeOperation.Previous, BlendOperation.Source)
    val transparentOver = control(2, DisposeOperation.None, BlendOperation.Over)
    val restored = AnimationDecoder
      .compose(
        Vector(
          sourceNone -> oneRed,
          previous -> oneGreen,
          transparentOver -> Image16(1, 1, Vector(clear)).toOption.get
        ),
        1,
        1
      )
      .toOption
      .get
    assertEquals(restored(2).image(0, 0), red)

  private def animationBytes(first: Image16, second: Image16): Array[Byte] =
    val firstPng = Codec16.encode(first, EncoderOptions.default).toOption.get
    val secondPng = Codec16.encode(second, EncoderOptions.default).toOption.get
    val firstData = idat(firstPng)
    val secondData = idat(secondPng)
    val header = Header(2, 1, 16, ColorType.TruecolorAlpha).toOption.get
    val chunks = Vector(
      Chunk(ChunkType.IHDR, header.bytes).toOption.get,
      Chunk(ChunkType.acTL, 2L.uint32Bytes ++ 0L.uint32Bytes).toOption.get,
      Chunk(ChunkType.fcTL, frameControl(0, 2, 1, 0, 0)).toOption.get,
      Chunk(ChunkType.IDAT, firstData).toOption.get,
      Chunk(ChunkType.fcTL, frameControl(1, 1, 1, 1, 0)).toOption.get,
      Chunk(ChunkType.fdAT, 2L.uint32Bytes ++ secondData).toOption.get,
      Chunk(ChunkType.IEND, Array.emptyByteArray).toOption.get
    )
    Codec.Signature.toArray ++ chunks.flatMap(_.bytes)

  private def frameControl(
      sequence: Long,
      width: Long,
      height: Long,
      x: Long,
      y: Long
  ): Array[Byte] =
    sequence.uint32Bytes ++ width.uint32Bytes ++ height.uint32Bytes ++ x.uint32Bytes ++ y.uint32Bytes ++
      Array[Byte](0, 1, 0, 10, 0, 0)

  private def control(
      sequence: Long,
      dispose: DisposeOperation,
      blend: BlendOperation
  ): FrameControl =
    val bytes = sequence.uint32Bytes ++ 1L.uint32Bytes ++ 1L.uint32Bytes ++ 0L.uint32Bytes ++
      0L.uint32Bytes ++ Array[Byte](0, 1, 0, 10, dispose.code.toByte, blend.code.toByte)
    FrameControl.parse(bytes, 1, 1).toOption.get

  private def idat(png: Array[Byte]): Array[Byte] =
    val cursor = Binary.Cursor(png)
    cursor.take(Codec.Signature.length)
    var result = Array.emptyByteArray
    while cursor.remaining > 0 do
      val chunk = Chunk.parse(cursor).toOption.get
      if chunk.chunkType == ChunkType.IDAT then result ++= chunk.data
    result
