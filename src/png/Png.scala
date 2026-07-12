package png

import java.io.{ InputStream, OutputStream }
import java.nio.file.Path

/** Public entry point for PNG encoding and decoding.
  *
  * Array methods are convenient for small in-memory images. Stream and path overloads apply the same
  * [[DecoderOptions]] and [[EncoderOptions]] while keeping ownership of caller-provided streams with the
  * caller: these methods flush but never close them.
  */
object Png:
  /** Fixed eight-byte datastream signature from PNG §5.2. */
  val Signature: Vector[Byte] = Codec.Signature

  /** Encode an RGBA8 image with default lossless options. */
  def encode(image: Image): Either[PngError, Array[Byte]] = Codec.encode(image)

  /** Encode an RGBA8 image with explicit compression, interlace, and IDAT sizing. */
  def encode(image: Image, options: EncoderOptions): Either[PngError, Array[Byte]] = Codec.encode(
    image,
    options
  )

  /** Encode an image and its metadata. */
  def encode(document: PngDocument): Either[PngError, Array[Byte]] = encode(document, EncoderOptions.default)

  def encode(document: PngDocument, options: EncoderOptions): Either[PngError, Array[Byte]] = Codec
    .encodeDocument(document, options)

  /** Decode pixels while intentionally discarding portable metadata. */
  def decode(bytes: Array[Byte]): Either[PngError, Image] = Codec.decode(bytes)

  /** Decode pixels under caller-defined resource limits. */
  def decode(bytes: Array[Byte], options: DecoderOptions): Either[PngError, Image] = Codec.decode(
    bytes,
    options
  )

  /** Decode every source sample into a 16-bit raster without discarding low-order bits. */
  def decode16(bytes: Array[Byte]): Either[PngError, Image16] =
    decode16(bytes, DecoderOptions.default)

  def decode16(bytes: Array[Byte], options: DecoderOptions): Either[PngError, Image16] =
    Codec16.decode(bytes, options)

  /** Encode a lossless RGBA16 raster as color type 6, bit depth 16. */
  def encode16(image: Image16): Either[PngError, Array[Byte]] =
    encode16(image, EncoderOptions.default)

  def encode16(image: Image16, options: EncoderOptions): Either[PngError, Array[Byte]] =
    Codec16.encode(image, options)

  /** Decode and composite every APNG frame into a lossless 16-bit canvas. */
  def decodeAnimation(bytes: Array[Byte]): Either[PngError, PngAnimation] =
    decodeAnimation(bytes, DecoderOptions.default)

  def decodeAnimation(bytes: Array[Byte], options: DecoderOptions): Either[PngError, PngAnimation] =
    AnimationDecoder.decode(bytes, options)

  /** Decode pixels plus color, density, text, and safe-to-copy metadata. */
  def decodeDocument(bytes: Array[Byte]): Either[PngError, PngDocument] = decodeDocument(
    bytes,
    DecoderOptions.default
  )

  def decodeDocument(bytes: Array[Byte], options: DecoderOptions): Either[PngError, PngDocument] = Codec
    .decodeDocument(bytes, options)

  /** Read a file under default limits. */
  def read(path: Path): Either[PngError, Image] = Codec.read(path)

  /** Read a file under explicit file, chunk, dimension, pixel, and inflation limits. */
  def read(path: Path, options: DecoderOptions): Either[PngError, Image] = Codec.read(path, options)

  /** Encode and transactionally replace a path through a forced temporary sibling. */
  def write(
      path: Path,
      image: Image,
      options: EncoderOptions = EncoderOptions.default
  ): Either[PngError, Path] = Codec.write(path, image, options)

  def read(input: InputStream): Either[PngError, Image] = read(input, DecoderOptions.default)

  /** Consume but never close a caller-owned stream, stopping at its configured byte limit. */
  def read(input: InputStream, options: DecoderOptions): Either[PngError, Image] = PngStreams
    .read(input, options)
    .flatMap(Codec.decode(_, options))

  /** Write and flush, but never close, a caller-owned output stream. */
  def write(output: OutputStream, image: Image, options: EncoderOptions): Either[PngError, Unit] = Codec
    .encode(image, options)
    .flatMap(PngStreams.write(output, _))

  def write(output: OutputStream, image: Image): Either[PngError, Unit] = write(
    output,
    image,
    EncoderOptions.default
  )
