package png

import java.io.ByteArrayOutputStream
import png.PngError.{ InvalidChunkOrder, InvalidImage }

/** APNG sequence validation, frame decoding, and 16-bit canvas composition. */
private[png] object AnimationDecoder:
  final private case class EncodedFrame(control: FrameControl, compressed: Array[Byte])

  def decode(input: Array[Byte], options: DecoderOptions): Either[PngError, PngAnimation] =
    for
      validated <- Codec.validated(input, options)
      (chunks, header) = validated
      animationChunk <- exactlyOne(chunks, ChunkType.acTL)
      control <- AnimationControl.parse(animationChunk.data)
      fallback <- Codec16.decode(input, options)
      grouped <- groupFrames(chunks, header)
      (encodedFrames, fallbackIsFirst) = grouped
      _ <- Either.cond(
        encodedFrames.length == control.frames,
        (),
        InvalidImage(s"acTL declares ${control.frames} frames, found ${encodedFrames.length}")
      )
      decoded <- sequence(encodedFrames.map(frame => decodeFrame(frame, chunks, header, options)))
      frames <- compose(decoded, header.width, header.height)
    yield PngAnimation(header.width, header.height, control.plays, frames, fallback, fallbackIsFirst)

  private def groupFrames(
      chunks: Vector[Chunk],
      header: Header
  ): Either[PngError, (Vector[EncodedFrame], Boolean)] =
    var expectedSequence = 0L
    var current: Option[(FrameControl, ByteArrayOutputStream)] = None
    var result = Vector.empty[EncodedFrame]
    var seenIdat = false
    var fallbackIsFirst = false

    def flush(): Either[PngError, Unit] = current match
      case Some((control, output)) if output.size() > 0 =>
        result :+= EncodedFrame(control, output.toByteArray)
        current = None
        Right(())
      case Some(_) => Left(InvalidChunkOrder("APNG frame has no image data"))
      case None => Right(())

    chunks
      .foldLeft[Either[PngError, Unit]](Right(())):
        case (state, chunk) =>
          state.flatMap: _ =>
            if chunk.chunkType == ChunkType.fcTL then
              for
                _ <- flush()
                control <- FrameControl.parse(chunk.data, header.width, header.height)
                _ <- requireSequence(control.sequence, expectedSequence)
              yield
                if !seenIdat && result.isEmpty then fallbackIsFirst = true
                expectedSequence += 1
                current = Some(control -> ByteArrayOutputStream())
            else if chunk.chunkType == ChunkType.IDAT then
              seenIdat = true
              current.foreach(_._2.write(chunk.data))
              Right(())
            else if chunk.chunkType == ChunkType.fdAT then
              for
                frameData <- FrameData.parse(chunk.data)
                _ <- requireSequence(frameData.sequence, expectedSequence)
                frame <- current.toRight(InvalidChunkOrder("fdAT must follow fcTL"))
              yield
                expectedSequence += 1
                frame._2.write(frameData.compressed.toArray)
            else Right(())
      .flatMap(_ => flush())
      .map(_ => result -> fallbackIsFirst)

  private def decodeFrame(
      frame: EncodedFrame,
      chunks: Vector[Chunk],
      header: Header,
      options: DecoderOptions
  ): Either[PngError, (FrameControl, Image16)] =
    for
      frameHeader <- Header(
        frame.control.width.toInt,
        frame.control.height.toInt,
        header.bitDepth,
        header.colorType,
        header.interlaced
      )
      ihdr <- Chunk(ChunkType.IHDR, frameHeader.bytes)
      idat <- Chunk(ChunkType.IDAT, frame.compressed)
      iend <- Chunk(ChunkType.IEND, Array.emptyByteArray)
      required = chunks.filter(chunk =>
        chunk.chunkType == ChunkType.PLTE || chunk.chunkType == ChunkType.tRNS
      )
      synthetic = Codec.Signature.toArray ++ ihdr.bytes ++ required.flatMap(
        _.bytes
      ) ++ idat.bytes ++ iend.bytes
      image <- Codec16.decode(synthetic, options)
    yield frame.control -> image

  private[png] def compose(
      decoded: Vector[(FrameControl, Image16)],
      width: Int,
      height: Int
  ): Either[PngError, Vector[AnimationFrame]] =
    val transparent = Rgba16.unsafe(0, 0, 0, 0)
    var canvas = Vector.fill(width * height)(transparent)
    var frames = Vector.empty[AnimationFrame]

    decoded.zipWithIndex
      .foldLeft[Either[PngError, Unit]](Right(())):
        case (state, ((control, image), index)) =>
          state.flatMap: _ =>
            if index == 0 && control.dispose == DisposeOperation.Previous then
              Left(InvalidImage("the first APNG frame may not use PREVIOUS disposal"))
            else
              val before = canvas
              image.pixels.zipWithIndex.foreach: (source, frameIndex) =>
                val x = control.xOffset.toInt + frameIndex % image.width
                val y = control.yOffset.toInt + frameIndex / image.width
                val target = y * width + x
                canvas = canvas.updated(
                  target,
                  if control.blend == BlendOperation.Source then source else over(source, canvas(target))
                )
              Image16(width, height, canvas).map: displayed =>
                frames :+= AnimationFrame(displayed, control)
                control.dispose match
                  case DisposeOperation.None => ()
                  case DisposeOperation.Previous => canvas = before
                  case DisposeOperation.Background =>
                    for
                      y <- control.yOffset.toInt until (control.yOffset + control.height).toInt
                      x <- control.xOffset.toInt until (control.xOffset + control.width).toInt
                    do canvas = canvas.updated(y * width + x, transparent)
      .map(_ => frames)

  private def over(source: Rgba16, destination: Rgba16): Rgba16 =
    val maximum = 0xffffL
    val sourceAlpha = source.alpha.toLong
    val destinationAlpha = destination.alpha.toLong
    val inverse = maximum - sourceAlpha
    val outputAlpha = sourceAlpha + (destinationAlpha * inverse + maximum / 2) / maximum
    if outputAlpha == 0 then Rgba16.unsafe(0, 0, 0, 0)
    else
      def channel(sourceValue: Int, destinationValue: Int): Int =
        val premultiplied = sourceValue.toLong * sourceAlpha +
          (destinationValue.toLong * destinationAlpha * inverse + maximum / 2) / maximum
        ((premultiplied + outputAlpha / 2) / outputAlpha).toInt
      Rgba16.unsafe(
        channel(source.red, destination.red),
        channel(source.green, destination.green),
        channel(source.blue, destination.blue),
        outputAlpha.toInt
      )

  private def requireSequence(actual: Long, expected: Long): Either[PngError, Unit] =
    Either.cond(actual == expected, (), InvalidChunkOrder(s"APNG sequence $actual, expected $expected"))

  private def exactlyOne(chunks: Vector[Chunk], kind: ChunkType): Either[PngError, Chunk] =
    chunks.filter(_.chunkType == kind) match
      case Vector(chunk) => Right(chunk)
      case Vector() => Left(InvalidChunkOrder(s"missing ${kind.name}"))
      case _ => Left(InvalidChunkOrder(s"${kind.name} must occur exactly once"))

  private def sequence[A](values: Vector[Either[PngError, A]]): Either[PngError, Vector[A]] =
    values.foldLeft[Either[PngError, Vector[A]]](Right(Vector.empty))((result, value) =>
      for existing <- result; next <- value yield existing :+ next
    )
