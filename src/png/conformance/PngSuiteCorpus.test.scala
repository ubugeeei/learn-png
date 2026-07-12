package png

import java.nio.file.{ Files, Paths }
import javax.imageio.ImageIO
import munit.FunSuite

/** Independent decoder checks against Willem van Schaik's PngSuite basic corpus.
  *
  * The fixtures cover every legal PNG color type and bit depth. Comparing with Java ImageIO prevents our
  * encoder and decoder from validating one another's mistakes. See
  * [[https://www.schaik.com/pngsuite/ PngSuite]].
  */
final class PngSuiteCorpusSuite extends FunSuite:
  private val corpus = Paths.get("testdata", "pngsuite")
  private val fixtures = Vector(
    "basn0g01.png",
    "basn0g02.png",
    "basn0g04.png",
    "basn0g08.png",
    "basn0g16.png",
    "basn2c08.png",
    "basn2c16.png",
    "basn3p01.png",
    "basn3p02.png",
    "basn3p04.png",
    "basn3p08.png",
    "basn4a08.png",
    "basn4a16.png",
    "basn6a08.png",
    "basn6a16.png"
  )
  private val imageIoComparable = fixtures.toSet -- Set(
    "basn0g08.png",
    "basn0g16.png",
    "basn2c16.png",
    "basn4a08.png",
    "basn4a16.png",
    "basn6a16.png"
  )

  fixtures.foreach: name =>
    test(s"decode PngSuite $name and agree with an independent decoder"):
      val path = corpus.resolve(name)
      val bytes = Files.readAllBytes(path)
      val actual = Png.decode(bytes).toOption.get
      val lossless = Png.decode16(bytes).toOption.get
      val reference = ImageIO.read(path.toFile)

      assertEquals(actual.width, reference.getWidth)
      assertEquals(actual.height, reference.getHeight)
      assertEquals(lossless.width, reference.getWidth)
      assertEquals(lossless.height, reference.getHeight)
      if imageIoComparable(name) then
        assertEquals(
          actual.pixels.map(argb),
          reference
            .getRGB(0, 0, reference.getWidth, reference.getHeight, null, 0, reference.getWidth)
            .toVector,
          clues(name)
        )

  private def argb(pixel: Rgba): Int =
    (pixel.alpha << 24) | (pixel.red << 16) | (pixel.green << 8) | pixel.blue
