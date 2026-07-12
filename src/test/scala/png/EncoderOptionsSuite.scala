package png

import munit.FunSuite

final class EncoderOptionsSuite extends FunSuite:
  test("compression and IDAT bounds are validated"):
    assert(EncoderOptions(compressionLevel = -2).isLeft)
    assert(EncoderOptions(compressionLevel = 10).isLeft)
    assert(EncoderOptions(maximumIdatPayload = 0).isLeft)
    assert(EncoderOptions(interlaced = true, maximumIdatPayload = 1).isRight)
