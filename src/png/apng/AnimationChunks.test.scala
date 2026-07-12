package png

import munit.FunSuite
import png.Binary.*

final class AnimationChunksSuite extends FunSuite:
  test("animation control requires a positive frame count"):
    val control = AnimationControl.parse(1L.uint32Bytes ++ 0L.uint32Bytes).toOption.get
    assertEquals(control.frames, 1L)
    assertEquals(control.plays, 0L)
    assert(AnimationControl.parse(Array.fill[Byte](8)(0)).isLeft)

  test("frame control validates rectangle, operations, and zero denominator"):
    val data = 0L.uint32Bytes ++ 2L.uint32Bytes ++ 3L.uint32Bytes ++ 1L.uint32Bytes ++
      1L.uint32Bytes ++ Array[Byte](0, 5, 0, 0, 2, 1)
    val frame = FrameControl.parse(data, 4, 5).toOption.get
    assertEquals(frame.delaySeconds, 0.05)
    assertEquals(frame.dispose, DisposeOperation.Previous)
    assertEquals(frame.blend, BlendOperation.Over)
    assert(FrameControl.parse(data, 2, 2).isLeft)

  test("frame data separates sequence number from zlib bytes"):
    val data = 7L.uint32Bytes ++ Array[Byte](1, 2, 3)
    val frame = FrameData.parse(data).toOption.get
    assertEquals(frame.sequence, 7L)
    assertEquals(frame.compressed, Vector[Byte](1, 2, 3))
