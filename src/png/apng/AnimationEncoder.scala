package png

import png.Binary.*

/** APNG encoder using RGBA16 frame rectangles and continuous sequence numbers. */
private[png] object AnimationEncoder:
  def encode(
      animation: AnimationSource,
      options: EncoderOptions
  ): Either[PngError, Array[Byte]] =
    val encoded = animation.frames.map(frame => Codec16.encode(frame.image, options))
    sequence(encoded).flatMap: pngFrames =>
      for
        firstChunks <- chunks(pngFrames.head)
        header <- firstChunks
          .find(_.chunkType == ChunkType.IHDR)
          .toRight(PngError.InvalidImage("encoded frame lacks IHDR"))
        animationControl <- Chunk(
          ChunkType.acTL,
          animation.frames.length.toLong.uint32Bytes ++ animation.plays.uint32Bytes
        )
        generated <- generateFrames(animation.frames, pngFrames, options.maximumIdatPayload)
        iend <- Chunk(ChunkType.IEND, Array.emptyByteArray)
      yield Codec.Signature.toArray ++ header.bytes ++ animationControl.bytes ++ generated ++ iend.bytes

  private def generateFrames(
      frames: Vector[AnimationSourceFrame],
      encoded: Vector[Array[Byte]],
      maximumPayload: Int
  ): Either[PngError, Array[Byte]] =
    var sequenceNumber = 0L
    val output = Vector.newBuilder[Chunk]
    frames.zipWithIndex
      .foldLeft[Either[PngError, Unit]](Right(())):
        case (state, (frame, index)) =>
          state.flatMap: _ =>
            for
              control <- Chunk(ChunkType.fcTL, controlBytes(sequenceNumber, frame))
              _ = sequenceNumber += 1
              frameChunks <- chunks(encoded(index))
              compressed = frameChunks.filter(_.chunkType == ChunkType.IDAT).flatMap(_.data).toArray
              dataChunks <-
                if index == 0 then
                  sequence(
                    compressed.grouped(maximumPayload).toVector.map(payload => Chunk(ChunkType.IDAT, payload))
                  )
                else
                  sequence(
                    compressed
                      .grouped(maximumPayload)
                      .toVector
                      .map: payload =>
                        val current = sequenceNumber
                        sequenceNumber += 1
                        Chunk(ChunkType.fdAT, current.uint32Bytes ++ payload)
                  )
            yield
              output += control
              output ++= dataChunks
      .map(_ => output.result().flatMap(_.bytes).toArray)

  private def controlBytes(sequence: Long, frame: AnimationSourceFrame): Array[Byte] =
    sequence.uint32Bytes ++ frame.image.width.toLong.uint32Bytes ++ frame.image.height.toLong.uint32Bytes ++
      frame.xOffset.toLong.uint32Bytes ++ frame.yOffset.toLong.uint32Bytes ++
      uint16(frame.delayNumerator) ++ uint16(frame.delayDenominator) ++
      Array(frame.dispose.code.toByte, frame.blend.code.toByte)

  private def chunks(png: Array[Byte]): Either[PngError, Vector[Chunk]] =
    val cursor = Binary.Cursor(png)
    cursor
      .take(Codec.Signature.length)
      .flatMap: _ =>
        def loop(result: Vector[Chunk]): Either[PngError, Vector[Chunk]] =
          if cursor.remaining == 0 then Right(result)
          else Chunk.parse(cursor).flatMap(chunk => loop(result :+ chunk))
        loop(Vector.empty)

  private def uint16(value: Int): Array[Byte] = Array((value >>> 8).toByte, value.toByte)

  private def sequence[A](values: Vector[Either[PngError, A]]): Either[PngError, Vector[A]] =
    values.foldLeft[Either[PngError, Vector[A]]](Right(Vector.empty))((result, value) =>
      for existing <- result; next <- value yield existing :+ next
    )
