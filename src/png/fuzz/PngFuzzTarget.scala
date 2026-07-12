package png

import scala.annotation.static

/** Coverage-guided fuzzing entry point compatible with Jazzer's byte-array API.
  *
  * A fuzzer calls [[fuzzerTestOneInput]] with arbitrary bytes. Expected format failures remain typed
  * [[PngError]] values; an uncaught exception is therefore a genuine crash for the fuzzer to minimize and
  * report.
  *
  * Resource limits intentionally stay small so malformed dimensions and zlib bombs cannot turn one fuzz case
  * into unbounded allocation. See [[https://www.w3.org/TR/png-3/#14Security-considerations PNG §14]].
  */
final class PngFuzzTarget private ()

object PngFuzzTarget:
  private val Limits = DecoderOptions(
    maximumFileBytes = 1024 * 1024,
    maximumChunkBytes = 256 * 1024,
    maximumChunks = 256,
    maximumWidth = 512,
    maximumHeight = 512,
    maximumPixels = 512L * 512,
    maximumInflatedBytes = 4L * 1024 * 1024
  ).toOption.get

  /** Exercise every decoder surface without swallowing unexpected failures. */
  @static def fuzzerTestOneInput(data: Array[Byte]): Unit =
    val _ = Png.decode(data, Limits)
    val _ = Png.decode16(data, Limits)
    val _ = Png.decodeDocument(data, Limits)
    val _ = Png.decodeAnimation(data, Limits)
