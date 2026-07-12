package png

import java.nio.file.{ Files, Paths }
import munit.FunSuite
import scala.jdk.CollectionConverters.*

/** Systematic malformed-input checks derived from every PngSuite basic fixture.
  *
  * PNG requires a complete signature/chunk stream, valid CRCs, and no bytes after IEND. See
  * [[https://www.w3.org/TR/png-3/#5PNG-file-signature PNG §5]].
  */
final class NegativeCorpusSuite extends FunSuite:
  private val directory = Paths.get("testdata", "pngsuite")
  private val fixtures =
    val stream = Files.list(directory)
    try stream.filter(path => path.getFileName.toString.endsWith(".png")).sorted().iterator().asScala.toVector
    finally stream.close()

  fixtures.foreach: path =>
    val name = path.getFileName.toString

    test(s"reject a corrupted IHDR CRC in $name"):
      val corrupted = Files.readAllBytes(path)
      corrupted(16) = (corrupted(16) ^ 1).toByte
      assert(Png.decode16(corrupted).isLeft)

    test(s"reject representative truncations of $name"):
      val bytes = Files.readAllBytes(path)
      val cutPoints = Vector(0, 7, 8, 12, bytes.length / 2, bytes.length - 1).distinct
      cutPoints.foreach(cut => assert(Png.decode16(bytes.take(cut)).isLeft, clues(cut)))

    test(s"reject bytes after IEND in $name"):
      assert(Png.decode16(Files.readAllBytes(path) ++ Array[Byte](0)).isLeft)
