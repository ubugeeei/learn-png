package png

import java.io.{InputStream, OutputStream}
import java.nio.file.Path

/** Public entry point for PNG encoding and decoding.
  *
  * Array methods are convenient for small in-memory images. Stream and path
  * overloads apply the same [[DecoderOptions]] and [[EncoderOptions]] while
  * keeping ownership of caller-provided streams with the caller: these methods
  * flush but never close them.
  */
object Png:
  val Signature: Vector[Byte] = Codec.Signature

  def encode(image: Image): Either[PngError, Array[Byte]] = Codec.encode(image)

  def encode(
      image: Image,
      options: EncoderOptions
  ): Either[PngError, Array[Byte]] =
    Codec.encode(image, options)

  def decode(bytes: Array[Byte]): Either[PngError, Image] = Codec.decode(bytes)

  def decode(
      bytes: Array[Byte],
      options: DecoderOptions
  ): Either[PngError, Image] =
    Codec.decode(bytes, options)

  def read(path: Path): Either[PngError, Image] = Codec.read(path)

  def read(path: Path, options: DecoderOptions): Either[PngError, Image] =
    Codec.read(path, options)

  def write(
      path: Path,
      image: Image,
      options: EncoderOptions = EncoderOptions.default
  ): Either[PngError, Path] = Codec.write(path, image, options)

  def read(input: InputStream): Either[PngError, Image] =
    read(input, DecoderOptions.default)

  def read(
      input: InputStream,
      options: DecoderOptions
  ): Either[PngError, Image] =
    PngStreams.read(input, options).flatMap(Codec.decode(_, options))

  def write(
      output: OutputStream,
      image: Image,
      options: EncoderOptions
  ): Either[PngError, Unit] =
    Codec.encode(image, options).flatMap(PngStreams.write(output, _))

  def write(output: OutputStream, image: Image): Either[PngError, Unit] =
    write(output, image, EncoderOptions.default)
