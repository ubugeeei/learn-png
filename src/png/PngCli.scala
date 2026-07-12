package png

import java.nio.file.Path

/** Executable entry point for inspecting and normalizing PNG files.
  *
  * Run `scala-cli run . -- info image.png` or
  * `scala-cli run . -- copy input.png output.png --interlace`.
  */
object PngCli:
  def main(arguments: Array[String]): Unit =
    run(arguments.toList) match
      case Right(message) => println(message)
      case Left(error)    =>
        System.err.println(s"error: ${error.message}")
        System.err.println(usage)
        sys.exit(1)

  private[png] def run(arguments: List[String]): Either[PngError, String] =
    arguments match
      case "info" :: input :: Nil =>
        Png
          .read(Path.of(input))
          .map: image =>
            val transparent = image.pixels.count(_.alpha != 255)
            s"${image.width}x${image.height} RGBA8 ($transparent non-opaque pixels)"
      case "copy" :: input :: output :: flags =>
        for
          options <- parseOptions(flags)
          image <- Png.read(Path.of(input))
          path <- Png.write(Path.of(output), image, options)
        yield s"wrote ${image.width}x${image.height} PNG to $path"
      case _ =>
        Left(PngError.InvalidArguments("expected info or copy command"))

  private def parseOptions(
      flags: List[String]
  ): Either[PngError, EncoderOptions] =
    flags.foldLeft[Either[PngError, EncoderOptions]](
      Right(EncoderOptions.default)
    ):
      case (result, "--interlace") =>
        result.flatMap(options =>
          EncoderOptions(
            true,
            options.compressionLevel,
            options.maximumIdatPayload
          )
        )
      case (_, unknown) =>
        Left(PngError.InvalidArguments(s"unknown option $unknown"))

  val usage: String =
    """usage:
      |  png info <input.png>
      |  png copy <input.png> <output.png> [--interlace]""".stripMargin
