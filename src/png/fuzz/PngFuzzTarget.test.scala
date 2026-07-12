package png

import java.nio.file.{ Files, Paths }
import munit.FunSuite
import scala.jdk.CollectionConverters.*
import scala.util.Random

final class PngFuzzTargetSuite extends FunSuite:
  test("all PngSuite seeds and deterministic mutations reach every decoder without throwing"):
    val directory = Paths.get("testdata", "pngsuite")
    val stream = Files.list(directory)
    val seeds =
      try stream.filter(_.toString.endsWith(".png")).iterator().asScala.map(Files.readAllBytes).toVector
      finally stream.close()

    seeds.foreach: seed =>
      PngFuzzTarget.fuzzerTestOneInput(seed)
      mutations(seed, seed.length.toLong).foreach(PngFuzzTarget.fuzzerTestOneInput)

  test("empty, tiny, and oversized non-PNG inputs remain typed failures"):
    Vector(
      Array.emptyByteArray,
      Array[Byte](0),
      Array.fill[Byte](7)(-1),
      Array.fill[Byte](1024 * 1024 + 1)(0)
    ).foreach(PngFuzzTarget.fuzzerTestOneInput)

  private def mutations(seed: Array[Byte], randomSeed: Long): Vector[Array[Byte]] =
    val random = Random(randomSeed)
    Vector.tabulate(32): index =>
      val result = seed.clone()
      if result.nonEmpty then
        val mutations = 1 + index % math.min(8, result.length)
        (0 until mutations).foreach: _ =>
          val position = random.nextInt(result.length)
          result(position) = (result(position) ^ (1 << random.nextInt(8))).toByte
      result
