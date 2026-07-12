package png

import munit.FunSuite

final class PngBenchmarkSuite extends FunSuite:
  test("quick benchmark exercises every operation and reports finite positive measurements"):
    val results =
      PngBenchmark.run(PngBenchmark.Config(warmups = 0, samples = 1, batchSize = 1, imageSize = 8))

    assertEquals(
      results.map(_.name),
      Vector("decode-rgba8", "decode-rgba16", "encode-rgba8", "encode-rgba16")
    )
    assert(
      results.forall(result => result.nanosecondsPerOperation > 0 && result.nanosecondsPerOperation.isFinite)
    )
    assert(results.forall(result => result.mebibytesPerSecond > 0 && result.mebibytesPerSecond.isFinite))
