package png

import munit.FunSuite

final class DecoderOptionsSuite extends FunSuite:
  test("every resource limit must be positive"):
    assert(DecoderOptions(maximumFileBytes = 0).isLeft)
    assert(DecoderOptions(maximumWidth = 0).isLeft)
    assert(DecoderOptions(maximumHeight = 0).isLeft)
    assert(DecoderOptions(maximumPixels = 0).isLeft)
    assert(DecoderOptions(maximumInflatedBytes = 0).isLeft)
