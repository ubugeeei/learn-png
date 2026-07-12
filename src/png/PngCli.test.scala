package png

import java.nio.file.Files
import munit.FunSuite

final class PngCliSuite extends FunSuite:
  private val pixel = Rgba(255, 0, 0).toOption.get
  private val image = Image(1, 1, Vector(pixel)).toOption.get

  test("info reports dimensions and alpha summary"):
    val path = Files.createTempFile("learn-png-info", ".png")
    try
      Png.write(path, image).toOption.get
      assertEquals(PngCli.run(List("info", path.toString)), Right("1x1 RGBA8 (0 non-opaque pixels)"))
    finally Files.deleteIfExists(path): Unit

  test("copy can emit Adam7 output"):
    val input = Files.createTempFile("learn-png-input", ".png")
    val output = Files.createTempFile("learn-png-output", ".png")
    try
      Png.write(input, image).toOption.get
      assert(PngCli.run(List("copy", input.toString, output.toString, "--interlace")).isRight)
      assertEquals(Png.read(output), Right(image))
    finally
      Files.deleteIfExists(input)
      Files.deleteIfExists(output): Unit

  test("unknown options are typed argument errors"):
    assert(
      PngCli
        .run(List("copy", "in", "out", "--mystery"))
        .left
        .exists(_.isInstanceOf[PngError.InvalidArguments])
    )
